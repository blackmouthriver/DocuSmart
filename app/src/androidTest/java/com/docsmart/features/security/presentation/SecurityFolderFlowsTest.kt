package com.docsmart.features.security.presentation

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.security.SecureMoveResult
import com.docsmart.core.security.SecurityManager
import com.docsmart.core.ui.components.AppLibraryPickerViewModel
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.library.data.DocumentRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Ronda 20: carpeta segura desbloqueada de `SecurityScreen` (vacía y con
 * archivos, vista previa/restaurar/eliminar, diálogos de confirmación,
 * avisos de "original no eliminado", atajo de archivo pendiente y selector).
 */
class SecurityFolderFlowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val manager = mockk<SecurityManager>(relaxed = true)
    private var secureFiles = mutableListOf<File>()
    private val workDir =
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "sec_r20_${System.nanoTime()}",
        ).apply { mkdirs() }

    private fun newFile(
        name: String,
        bytes: Int,
    ): File = File(workDir, name).apply { writeBytes(ByteArray(bytes)) }

    private fun buildViewModel() =
        SecurityViewModel(
            securityManager = manager,
            pdfPasswordUseCase = mockk(relaxed = true),
            mediaDeletePermission = mockk(relaxed = true),
            documentIdentityMaintenance = mockk(relaxed = true),
            appLifecycleTracker = mockk(relaxed = true),
        )

    private fun stubBase(biometric: Boolean = false) {
        every { manager.hasPin() } returns true
        every { manager.isBiometricAvailable() } returns biometric
        every { manager.isBiometricEnabled() } returns false
        every { manager.pinLockoutRemainingMillis() } returns 0L
        every { manager.verifyPin(any()) } returns true
        every { manager.getSecureFiles() } answers { secureFiles.toList() }
    }

    private fun showUnlocked(
        pendingFileUri: String? = null,
        onPreviewFile: (String) -> Unit = {},
        picker: AppLibraryPickerViewModel? = null,
        onBack: () -> Unit = {},
        onHome: (() -> Unit)? = null,
    ): SecurityViewModel {
        val viewModel = buildViewModel()
        composeRule.setContentEsScaled {
            SecurityScreen(
                onBack = onBack,
                onHome = onHome,
                pendingFileUri = pendingFileUri,
                onPreviewFile = onPreviewFile,
                viewModel = viewModel,
                pickerViewModel = picker,
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { viewModel.verifyPin("1234", "mal", "bloqueado %1\$d") }
        composeRule.waitForText(esText(R.string.security_secure_folder))
        return viewModel
    }

    private fun openMenuAndClick(
        fileName: String,
        itemText: String,
    ) {
        composeRule.scrollListToText(fileName)
        composeRule.onAllNodesWithContentDescription(esText(R.string.viewer_more_options)).onFirst().performClick()
        composeRule.onNodeWithText(itemText).performClick()
        composeRule.waitForIdle()
    }

    private fun clickInDialog(text: String) {
        composeRule.onNode(hasText(text) and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun carpetaVacia_muestraElEstadoVacioYElContadorEnCero() {
        stubBase()
        showUnlocked()

        composeRule.onNodeWithText(esText(R.string.security_files_protected_count, 0)).assertExists()
        composeRule.scrollListToText(esText(R.string.security_no_protected_files))
        composeRule.onNodeWithText(esText(R.string.security_no_protected_files)).assertExists()
        composeRule.onNodeWithText(esText(R.string.security_no_protected_files_hint)).assertExists()
    }

    @Test
    fun conArchivos_listaNombresYTamanos() {
        stubBase()
        secureFiles.add(newFile("Pequeno.pdf", 10))
        secureFiles.add(newFile("Mediano.pdf", 2048))
        secureFiles.add(newFile("Grande.pdf", 3 * 1024 * 1024))
        showUnlocked()

        composeRule.onNodeWithText(esText(R.string.security_files_protected_count, 3)).assertExists()
        composeRule.scrollListToText("Pequeno.pdf")
        composeRule.onNodeWithText("Pequeno.pdf").assertExists()
        composeRule.onNodeWithText(esText(R.string.file_size_bytes, 10)).assertExists()
        composeRule.scrollListToText("Mediano.pdf")
        composeRule.onNodeWithText(esText(R.string.file_size_kb, 2)).assertExists()
        composeRule.scrollListToText("Grande.pdf")
        composeRule.onNodeWithText("Grande.pdf").assertExists()
    }

    @Test
    fun vistaPrevia_emiteLaRutaDeLaCopiaEfimera() {
        stubBase()
        val file = newFile("Doc.pdf", 100)
        secureFiles.add(file)
        every { manager.copyForPreview(any()) } returns File(workDir, "preview.pdf")
        val received = mutableListOf<String>()
        showUnlocked(onPreviewFile = { received += it })

        openMenuAndClick("Doc.pdf", esText(R.string.security_preview))
        composeRule.waitUntil(5_000) { received.isNotEmpty() }
        assertEquals(File(workDir, "preview.pdf").absolutePath, received.single())
    }

    @Test
    fun vistaPreviaFallida_muestraElErrorEnElSnackbar() {
        stubBase()
        secureFiles.add(newFile("Doc.pdf", 100))
        every { manager.copyForPreview(any()) } returns null
        showUnlocked()

        openMenuAndClick("Doc.pdf", esText(R.string.security_preview))
        composeRule.waitForText(esText(R.string.security_preview_error))
    }

    @Test
    fun restaurar_conOriginalBorrado_sacaElArchivoDeLaLista() {
        stubBase()
        val file = newFile("Doc.pdf", 100)
        secureFiles.add(file)
        every { manager.moveFromSecure(any(), any()) } answers {
            secureFiles.clear()
            SecureMoveResult(success = true, originalDeleted = true, destFile = File(workDir, "restaurado.pdf"))
        }
        showUnlocked()

        openMenuAndClick("Doc.pdf", esText(R.string.security_restore))
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Doc.pdf").fetchSemanticsNodes().isEmpty() }
        verify { manager.moveFromSecure(file, any()) }
    }

    @Test
    fun restaurar_sinPoderBorrarLaCopia_muestraElDialogoDeAviso() {
        stubBase()
        secureFiles.add(newFile("Doc.pdf", 100))
        every { manager.moveFromSecure(any(), any()) } returns
            SecureMoveResult(success = true, originalDeleted = false, destFile = File(workDir, "restaurado.pdf"))
        showUnlocked()

        openMenuAndClick("Doc.pdf", esText(R.string.security_restore))
        composeRule.waitForText(esText(R.string.security_original_kept_dialog_title))
        composeRule.onNodeWithText(esText(R.string.security_file_restored_original_kept)).assertExists()
        clickInDialog(esText(R.string.security_original_kept_dialog_confirm))
        composeRule.onAllNodesWithText(esText(R.string.security_original_kept_dialog_title)).assertCountEquals(0)
    }

    @Test
    fun restaurarFallido_muestraElErrorYConservaElArchivo() {
        stubBase()
        secureFiles.add(newFile("Doc.pdf", 100))
        every { manager.moveFromSecure(any(), any()) } returns
            SecureMoveResult(success = false, originalDeleted = false)
        showUnlocked()

        openMenuAndClick("Doc.pdf", esText(R.string.security_restore))
        composeRule.waitForText(esText(R.string.security_restore_error))
        composeRule.onNodeWithText("Doc.pdf").assertExists()
    }

    @Test
    fun eliminar_pideConfirmacion_cancelarConservaYConfirmarBorra() {
        stubBase()
        val file = newFile("Doc.pdf", 100)
        secureFiles.add(file)
        every { manager.deleteSecureFile(any()) } answers {
            secureFiles.clear()
            true
        }
        showUnlocked()

        openMenuAndClick("Doc.pdf", esText(R.string.general_delete))
        composeRule.onNodeWithText(esText(R.string.security_delete_confirm_title)).assertExists()
        composeRule.onNodeWithText(esText(R.string.security_delete_confirm_body, "Doc.pdf")).assertExists()
        clickInDialog(esText(R.string.general_cancel))
        verify(exactly = 0) { manager.deleteSecureFile(any()) }
        composeRule.onNodeWithText("Doc.pdf").assertExists()

        openMenuAndClick("Doc.pdf", esText(R.string.general_delete))
        clickInDialog(esText(R.string.general_delete))
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Doc.pdf").fetchSemanticsNodes().isEmpty() }
        verify { manager.deleteSecureFile(file) }
    }

    @Test
    fun eliminarFallido_muestraElErrorEnElSnackbar() {
        stubBase()
        secureFiles.add(newFile("Doc.pdf", 100))
        every { manager.deleteSecureFile(any()) } returns false
        showUnlocked()

        openMenuAndClick("Doc.pdf", esText(R.string.general_delete))
        clickInDialog(esText(R.string.general_delete))
        composeRule.waitForText(esText(R.string.general_delete_error))
        composeRule.onNodeWithText("Doc.pdf").assertExists()
    }

    @Test
    fun configuracion_biometriaCambiarPinYVolver() {
        stubBase(biometric = true)
        showUnlocked()

        composeRule.scrollListToText(esText(R.string.security_biometric_unlock))
        composeRule.onNodeWithText(esText(R.string.security_biometric_unlock)).assertExists()
        composeRule.onNode(isToggleable()).performClick()
        composeRule.waitForIdle()
        verify { manager.setBiometricEnabled(true) }

        composeRule.scrollListToText(esText(R.string.security_change_pin))
        composeRule.onNodeWithText(esText(R.string.security_change_pin)).performClick()
        composeRule.waitForText(esText(R.string.security_create_pin))
        composeRule.onNodeWithContentDescription(esText(R.string.general_back)).performClick()
        composeRule.waitForText(esText(R.string.security_enter_pin))
    }

    @Test
    fun sinBiometriaDisponible_noHayInterruptor() {
        stubBase(biometric = false)
        showUnlocked()

        composeRule.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test
    fun archivoPendienteLocal_seMueveAlDesbloquearYAvisaExito() {
        stubBase()
        val pending = newFile("Pendiente.pdf", 50)
        every { manager.moveToSecure(any()) } returns
            SecureMoveResult(success = true, originalDeleted = true, destFile = File(workDir, "dest.pdf"))
        showUnlocked(pendingFileUri = Uri.fromFile(pending).toString())

        composeRule.waitForText(esText(R.string.security_file_protected_success))
        verify(exactly = 1) { manager.moveToSecure(match { it.absolutePath == pending.absolutePath }) }
    }

    @Test
    fun archivoPendienteLocal_conOriginalSinBorrar_muestraElDialogoBloqueante() {
        stubBase()
        val pending = newFile("Pendiente.pdf", 50)
        every { manager.moveToSecure(any()) } returns
            SecureMoveResult(success = true, originalDeleted = false, destFile = File(workDir, "dest.pdf"))
        showUnlocked(pendingFileUri = Uri.fromFile(pending).toString())

        composeRule.waitForText(esText(R.string.security_original_kept_dialog_title))
        composeRule.onNodeWithText(esText(R.string.security_file_protected_original_kept)).assertExists()
        clickInDialog(esText(R.string.security_original_kept_dialog_confirm))
    }

    @Test
    fun archivoPendienteLocal_fallido_muestraElError() {
        stubBase()
        val pending = newFile("Pendiente.pdf", 50)
        every { manager.moveToSecure(any()) } returns SecureMoveResult(success = false, originalDeleted = false)
        showUnlocked(pendingFileUri = Uri.fromFile(pending).toString())

        composeRule.waitForText(esText(R.string.security_file_protect_error))
    }

    @Ignore("recompone en un hilo sin Looper; pendiente, ver backlog v17")
    @Test
    fun archivoPendienteContentInexistente_avisaElError() {
        stubBase()
        showUnlocked(pendingFileUri = "content://com.docsmart.r20.inexistente/doc/1")

        composeRule.waitForText(esText(R.string.security_file_protect_error), timeoutMillis = 10_000)
    }

    @Test
    fun protegerNuevoArchivo_abreElSelectorYMueveUnDocumentoLocalDeLaBiblioteca() {
        stubBase()
        val local = newFile("Local.pdf", 50)
        every { manager.moveToSecure(any()) } returns
            SecureMoveResult(success = true, originalDeleted = true, destFile = File(workDir, "dest.pdf"))
        val repository = mockk<DocumentRepository>(relaxed = true)
        val document =
            DocumentUiModel(
                id = local.absolutePath,
                name = "Local.pdf",
                type = DocumentType.PDF,
                size = "50 B",
                date = "Hoy",
            )
        coEvery { repository.loadAllDocuments() } returns listOf(document)
        showUnlocked(picker = AppLibraryPickerViewModel(repository))

        composeRule.scrollListToText(esText(R.string.security_protect_new_file))
        composeRule.onNodeWithText(esText(R.string.security_protect_new_file)).performClick()
        composeRule.waitForText(esText(R.string.security_protect_file_dialog_title))
        composeRule.waitForText("Local.pdf")
        composeRule.onNodeWithText("Local.pdf").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        verify { manager.moveToSecure(match { it.absolutePath == local.absolutePath }) }
    }

    @Test
    fun protegerNuevoArchivo_conDocumentoContent_intentaImportarPorUri() {
        stubBase()
        val repository = mockk<DocumentRepository>(relaxed = true)
        val document =
            DocumentUiModel(
                id = "content://com.docsmart.r20.inexistente/doc/2",
                name = "Nube.pdf",
                type = DocumentType.PDF,
                size = "1 KB",
                date = "Hoy",
            )
        coEvery { repository.loadAllDocuments() } returns listOf(document)
        showUnlocked(picker = AppLibraryPickerViewModel(repository))

        composeRule.scrollListToText(esText(R.string.security_protect_new_file))
        composeRule.onNodeWithText(esText(R.string.security_protect_new_file)).performClick()
        composeRule.waitForText("Nube.pdf")
        composeRule.onNodeWithText("Nube.pdf").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForText(esText(R.string.security_file_protect_error), timeoutMillis = 10_000)
    }

    @Test
    fun flechaAtrasDeLaCarpeta_invocaOnBack() {
        stubBase()
        var backs = 0
        showUnlocked(onBack = { backs++ })

        // En el banner de la carpeta la acción "Atrás" es un texto (no un ícono con descripción).
        composeRule.onNodeWithText(esText(R.string.general_back)).performClick()
        assertEquals(1, backs)
    }

    // Gap real (ronda 23): SecurityScreen acepta un `onHome` opcional para el
    // banner "Volver"/"Inicio" (mismo patron de pantallas anidadas que Modo
    // Estudio/Agenda), pero ningun test de la Carpeta Segura lo pasaba
    // distinto de null -- el boton "Inicio" del banner
    // (DocuSmartTopBanner/BannerNavRow) nunca se ejercitaba en esta pantalla.
    @Test
    fun botonInicioDeLaCarpeta_invocaOnHome() {
        stubBase()
        var homes = 0
        showUnlocked(onHome = { homes++ })

        composeRule.onNodeWithText(esText(R.string.nav_home)).performClick()
        assertEquals(1, homes)
    }
}
