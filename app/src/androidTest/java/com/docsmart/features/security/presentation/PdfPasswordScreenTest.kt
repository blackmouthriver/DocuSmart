package com.docsmart.features.security.presentation

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.security.domain.PdfPasswordMessages
import com.docsmart.features.security.domain.PdfPasswordResult
import com.docsmart.features.security.domain.PdfPasswordUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Ronda 18: `PdfPasswordScreen` (proteger / quitar contraseña de un PDF) con
 * un `SecurityViewModel` real armado a mano (dependencias mockeadas) y
 * `PdfPasswordUseCase` mockeado.
 *
 * No cubierto: elegir un archivo. El selector (`FileSourcePickerDialog`)
 * obtiene su ViewModel con `hiltViewModel()` por defecto, y el Activity de
 * prueba no es un punto de entrada de Hilt; por eso el botón de acción con
 * archivo seleccionado y el guardado en Descargas (que escribe en MediaStore
 * real) quedan fuera. Los estados de resultado (éxito/error/procesando) se
 * provocan llamando directo al ViewModel.
 */
class PdfPasswordScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val useCase = mockk<PdfPasswordUseCase>()

    private fun string(resId: Int): String =
        forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(resId)

    private fun buildViewModel() =
        SecurityViewModel(
            securityManager = mockk(relaxed = true),
            pdfPasswordUseCase = useCase,
            mediaDeletePermission = mockk(relaxed = true),
            documentIdentityMaintenance = mockk(relaxed = true),
            appLifecycleTracker = mockk(relaxed = true),
        )

    private fun messages() =
        PdfPasswordMessages(
            readError = "leer",
            emptyFile = "vacio",
            protectSuccess = "ok",
            protectGenerateError = "gen",
            protectError = "err",
            removeSuccess = "ok",
            removeGenerateError = "gen",
            removeError = "err",
        )

    private fun setScreen(viewModel: SecurityViewModel) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localized = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            // La pantalla usa rememberLauncherForActivityResult() en los formularios.
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalActivityResultRegistryOwner provides composeRule.activity,
                LocalOnBackPressedDispatcherOwner provides composeRule.activity,
            ) {
                MaterialTheme { PdfPasswordScreen(viewModel = viewModel) }
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickText(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun openProtectForm(viewModel: SecurityViewModel) {
        setScreen(viewModel)
        clickText(string(R.string.pdf_pw_mode_protect_title))
        waitForText(string(R.string.pdf_pw_protect_section_title))
    }

    private fun openRemoveForm(viewModel: SecurityViewModel) {
        setScreen(viewModel)
        clickText(string(R.string.pdf_pw_mode_remove_title))
        waitForText(string(R.string.pdf_pw_remove_section_title))
    }

    @Test
    fun selector_muestraLasDosOpcionesYCancelarVuelveDesdeElFormulario() {
        setScreen(buildViewModel())

        composeRule.onNodeWithText(string(R.string.security_pdf_password)).assertExists()
        composeRule.onNodeWithText(string(R.string.security_what_to_do)).assertExists()
        composeRule.onNodeWithText(string(R.string.pdf_pw_mode_protect_title)).assertExists()
        composeRule.onNodeWithText(string(R.string.pdf_pw_mode_remove_title)).assertExists()

        clickText(string(R.string.pdf_pw_mode_protect_title))
        waitForText(string(R.string.pdf_pw_protect_section_title))
        clickText(string(R.string.general_cancel))

        waitForText(string(R.string.security_what_to_do))
    }

    @Test
    fun formularioProteger_validaQueLasContrasenasCoincidan() {
        openProtectForm(buildViewModel())

        composeRule.onNodeWithText(string(R.string.pdf_pw_tap_to_select)).assertExists()
        composeRule.onNodeWithText(string(R.string.pdf_pw_new_password_label)).performScrollTo()
            .performTextInput("abc")
        composeRule.onNodeWithText(string(R.string.pdf_pw_confirm_password_label)).performScrollTo()
            .performTextInput("abd")

        waitForText(string(R.string.pdf_pw_passwords_dont_match))
        // Sin archivo elegido el botón sigue deshabilitado aunque haya contraseña.
        composeRule.onNodeWithText(string(R.string.pdf_pw_protect_button)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun formularioProteger_alternaLaVisibilidadDeLaContrasena() {
        openProtectForm(buildViewModel())

        composeRule.onNodeWithContentDescription(string(R.string.password_show)).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(string(R.string.password_hide)).assertExists()
    }

    @Test
    fun formularioQuitar_muestraElAvisoYDeshabilitaElBotonSinArchivo() {
        openRemoveForm(buildViewModel())

        composeRule.onNodeWithText(string(R.string.pdf_pw_tap_to_select_protected)).assertExists()
        composeRule.onNodeWithText(string(R.string.pdf_pw_current_password_label)).performScrollTo()
            .performTextInput("clave")
        composeRule.onNodeWithText(string(R.string.pdf_pw_remove_warning)).performScrollTo().assertExists()
        composeRule.onNodeWithText(string(R.string.pdf_pw_remove_button)).performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithContentDescription(string(R.string.password_show)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(string(R.string.password_hide)).assertExists()

        clickText(string(R.string.general_cancel))
        waitForText(string(R.string.security_what_to_do))
    }

    @Test
    fun proteger_conExito_muestraElArchivoGeneradoYElMensaje() {
        val viewModel = buildViewModel()
        coEvery { useCase.protect(any(), any(), any(), any(), any()) } returns
            PdfPasswordResult.Success(File("salida.pdf"), "PDF listo")
        openProtectForm(viewModel)

        composeRule.runOnUiThread {
            viewModel.protectPdfWithPassword(
                InstrumentationRegistry.getInstrumentation().targetContext,
                Uri.EMPTY,
                "clave",
                "doc",
                messages(),
                "clave incorrecta",
            )
        }

        waitForText(string(R.string.pdf_pw_protected_success_title))
        composeRule.onNodeWithText("salida.pdf").assertExists()
        composeRule.onNodeWithText(string(R.string.pdf_pw_save_downloads_button)).assertExists()
        waitForText("PDF listo")
    }

    @Test
    fun proteger_conErrorOContrasenaIncorrecta_muestraElMensajeEnLaTarjetaDeError() {
        val viewModel = buildViewModel()
        coEvery { useCase.protect(any(), any(), any(), any(), any()) } returnsMany
            listOf(PdfPasswordResult.Error("Falló todo"), PdfPasswordResult.WrongPassword)
        openProtectForm(viewModel)
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.runOnUiThread {
            viewModel.protectPdfWithPassword(context, Uri.EMPTY, "clave", "doc", messages(), "clave incorrecta")
        }
        waitForText("Falló todo")

        composeRule.runOnUiThread {
            viewModel.protectPdfWithPassword(context, Uri.EMPTY, "clave", "doc", messages(), "clave incorrecta")
        }
        waitForText("clave incorrecta")
    }

    @Test
    fun proteger_mientrasProcesa_elBotonMuestraUnIndicadorEnVezDelTexto() {
        val viewModel = buildViewModel()
        coEvery { useCase.protect(any(), any(), any(), any(), any()) } coAnswers { awaitCancellation() }
        openProtectForm(viewModel)

        composeRule.runOnUiThread {
            viewModel.protectPdfWithPassword(
                InstrumentationRegistry.getInstrumentation().targetContext,
                Uri.EMPTY,
                "clave",
                "doc",
                messages(),
                "clave incorrecta",
            )
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { viewModel.uiState.value.isPdfProcessing }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(string(R.string.pdf_pw_protect_button)).assertCountEquals(0)
    }

    @Test
    fun quitar_conExito_muestraElArchivoSinContrasena() {
        val viewModel = buildViewModel()
        coEvery { useCase.removePassword(any(), any(), any(), any(), any()) } returns
            PdfPasswordResult.Success(File("abierto.pdf"), "Contraseña quitada")
        openRemoveForm(viewModel)

        composeRule.runOnUiThread {
            viewModel.removePdfPassword(
                InstrumentationRegistry.getInstrumentation().targetContext,
                Uri.EMPTY,
                "clave",
                "doc",
                messages(),
                "clave incorrecta",
            )
        }

        waitForText(string(R.string.pdf_pw_removed_success_title))
        composeRule.onNodeWithText("abierto.pdf").assertExists()
    }

    @Test
    fun quitar_conContrasenaIncorrecta_muestraElMensajeDeReintento() {
        val viewModel = buildViewModel()
        coEvery { useCase.removePassword(any(), any(), any(), any(), any()) } returns PdfPasswordResult.WrongPassword
        openRemoveForm(viewModel)

        composeRule.runOnUiThread {
            viewModel.removePdfPassword(
                InstrumentationRegistry.getInstrumentation().targetContext,
                Uri.EMPTY,
                "mala",
                "doc",
                messages(),
                "intenta de nuevo",
            )
        }

        waitForText("intenta de nuevo")
    }
}
