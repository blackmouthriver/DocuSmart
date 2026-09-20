package com.docsmart.features.scanner.presentation

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

// Ronda 20: utilidades para las pruebas de flujos del escáner y del QR (pantalla
// de resultado del escaneo, creador y lector de QR). Ninguna abre selectores,
// chooser ni cámara reales: los intents se graban y los launchers de resultado
// se resuelven con un registro falso.

/** Contexto que graba los `startActivity` (chooser de compartir, abrir enlace...) sin lanzarlos. */
internal class RecordingContext(
    base: Context,
) : ContextWrapper(base) {
    val started = CopyOnWriteArrayList<Intent>()

    override fun startActivity(intent: Intent) {
        started += intent
    }

    override fun startActivity(
        intent: Intent,
        options: Bundle?,
    ) {
        started += intent
    }
}

/**
 * Dueño de un registro de resultados falso: cada `launch` se resuelve al instante,
 * sin abrir nada -- `false` para el permiso de cámara y [uriResult] (null = el
 * usuario canceló) para cualquier selector de archivos.
 */
internal class FakeResultRegistryOwner(
    private val uriResult: () -> android.net.Uri? = { null },
) : ActivityResultRegistryOwner {
    val launched = CopyOnWriteArrayList<String>()

    override val activityResultRegistry: ActivityResultRegistry =
        object : ActivityResultRegistry() {
            @Suppress("UNCHECKED_CAST")
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                launched += contract.javaClass.simpleName
                val result: Any? =
                    if (contract is ActivityResultContracts.RequestPermission) false else uriResult()
                dispatchResult(requestCode, result as O)
            }
        }
}

/**
 * Igual que `setContentEs` (ScannerTestSupport) pero además reduce la escala de
 * densidad en pantallas bajas (emulador de CI de 320x640 dp) y permite fijar un
 * dueño del registro de resultados propio.
 */
internal fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.setContentEsFit(
    overrideContext: ((Context) -> Context)? = null,
    registryOwner: ActivityResultRegistryOwner? = null,
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
            LocalDensity provides testViewportDensity(),
            LocalActivityResultRegistryOwner provides (registryOwner ?: rule.activity),
            LocalOnBackPressedDispatcherOwner provides rule.activity,
        ) { content() }
    }
}

/** JPEG sólido y pequeño: simula una página escaneada real (Bitmap -> archivo). */
internal fun createTestJpeg(
    file: File,
    color: Int = Color.RED,
    width: Int = 64,
    height: Int = 96,
): File {
    file.parentFile?.mkdirs()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(color)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    bitmap.recycle()
    return file
}
