package com.docsmart.features.scanner.presentation

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.docsmart.core.ui.test.forceLocale
import io.mockk.every
import io.mockk.mockk

/**
 * Utilidades compartidas por las pruebas de las pantallas/componentes de QR y
 * escáner: fija el locale (el emulador de CI arranca en inglés) y reprovee los
 * dueños de la Activity para los Composables que usan launchers de resultado.
 */
internal fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.setContentEs(
    overrideContext: ((Context) -> Context)? = null,
    content: @Composable () -> Unit,
) {
    val rule = this
    setContent {
        val baseContext = LocalContext.current
        val localizedContext =
            remember(baseContext) {
                val forced = forceLocale(baseContext, "es-ES")
                overrideContext?.invoke(forced) ?: forced
            }
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalResources provides localizedContext.resources,
            LocalActivityResultRegistryOwner provides rule.activity,
            LocalOnBackPressedDispatcherOwner provides rule.activity,
        ) { content() }
    }
}

/** Contexto que aísla las SharedPreferences por nombre: nunca toca datos reales del dispositivo. */
internal class MemoryPrefsContext(
    base: Context,
) : ContextWrapper(base) {
    private val prefsByName = mutableMapOf<String, SharedPreferences>()

    // `getOrPut` NO es atómico: con el despachador "unconfined" del reloj de pruebas de Compose, el guardado
    // de la pantalla corre en un hilo de trabajo mientras la prueba sondea desde otro. Sin sincronizar,
    // ambos creaban su propio SharedPreferences falso, uno pisaba al otro en el mapa y la entrada quedaba en
    // el descartado: `QrCreatorFlowsTest.url_genera...` fallaba solo en el emulador lento del CI
    // ("entries.size == 0" tras 5 s de espera).
    override fun getSharedPreferences(
        name: String?,
        mode: Int,
    ): SharedPreferences =
        synchronized(prefsByName) {
            prefsByName.getOrPut(name ?: "default") { fakePrefs() }
        }

    private fun fakePrefs(): SharedPreferences {
        // También se lee/escribe desde hilos distintos (ver arriba).
        val store = java.util.Collections.synchronizedMap(mutableMapOf<String, String?>())
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String?>()
            editor
        }
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers { store[firstArg<String>()] ?: secondArg<String?>() }
        return prefs
    }
}
