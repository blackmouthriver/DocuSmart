package com.docsmart.features.security.presentation

import android.content.Context
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.docsmart.core.security.SecurityManager
import com.docsmart.core.security.SecureMoveResult
import com.docsmart.features.security.domain.PdfPasswordMessages
import com.docsmart.features.security.domain.PdfPasswordResult
import com.docsmart.features.security.domain.PdfPasswordUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Cubre RF-SEC-01/02/05/09 y el manejo de error/successMessage
 * (docs/requirements/security.md §6, ítem 4). `authenticateWithBiometric()`
 * y `savePdfToDownloads()` quedan fuera de alcance: ambos instancian clases
 * reales del framework de Android (`BiometricPrompt`, `ContentValues`,
 * `MediaStore`, `Environment`) que en un test JVM puro (sin Robolectric)
 * lanzan `RuntimeException: Method ... not mocked` -- mismo motivo por el
 * que `SecurityManagerTest` ya excluye `isBiometricAvailable()`.
 * `importFileToSecure()` (variante SAF de `importLocalFile()`, mismo
 * árbol de éxito/error/original-no-borrado) tampoco se cubre acá por la
 * misma razón: depende de `ContentResolver`/`DocumentsContract` reales.
 *
 * `SecurityManager` se mockea completo (a diferencia de `SecurityScreenTest`,
 * que lo usa real por ser una prueba de UI con Context real disponible) --
 * acá el objetivo es la lógica del ViewModel, no la del propio
 * `SecurityManager` (ya cubierta en `SecurityManagerTest`).
 *
 * `unlockAndLoadFiles()` (privado, disparado por `verifyPin`/`setupPin`)
 * corre en `Dispatchers.IO` real -- no virtual -- así que las aserciones
 * usan Turbine (`uiState.test { }`) para esperar la emisión real en vez de
 * asumir que ya terminó.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityViewModelTest {

    private lateinit var securityManager: SecurityManager
    private lateinit var pdfPasswordUseCase: PdfPasswordUseCase
    private lateinit var secureFolder: File

    private val testMessages = PdfPasswordMessages(
        readError = "readError", emptyFile = "emptyFile",
        protectSuccess = "protectSuccess", protectGenerateError = "protectGenerateError %1\$d",
        protectError = "protectError %1\$s", removeSuccess = "removeSuccess",
        removeGenerateError = "removeGenerateError %1\$d", removeError = "removeError %1\$s"
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val tempDir = Files.createTempDirectory("docsmart_security_vm_test_").toFile()
        secureFolder = File(tempDir, "secure").apply { mkdirs() }

        securityManager = mockk(relaxed = true)
        every { securityManager.secureFolder } returns secureFolder
        every { securityManager.hasPin() } returns false
        every { securityManager.isBiometricAvailable() } returns false
        every { securityManager.isBiometricEnabled() } returns false
        every { securityManager.getSecureFiles() } returns emptyList()

        pdfPasswordUseCase = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        secureFolder.parentFile?.deleteRecursively()
    }

    private fun buildViewModel() = SecurityViewModel(securityManager, pdfPasswordUseCase)

    // Con UnconfinedTestDispatcher no hay garantía de cuántas emisiones
    // intermedias produce un viewModelScope.launch{} antes de que el
    // colector de Turbine retome el control (ej. isPdfProcessing=true
    // puede o no alcanzar a observarse por separado del resultado final)
    // -- en vez de asumir un número exacto de awaitItem(), se espera hasta
    // que se cumpla la condición buscada.
    private suspend fun ReceiveTurbine<SecurityUiState>.awaitUntil(
        predicate: (SecurityUiState) -> Boolean
    ): SecurityUiState {
        var item = awaitItem()
        var attempts = 0
        while (!predicate(item) && attempts < 10) {
            item = awaitItem()
            attempts++
        }
        return item
    }

    // ── Estado inicial ─────────────────────────────────────────────────────────

    @Test
    fun `estado inicial refleja hasPin, isBiometricAvailable e isBiometricEnabled de SecurityManager`() {
        every { securityManager.hasPin() } returns true
        every { securityManager.isBiometricAvailable() } returns true
        every { securityManager.isBiometricEnabled() } returns true

        val state = buildViewModel().uiState.value

        assertEquals(SecurityScreenState.LOCKED, state.screenState)
        assertTrue(state.hasPin)
        assertTrue(state.isBiometricAvailable)
        assertTrue(state.isBiometricEnabled)
    }

    // ── Transiciones de pantalla ──────────────────────────────────────────────

    @Test
    fun `goToSetupPin cambia a SETUP_PIN`() {
        val viewModel = buildViewModel()
        viewModel.goToSetupPin()
        assertEquals(SecurityScreenState.SETUP_PIN, viewModel.uiState.value.screenState)
    }

    @Test
    fun `goToLocked cambia a LOCKED y limpia error`() = runTest {
        val viewModel = buildViewModel()
        every { securityManager.verifyPin("0000") } returns false
        viewModel.verifyPin("0000", "PIN incorrecto")
        assertEquals("PIN incorrecto", viewModel.uiState.value.error)

        viewModel.goToLocked()

        val state = viewModel.uiState.value
        assertEquals(SecurityScreenState.LOCKED, state.screenState)
        assertNull(state.error)
    }

    @Test
    fun `lockIfUnlocked bloquea cuando el estado es UNLOCKED (RF-SEC-08)`() = runTest {
        every { securityManager.verifyPin("1234") } returns true

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem() // estado inicial (LOCKED)
            viewModel.verifyPin("1234", "PIN incorrecto")
            assertEquals(SecurityScreenState.UNLOCKED, awaitItem().screenState)

            viewModel.lockIfUnlocked()

            assertEquals(SecurityScreenState.LOCKED, awaitItem().screenState)
        }
    }

    @Test
    fun `lockIfUnlocked no hace nada si el estado no es UNLOCKED`() {
        // No debe descartar un PIN a medio configurar (SETUP_PIN) solo
        // porque la app pasó un instante a segundo plano.
        val viewModel = buildViewModel()
        viewModel.goToSetupPin()

        viewModel.lockIfUnlocked()

        assertEquals(SecurityScreenState.SETUP_PIN, viewModel.uiState.value.screenState)
    }

    @Test
    fun `verifyPin con PIN correcto desbloquea y carga los archivos seguros`() = runTest {
        every { securityManager.verifyPin("1234") } returns true
        val secureFile = File(secureFolder, "documento.pdf").apply { writeText("x") }
        every { securityManager.getSecureFiles() } returns listOf(secureFile)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(SecurityScreenState.LOCKED, awaitItem().screenState)

            viewModel.verifyPin("1234", "PIN incorrecto")

            val unlocked = awaitItem()
            assertEquals(SecurityScreenState.UNLOCKED, unlocked.screenState)
            assertEquals(listOf(secureFile), unlocked.secureFiles)
            assertNull(unlocked.error)
        }
    }

    @Test
    fun `verifyPin con PIN incorrecto muestra error y no desbloquea`() {
        every { securityManager.verifyPin("0000") } returns false

        val viewModel = buildViewModel()
        viewModel.verifyPin("0000", "PIN incorrecto")

        val state = viewModel.uiState.value
        assertEquals("PIN incorrecto", state.error)
        assertEquals(SecurityScreenState.LOCKED, state.screenState)
    }

    @Test
    fun `setupPin exitoso marca hasPin y desbloquea`() = runTest {
        every { securityManager.setPin("1234") } returns true

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertFalse(awaitItem().hasPin)

            viewModel.setupPin("1234", "no debería mostrarse")

            assertTrue(awaitItem().hasPin)
            assertEquals(SecurityScreenState.UNLOCKED, awaitItem().screenState)
        }
    }

    @Test
    fun `setupPin fallido muestra error y no cambia hasPin ni el estado de pantalla`() {
        // Corregido 2026-08-26 (ver security.md §10): antes, si
        // SecurityManager.setPin() devolvía false, el ViewModel no hacía
        // nada -- ni error, ni cambio de estado. El usuario no se enteraba
        // de que falló.
        every { securityManager.setPin("1234") } returns false

        val viewModel = buildViewModel()
        viewModel.setupPin("1234", "No se pudo guardar el PIN")

        val state = viewModel.uiState.value
        assertFalse(state.hasPin)
        assertEquals(SecurityScreenState.LOCKED, state.screenState)
        assertEquals("No se pudo guardar el PIN", state.error)
    }

    // ── error / successMessage ────────────────────────────────────────────────

    @Test
    fun `dismissError limpia el error`() {
        every { securityManager.verifyPin("0000") } returns false
        val viewModel = buildViewModel()
        viewModel.verifyPin("0000", "PIN incorrecto")

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `dismissSuccess limpia el successMessage`() = runTest {
        val file = File(secureFolder.parentFile, "converted/foo.pdf").apply { parentFile?.mkdirs(); writeText("x") }
        every { securityManager.moveToSecure(file) } returns SecureMoveResult(success = true, originalDeleted = true)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem() // estado inicial
            viewModel.importLocalFile(file, "ok", "error", "original conservado")
            assertEquals("ok", awaitItem().successMessage)
        }

        viewModel.dismissSuccess()

        assertNull(viewModel.uiState.value.successMessage)
    }

    @Test
    fun `importLocalFile exitoso con original borrado muestra successMessage`() = runTest {
        val file = File(secureFolder.parentFile, "converted/foo.pdf").apply { parentFile?.mkdirs(); writeText("x") }
        every { securityManager.moveToSecure(file) } returns SecureMoveResult(success = true, originalDeleted = true)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.importLocalFile(file, "Archivo protegido", "Error", "Original conservado")
            assertEquals("Archivo protegido", awaitItem().successMessage)
        }
    }

    @Test
    fun `importLocalFile exitoso con original NO borrado muestra originalKeptMessage`() = runTest {
        val file = File(secureFolder.parentFile, "converted/foo.pdf").apply { parentFile?.mkdirs(); writeText("x") }
        every { securityManager.moveToSecure(file) } returns SecureMoveResult(success = true, originalDeleted = false)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.importLocalFile(file, "Archivo protegido", "Error", "Original conservado")
            assertEquals("Original conservado", awaitItem().successMessage)
        }
    }

    @Test
    fun `importLocalFile fallido muestra error`() = runTest {
        val file = File(secureFolder.parentFile, "converted/foo.pdf").apply { parentFile?.mkdirs(); writeText("x") }
        every { securityManager.moveToSecure(file) } returns SecureMoveResult(success = false, originalDeleted = false)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.importLocalFile(file, "Archivo protegido", "Error al proteger", "Original conservado")
            assertEquals("Error al proteger", awaitItem().error)
        }
    }

    // ── PDF Password ──────────────────────────────────────────────────────────

    @Test
    fun `protectPdfWithPassword exitoso actualiza pdfOutputFile y successMessage`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val uri = mockk<android.net.Uri>(relaxed = true)
        val outputFile = File(secureFolder, "protegido.pdf")
        coEvery {
            pdfPasswordUseCase.protect(context, uri, "1234", "doc", testMessages)
        } returns PdfPasswordResult.Success(outputFile, "PDF protegido")

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem() // estado inicial
            viewModel.protectPdfWithPassword(context, uri, "1234", "doc", testMessages, "Contraseña incorrecta")

            val result = awaitUntil { !it.isPdfProcessing }
            assertEquals(outputFile, result.pdfOutputFile)
            assertEquals("PDF protegido", result.successMessage)
        }
    }

    @Test
    fun `protectPdfWithPassword con error del UseCase actualiza pdfPasswordError`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val uri = mockk<android.net.Uri>(relaxed = true)
        coEvery {
            pdfPasswordUseCase.protect(context, uri, "1234", "doc", testMessages)
        } returns PdfPasswordResult.Error("No se pudo leer el PDF")

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem() // estado inicial
            viewModel.protectPdfWithPassword(context, uri, "1234", "doc", testMessages, "Contraseña incorrecta")

            val result = awaitUntil { !it.isPdfProcessing }
            assertEquals("No se pudo leer el PDF", result.pdfPasswordError)
        }
    }

    @Test
    fun `removePdfPassword con contraseña incorrecta usa wrongPasswordMessage`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val uri = mockk<android.net.Uri>(relaxed = true)
        coEvery {
            pdfPasswordUseCase.removePassword(context, uri, "0000", "doc", testMessages)
        } returns PdfPasswordResult.WrongPassword

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem() // estado inicial
            viewModel.removePdfPassword(context, uri, "0000", "doc", testMessages, "Contraseña incorrecta")

            val result = awaitUntil { !it.isPdfProcessing }
            assertEquals("Contraseña incorrecta", result.pdfPasswordError)
        }
    }

    @Test
    fun `setPdfPasswordMode actualiza el modo y limpia error y resultado previos`() {
        val viewModel = buildViewModel()
        viewModel.setPdfPasswordMode(PdfPasswordMode.PROTECT)
        assertEquals(PdfPasswordMode.PROTECT, viewModel.uiState.value.pdfPasswordMode)

        viewModel.setPdfPasswordMode(null)
        val state = viewModel.uiState.value
        assertNull(state.pdfPasswordMode)
        assertNull(state.pdfPasswordError)
        assertNull(state.pdfOutputFile)
    }

    // ── Archivos ──────────────────────────────────────────────────────────────

    @Test
    fun `deleteFile llama a SecurityManager y recarga secureFiles`() = runTest {
        val file = File(secureFolder, "a.pdf")
        every { securityManager.getSecureFiles() } returns listOf(file)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.deleteFile(file)
            assertEquals(listOf(file), awaitItem().secureFiles)
        }
        verify { securityManager.deleteSecureFile(file) }
    }

    @Test
    fun `restoreFile llama a SecurityManager y recarga secureFiles`() = runTest {
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns secureFolder.parentFile
        val file = File(secureFolder, "a.pdf")
        every { securityManager.getSecureFiles() } returns listOf(file)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.restoreFile(file, context)
            assertEquals(listOf(file), awaitItem().secureFiles)
        }
        verify { securityManager.moveFromSecure(file, File(secureFolder.parentFile, "converted")) }
    }

    @Test
    fun `reloadFiles recarga secureFiles desde SecurityManager`() = runTest {
        val file = File(secureFolder, "a.pdf")
        every { securityManager.getSecureFiles() } returns listOf(file)

        val viewModel = buildViewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.reloadFiles()
            assertEquals(listOf(file), awaitItem().secureFiles)
        }
    }

    // ── Biometría ─────────────────────────────────────────────────────────────

    @Test
    fun `toggleBiometric invierte isBiometricEnabled y persiste el cambio`() {
        every { securityManager.isBiometricEnabled() } returns false
        val viewModel = buildViewModel()
        assertFalse(viewModel.uiState.value.isBiometricEnabled)

        viewModel.toggleBiometric()

        assertTrue(viewModel.uiState.value.isBiometricEnabled)
        verify { securityManager.setBiometricEnabled(true) }
    }
}
