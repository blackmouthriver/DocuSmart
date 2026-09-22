package com.docsmart.features.study.presentation

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.R
import com.docsmart.features.agenda.presentation.components.esString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Banner de Lectura con la ficha del documento integrada (fase 4, 2026-09-22): portada,
 * título, páginas, avance ("% escuchado"), resaltados y voz -- sin cobertura automática
 * hasta esta auditoría, porque vive en `StudyScreen` (no en `ReadingTab`, cuyas pruebas
 * no la ejercitan).
 */
class ReadingDocumentBannerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val env by lazy { StudyTestEnv(composeRule) }
    private var backClicks = 0
    private var homeClicks = 0

    private fun setBanner(
        currentPage: Int = 1,
        totalPages: Int = 4,
        readingFinished: Boolean = false,
        highlightedCount: Int = 0,
        voiceName: String? = null,
    ) {
        env.setContent {
            ReadingDocumentBanner(
                documentUri = Uri.parse("file:///no/existe/documento.pdf"),
                documentName = "HU46_test.pdf",
                currentPage = currentPage,
                totalPages = totalPages,
                readingFinished = readingFinished,
                highlightedCount = highlightedCount,
                voiceName = voiceName,
                onBack = { backClicks++ },
                onHome = { homeClicks++ },
            )
        }
    }

    @Test
    fun muestraElNombreLasPaginasYElAvanceEnReposo() {
        setBanner(currentPage = 1, totalPages = 4)

        composeRule.onNodeWithText("HU46_test.pdf").assertExists()
        composeRule.onNodeWithText(esString(R.string.study_total_pages, 4)).assertExists()
        // En la primera página aún no se escuchó nada (listenedFraction).
        composeRule.onNodeWithText(esString(R.string.study_listened_percent, 0)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_highlighted_count, 0)).assertExists()
    }

    @Test
    fun elAvanceCuentaSoloLasPaginasYaLeidasNoLaActual() {
        setBanner(currentPage = 3, totalPages = 4)

        // Página 3 de 4 en curso: 2 páginas completas -> 50%, no 75%.
        composeRule.onNodeWithText(esString(R.string.study_listened_percent, 50)).assertExists()
    }

    @Test
    fun alTerminarLaLecturaElAvanceQuedaEnCienPorCiento() {
        setBanner(currentPage = 1, totalPages = 4, readingFinished = true)

        composeRule.onNodeWithText(esString(R.string.study_listened_percent, 100)).assertExists()
    }

    @Test
    fun muestraLosResaltadosYLaVozCuandoHay() {
        setBanner(highlightedCount = 3, voiceName = "Sofía")

        composeRule.onNodeWithText(esString(R.string.study_highlighted_count, 3)).assertExists()
        composeRule.onNodeWithText(esString(R.string.study_voice_chip, "Sofía")).assertExists()
    }

    @Test
    fun sinVozElegidaNoMuestraElChipDeVoz() {
        setBanner(voiceName = null)

        composeRule.onNodeWithText(esString(R.string.study_voice_chip, "Sofía")).assertDoesNotExist()
    }

    @Test
    fun sinPaginasNoMuestraElContadorNiFallaElAvance() {
        setBanner(currentPage = 1, totalPages = 0)

        composeRule.onNodeWithText(esString(R.string.study_total_pages, 0)).assertDoesNotExist()
        composeRule.onNodeWithText(esString(R.string.study_listened_percent, 0)).assertExists()
    }

    @Test
    fun volverEInicioDisparanSusCallbacks() {
        setBanner()

        composeRule.onNodeWithText(esString(R.string.general_back)).performClick()
        composeRule.onNodeWithText(esString(R.string.nav_home)).performClick()
        assertEquals(1, backClicks)
        assertEquals(1, homeClicks)
    }

    @Test
    fun unNombreDeArchivoLargoNoRompeElBanner() {
        env.setContent {
            ReadingDocumentBanner(
                documentUri = Uri.parse("file:///no/existe/documento.pdf"),
                documentName = "Un nombre de archivo extremadamente largo para forzar el recorte a dos líneas.pdf",
                currentPage = 1,
                totalPages = 10,
                readingFinished = false,
                highlightedCount = 0,
                voiceName = null,
                onBack = {},
                onHome = {},
            )
        }

        composeRule
            .onNodeWithText(
                "Un nombre de archivo extremadamente largo para forzar el recorte a dos líneas.pdf",
            ).assertExists()
    }
}
