package com.docsmart.features.home.presentation.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.test.forceLocale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: cuadrícula de accesos rápidos de Home (11 accesos + hoja de
 * "Escáner") y el placeholder de anuncio. Los callbacks se invocan por
 * semántica (OnClick) para no depender de que el nodo esté en pantalla.
 */
class HomeComponentsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()

    private fun string(resId: Int): String =
        forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(resId)

    private fun setGrid() {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localized = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources) {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        QuickAccessGrid(
                            onScanClick = { calls += "scan" },
                            onImageToPdfClick = { calls += "imgpdf" },
                            onSafeBoxClick = { calls += "safe" },
                            onStudyModeClick = { calls += "reading" },
                            onStudyMenuClick = { calls += "study" },
                            onQrReaderClick = { calls += "qrread" },
                            onQrCreatorClick = { calls += "qrcreate" },
                            onNotesClick = { calls += "notes" },
                            onPomodoroClick = { calls += "pomodoro" },
                            onAgendaClick = { calls += "agenda" },
                            onTrashClick = { calls += "trash" },
                        )
                    }
                }
            }
        }
    }

    private fun invoke(text: String) {
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun cadaAccesoRapido_invocaSuPropioCallback() {
        setGrid()

        composeRule.onNodeWithText(string(R.string.home_quick_access)).assertIsDisplayed()
        listOf(
            R.string.home_img_pdf,
            R.string.home_security,
            R.string.study_title,
            R.string.study_tab_reading,
            R.string.study_tab_notes,
            R.string.study_tab_pomodoro,
            R.string.home_agenda,
            R.string.home_qr_read,
            R.string.home_qr_create,
            R.string.library_trash,
        ).forEach { labelRes ->
            composeRule.onNodeWithText(string(labelRes)).performScrollTo()
            invoke(string(labelRes))
        }

        assertEquals(
            listOf(
                "imgpdf",
                "safe",
                "study",
                "reading",
                "notes",
                "pomodoro",
                "agenda",
                "qrread",
                "qrcreate",
                "trash",
            ),
            calls,
        )
    }

    @Test
    fun tocarEscanear_abreLaHojaYLaOpcionDeEscanearInvocaElCallback() {
        setGrid()

        invoke(string(R.string.home_scan))
        composeRule.onNodeWithText(string(R.string.home_scanner_sheet_title)).assertExists()
        invoke(string(R.string.home_scanner_option_scan_title))

        assertEquals(listOf("scan"), calls)
    }

    @Test
    fun hojaDeEscaner_leerQrInvocaElCallback() {
        setGrid()

        invoke(string(R.string.home_scan))
        invoke(string(R.string.qr_reader_title))

        assertEquals(listOf("qrread"), calls)
    }

    @Test
    fun hojaDeEscaner_crearQrInvocaElCallback() {
        setGrid()

        invoke(string(R.string.home_scan))
        invoke(string(R.string.qr_creator_title))

        assertEquals(listOf("qrcreate"), calls)
    }

    @Test
    fun placeholderDeAnuncio_muestraElEspacioPublicitario() {
        // Texto hardcodeado en AdBanner (placeholder sin uso real hoy).
        composeRule.setContent { MaterialTheme { AdBanner() } }

        composeRule.onNodeWithText("Espacio publicitario").assertIsDisplayed()
    }
}
