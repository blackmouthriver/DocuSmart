package com.docsmart.features.security.presentation

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.io.File

/**
 * Utilidades compartidas por las pruebas de Seguridad/Ajustes/DailyLimit de la
 * ronda 20: idioma español forzado, densidad reducida en pantallas bajas y
 * SharedPreferences falsas aisladas de las reales del dispositivo.
 */
internal fun esText(
    id: Int,
    vararg args: Any,
): String = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(id, *args)

internal fun AndroidComposeTestRule<*, ComponentActivity>.setContentEsScaled(
    filesDir: File? = null,
    content: @Composable () -> Unit,
) {
    setContent {
        val baseContext = LocalContext.current
        val localized = remember(baseContext) { forceLocale(baseContext, "es-ES") }
        // Con filesDir se aísla la carpeta de archivos de la app real (solo filesDir; prefs reales).
        val effective =
            remember(localized, filesDir) {
                if (filesDir == null) localized else FilesDirContext(localized, filesDir)
            }
        CompositionLocalProvider(
            LocalContext provides effective,
            LocalResources provides localized.resources,
            LocalDensity provides testViewportDensity(),
            LocalActivityResultRegistryOwner provides activity,
            LocalOnBackPressedDispatcherOwner provides activity,
        ) { content() }
    }
}

internal fun AndroidComposeTestRule<*, ComponentActivity>.waitForText(
    text: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}

/** Desplaza un LazyColumn hasta el nodo con [text] (si no hay scroll, no hace nada). */
internal fun AndroidComposeTestRule<*, ComponentActivity>.scrollListToText(text: String) {
    try {
        onNode(hasScrollAction()).performScrollToNode(hasText(text))
    } catch (_: AssertionError) {
        // Sin contenedor desplazable o ya visible.
    }
}

/** Contexto que solo redefine filesDir (para no tocar los archivos generados reales de la app). */
internal class FilesDirContext(
    base: Context,
    private val testFilesDir: File,
) : ContextWrapper(base) {
    override fun getFilesDir(): File = testFilesDir
}

/** Contexto que aísla SharedPreferences y filesDir del estado real de la app. */
internal class IsolatedTestContext(
    base: Context,
    private val fakePrefs: SharedPreferences,
    private val testFilesDir: File,
) : ContextWrapper(base) {
    override fun getSharedPreferences(
        name: String?,
        mode: Int,
    ): SharedPreferences = fakePrefs

    override fun getFilesDir(): File = testFilesDir
}

/** SharedPreferences en memoria con getters, setters, remove, apply y commit. */
internal fun fakePrefsInMemory(): SharedPreferences {
    val store = mutableMapOf<String, Any?>()
    val editor = mockk<SharedPreferences.Editor>()
    every { editor.putString(any(), any()) } answers {
        store[firstArg<String>()] = secondArg<String?>()
        editor
    }
    every { editor.putBoolean(any(), any()) } answers {
        store[firstArg<String>()] = secondArg<Boolean>()
        editor
    }
    every { editor.putInt(any(), any()) } answers {
        store[firstArg<String>()] = secondArg<Int>()
        editor
    }
    every { editor.putLong(any(), any()) } answers {
        store[firstArg<String>()] = secondArg<Long>()
        editor
    }
    every { editor.putFloat(any(), any()) } answers {
        store[firstArg<String>()] = secondArg<Float>()
        editor
    }
    every { editor.remove(any()) } answers {
        store.remove(firstArg<String>())
        editor
    }
    every { editor.clear() } answers {
        store.clear()
        editor
    }
    every { editor.apply() } just Runs
    every { editor.commit() } returns true
    val prefs = mockk<SharedPreferences>()
    every { prefs.edit() } returns editor
    every { prefs.getString(any(), any()) } answers { (store[firstArg<String>()] as? String) ?: secondArg() }
    every { prefs.getInt(any(), any()) } answers { (store[firstArg<String>()] as? Int) ?: secondArg() }
    every { prefs.getLong(any(), any()) } answers { (store[firstArg<String>()] as? Long) ?: secondArg() }
    every { prefs.getFloat(any(), any()) } answers { (store[firstArg<String>()] as? Float) ?: secondArg() }
    every { prefs.getBoolean(any(), any()) } answers { (store[firstArg<String>()] as? Boolean) ?: secondArg() }
    every { prefs.contains(any()) } answers { store.containsKey(firstArg<String>()) }
    return prefs
}
