package com.docsmart.features.converter.presentation

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.domain.usecase.ImageFormatUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Flujo crítico #2 del manual de marca (junto con ViewerScreenTest): "abrir
 * o convertir un documento en menos de 3 toques". Cubre justo el terreno
 * donde se encontró y corrigió antes en esta sesión un crash real
 * (WEBP_LOSSLESS en Android 8-10, en ImageFormatUseCase) — por eso
 * ImageFormatUseCase NO se mockea acá, se usa la instancia real, para que
 * esta prueba dé protección de regresión de verdad ante ese bug, no solo
 * contra un mock.
 *
 * El selector de archivos del sistema es un proceso externo — no se puede
 * conducir con Compose UI Testing. Se simula seleccionando un archivo real
 * llamando a onFilesSelected() directo (mismo principio que ViewerScreenTest
 * resolviendo documentId sin ContentResolver real); el resto del flujo
 * (elegir formato, tocar "Convertir", ver el resultado) sí se conduce por
 * la UI real.
 */
class ConverterScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun buildViewModel(): ConverterViewModel {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Los 3 StateFlow<Boolean> de AdManager se leen directo en Composables
        // de anuncios (ej. DocuSmartBannerAd) -- un mock relajado sin stub
        // explícito para cada uno causa un ClassCastException al intentar
        // leer el proxy de MockK como Boolean primitivo.
        val adManager = mockk<AdManager>(relaxed = true)
        every { adManager.isPremium } returns MutableStateFlow(false)
        every { adManager.isInitialized } returns MutableStateFlow(true)
        every { adManager.isRewardedReady } returns MutableStateFlow(false)

        // canConvert() en un mock relajado devuelve false por defecto -- sin
        // esto, convert() muestra el diálogo de límite diario en vez de
        // convertir de verdad.
        val dailyLimitManager = mockk<DailyLimitManager>(relaxed = true)
        every { dailyLimitManager.canConvert() } returns true

        return ConverterViewModel(
            convertImageToPdf = mockk(relaxed = true),
            pdfToImage        = mockk(relaxed = true),
            pdfToText         = mockk(relaxed = true),
            pdfToWord         = mockk(relaxed = true),
            pdfToHtml         = mockk(relaxed = true),
            imageFormat       = ImageFormatUseCase(appContext), // real, no mock
            wordToPdf         = mockk(relaxed = true),
            wordToText        = mockk(relaxed = true),
            wordToHtml        = mockk(relaxed = true),
            excelToPdf        = mockk(relaxed = true),
            excelToCsv        = mockk(relaxed = true),
            excelToHtml       = mockk(relaxed = true),
            pptToPdf          = mockk(relaxed = true),
            pptToText         = mockk(relaxed = true),
            adManager         = adManager,
            dailyLimitManager = dailyLimitManager
        )
    }

    // Imagen real de prueba en cacheDir -- ImageFormatUseCase la lee vía
    // ContentResolver igual que con un archivo elegido por el usuario.
    private fun createTestImageFile(): Uri {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = File(appContext.cacheDir, "converter_test_${System.currentTimeMillis()}.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return Uri.fromFile(file)
    }

    @Test
    fun convertirImagenAWebp_muestraResultadoExitoso() {
        val viewModel = buildViewModel()
        val imageUri = createTestImageFile()

        composeRule.setContent {
            // Fuerza español -- "Convertir a WebP" viene de
            // stringResource(R.string.converter_to_format, ...), y el
            // emulador de CI arranca en inglés por defecto (ver
            // com.docsmart.core.ui.test.forceLocale).
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            // LocalContext ya no encadena de vuelta a la Activity real
            // (createConfigurationContext() no es un ContextWrapper) --
            // ConverterScreen usa rememberLauncherForActivityResult() para
            // el picker de archivos, que se resuelve a través de
            // LocalActivityResultRegistryOwner/LocalOnBackPressedDispatcherOwner,
            // no cadena arriba desde LocalContext. Sin re-proveer esos dos
            // apuntando a la Activity real, se rompe con "No
            // ActivityResultRegistryOwner was provided" al componer, aunque
            // el test nunca abra el picker de verdad.
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity
            ) {
                ConverterScreen(viewModel = viewModel)
            }
        }

        composeRule.runOnUiThread {
            // Orden real de la UI: elegir el tipo primero, el picker de
            // archivos se abre después para ese tipo específico --
            // onTypeSelected() limpia selectedFiles a propósito.
            viewModel.onTypeSelected(ConversionType.IMAGE_TO_WEBP)
            viewModel.onFilesSelected(listOf(imageUri))
        }
        // waitForIdle() no alcanza acá -- corriendo en el emulador de CI
        // (más lento/con render por software que el dispositivo real usado
        // en desarrollo), la recomposición tras mutar el ViewModel desde
        // runOnUiThread a veces todavía no había dibujado el botón cuando
        // waitForIdle() retornaba, y el test fallaba con "Failed to inject
        // touch input... could not find node". Se espera explícitamente a
        // que el nodo exista antes de tocarlo, mismo patrón que ya se usa
        // más abajo para "¡Conversión exitosa!". 20s (no 10s) porque en el
        // emulador de CI (swiftshader por software, 2 vCPU) un primer
        // intento con 10s todavía no alcanzaba.
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText("Convertir a WebP")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // "Convertir a WebP" (converter_to_format) es el texto real
        // renderizado en español gracias a forceLocale() (ver setContent{}
        // arriba) -- el hallazgo de i18n original (ConversionType.label
        // hardcodeado, converter_to_format recibiendo type.toFormat sin
        // localizar) ya se corrigió, ver docs/requirements/conversion.md.
        composeRule.onNodeWithText("Convertir a WebP").performClick()

        // La conversión real corre en un dispositivo de 8x8 px -- rápida,
        // pero es E/S real (comprimir + escribir a disco), así que se
        // espera con margen en vez de asumir que ya terminó tras waitForIdle().
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText("¡Conversión exitosa!")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
