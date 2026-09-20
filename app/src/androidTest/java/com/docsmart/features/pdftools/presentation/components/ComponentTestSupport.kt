package com.docsmart.features.pdftools.presentation.components

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ui.test.forceLocale
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import java.io.File

// Utilidades compartidas por las pruebas instrumentadas de los componentes
// del Convertidor y de Herramientas PDF (Composables que reciben estado y
// lambdas por parámetro, sin ViewModel ni Hilt).
// /

/** Contexto de la app con el locale fijado en español, para resolver strings igual que la UI. */
internal fun esContext(): Context {
    return forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES")
}

/**
 * Contexto que no lanza actividades reales: las registra. Así se verifica el
 * Intent de "Compartir" sin abrir el selector del sistema (que taparía la app).
 */
internal class RecordingContext(base: Context) : ContextWrapper(base) {
    val startedIntents = mutableListOf<Intent>()

    override fun startActivity(intent: Intent) {
        startedIntents += intent
    }
}

/**
 * Compone [content] con locale español dentro de una columna con scroll (los
 * componentes miden más alto que la pantalla). Si se pasa [overrideContext]
 * (ya en español) se usa como LocalContext en lugar del contexto de la Activity.
 */
internal fun ComposeContentTestRule.setEsContent(
    overrideContext: Context? = null,
    content: @Composable () -> Unit,
) {
    setContent {
        val base = LocalContext.current
        val localized = remember(base) { overrideContext ?: forceLocale(base, "es-ES") }
        CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                content()
            }
        }
    }
}

/** Botón (con acción de clic) cuyo texto es [text]; distingue el botón de un título con el mismo texto. */
internal fun ComposeContentTestRule.button(text: String): SemanticsNodeInteraction {
    return onNode(hasText(text) and hasClickAction())
}

/** PDF real y liviano de [pages] páginas apaisadas, en cacheDir, para las vistas previas que renderizan. */
internal fun createTestPdf(pages: Int): File {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val file = File(appContext.cacheDir, "component_test_${System.nanoTime()}.pdf")
    PdfDocument(PdfWriter(file)).use { pdf ->
        repeat(pages) { pdf.addNewPage(PageSize(300f, 200f)) }
    }
    return file
}
