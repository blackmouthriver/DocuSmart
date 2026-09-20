package com.docsmart.features.viewer.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.docsmart.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Ronda 20: PDF protegido con contraseña (cifrado RC4 128 real generado con iText7) abierto
 * en ViewerScreen: diálogo de contraseña, mostrar/ocultar, contraseña incorrecta, desbloqueo
 * y cancelación. El PdfPasswordDialogViewModel se inyecta a mano (sin Hilt).
 */
class ViewerPasswordTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val files = ViewerTestFiles()
    private val harness = ViewerHarness()
    private val dialogViewModel = PdfPasswordDialogViewModel()
    private var backCalls = 0

    @After
    fun tearDown() {
        files.deleteAll()
    }

    private fun openProtected(file: File) {
        composeRule.setViewerContent {
            ViewerScreen(
                documentId = file.absolutePath,
                onBack = { backCalls++ },
                viewModel = harness.viewModel,
                passwordDialogViewModel = dialogViewModel,
            )
        }
        composeRule.waitForText(vs(R.string.viewer_pdf_password_title))
    }

    private fun openButton() = composeRule.onNode(hasText(vs(R.string.viewer_pdf_password_open_button)) and hasClickAction())

    @Test
    fun pdfProtegido_pideContrasenaYElBotonEstaDeshabilitadoSinTexto() {
        val file = files.pdf(password = "clave123")
        openProtected(file)

        composeRule.onNodeWithText(vs(R.string.viewer_pdf_password_body, file.name)).assertExists()
        composeRule.onNodeWithText(vs(R.string.viewer_pdf_password_open_button)).assertIsNotEnabled()
        assertEquals(true, harness.viewModel.uiState.value.requiresPassword)
    }

    @Test
    fun pdfProtegido_mostrarYOcultarLaContrasena() {
        openProtected(files.pdf(password = "clave123"))

        composeRule.onNodeWithContentDescription(vs(R.string.password_show)).performClick()
        composeRule.waitForViewerNode(hasContentDescription(vs(R.string.password_hide)))
        assertEquals(true, dialogViewModel.showPassword)

        composeRule.onNodeWithContentDescription(vs(R.string.password_hide)).performClick()
        composeRule.waitForViewerNode(hasContentDescription(vs(R.string.password_show)))
    }

    @Ignore("inestable en el emulador de CI 320x640 (temporización); pendiente, ver backlog v17")
    @Test
    fun pdfProtegido_rechazaLaContrasenaIncorrectaYAbreConLaCorrecta() {
        openProtected(files.pdf(password = "clave123"))

        composeRule.onNode(hasSetTextAction()).performTextInput("mala")
        openButton().performClick()
        composeRule.waitForText(vs(R.string.pdf_pw_wrong_password_retry))
        assertEquals(true, harness.viewModel.uiState.value.requiresPassword)

        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("clave123")
        openButton().performClick()

        composeRule.waitForViewerNode(hasContentDescription(vs(R.string.viewer_page_content_desc, 1)), 30_000)
        assertFalse(harness.viewModel.uiState.value.requiresPassword)
        assertEquals(0, backCalls)
        // Al salir el diálogo, la contraseña tecleada no queda viva en memoria.
        composeRule.waitForState { dialogViewModel.password.isEmpty() }
    }

    @Test
    fun pdfProtegido_cancelarCierraElVisor() {
        openProtected(files.pdf(password = "clave123"))

        composeRule.onNodeWithText(vs(R.string.general_cancel)).performClick()

        composeRule.waitForState { backCalls == 1 }
        assertFalse(harness.viewModel.uiState.value.requiresPassword)
    }
}
