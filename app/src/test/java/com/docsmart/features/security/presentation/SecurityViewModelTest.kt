package com.docsmart.features.security.presentation

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.docsmart.core.security.SecureMoveResult
import com.docsmart.core.security.SecurityManager
import com.docsmart.features.library.data.MediaDeletePermission
import com.docsmart.features.security.domain.PdfPasswordMessages
import com.docsmart.features.security.domain.PdfPasswordResult
import com.docsmart.features.security.domain.PdfPasswordUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
    private lateinit var mediaDeletePermission: MediaDeletePermission
    private lateinit var documentIdentityMaintenance: com.docsmart.core.data.DocumentIdentityMaintenance
    private lateinit var secureFolder: File

    private val testMessages =
        PdfPasswordMessages(
            readError = "readError",
            emptyFile = "emptyFile",
            protectSuccess = "protectSuccess",
            protectGenerateError = "protectGenerateError %1\$d",
            protectError = "protectError %1\$s",
            removeSuccess = "removeSuccess",
            removeGenerateError = "removeGenerateError %1\$d",
            removeError = "removeError %1\$s",
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
        mediaDeletePermission = mockk(relaxed = true)
        documentIdentityMaintenance = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        secureFolder.parentFile?.deleteRecursively()
    }

    private fun buildViewModel() =
        SecurityViewModel(
            securityManager,
            pdfPasswordUseCase,
            mediaDeletePermission,
            documentIdentityMaintenance,
            // Hallazgo real de la revisión de seguridad adversarial de este
            // mismo lote (2026-09-16): ProcessLifecycleOwner real no se
            // inicializa en un test JVM plano -- AppLifecycleTracker
            // permite mockearlo en vez de romper la construcción del
            // ViewModel.
            mockk<com.docsmart.core.util.AppLifecycleTracker>(relaxed = true),
        )

    // Con UnconfinedTestDispatcher no hay garantía de cuántas emisiones
    // intermedias produce un viewModelScope.launch{} antes de que el
    // colector de Turbine retome el control (ej. isPdfProcessing=true
    // puede o no alcanzar a observarse por separado del resultado final)
    // -- en vez de asumir un número exacto de awaitItem(), se espera hasta
    // que se cumpla la condición buscada.
    private suspend fun ReceiveTurbine<SecurityUiState>.awaitUntil(predicate: (SecurityUiState) -> Boolean): SecurityUiState {
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
    fun `goToLocked cambia a LOCKED y limpia error`() =
        runTest {
            val viewModel = buildViewModel()
            every { securityManager.verifyPin("0000") } returns false
            viewModel.verifyPin("0000", "PIN incorrecto", "bloqueado %1\$d s")
            assertEquals("PIN incorrecto", viewModel.uiState.value.error)

            viewModel.goToLocked()

            val state = viewModel.uiState.value
            assertEquals(SecurityScreenState.LOCKED, state.screenState)
            assertNull(state.error)
        }

    @Test
    fun `lockIfUnlocked bloquea cuando el estado es UNLOCKED (RF-SEC-08)`() =
        runTest {
            every { securityManager.verifyPin("1234") } returns true

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitItem() // estado inicial (LOCKED)
                viewModel.verifyPin("1234", "PIN incorrecto", "bloqueado %1\$d s")
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
    fun `resetPin llama a SecurityManager, limpia secureFiles y hasPin, y pasa a SETUP_PIN (RF-SEC-09)`() =
        runTest {
            // Simula el caso real: el usuario estuvo desbloqueado (secureFiles con
            // datos), luego RF-SEC-08 lo bloqueó automáticamente -- secureFiles
            // sigue en el estado aunque la pantalla ya sea LOCKED. resetPin() debe
            // limpiarlo igual, no solo cuando arranca vacío.
            every { securityManager.hasPin() } returns true
            every { securityManager.verifyPin("1234") } returns true
            val secureFile = File(secureFolder, "documento.pdf").apply { writeText("x") }
            every { securityManager.getSecureFiles() } returns listOf(secureFile)

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertTrue(awaitItem().hasPin) // LOCKED inicial, con PIN ya configurado
                viewModel.verifyPin("1234", "PIN incorrecto", "bloqueado %1\$d s")
                assertEquals(SecurityScreenState.UNLOCKED, awaitItem().screenState)
                viewModel.lockIfUnlocked()
                assertEquals(SecurityScreenState.LOCKED, awaitItem().screenState)

                viewModel.resetPin()

                val reset = awaitItem()
                assertEquals(SecurityScreenState.SETUP_PIN, reset.screenState)
                assertFalse(reset.hasPin)
                assertTrue(reset.secureFiles.isEmpty())
                assertNull(reset.error)
            }
            verify { securityManager.resetPinAndWipeFiles() }
        }

    @Test
    fun `verifyPin con PIN correcto desbloquea y carga los archivos seguros`() =
        runTest {
            every { securityManager.verifyPin("1234") } returns true
            val secureFile = File(secureFolder, "documento.pdf").apply { writeText("x") }
            every { securityManager.getSecureFiles() } returns listOf(secureFile)

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(SecurityScreenState.LOCKED, awaitItem().screenState)

                viewModel.verifyPin("1234", "PIN incorrecto", "bloqueado %1\$d s")

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
        viewModel.verifyPin("0000", "PIN incorrecto", "bloqueado %1\$d s")

        val state = viewModel.uiState.value
        assertEquals("PIN incorrecto", state.error)
        assertEquals(SecurityScreenState.LOCKED, state.screenState)
    }

    @Test
    fun `verifyPin con bloqueo activo muestra el mensaje de bloqueo y no llama a SecurityManager`() {
        // Bug real encontrado 2026-09-14 (repaso general): el PIN no tenía
        // límite de intentos. Ahora SecurityViewModel debe respetar el
        // bloqueo que reporta SecurityManager.pinLockoutRemainingMillis()
        // antes de intentar verificar, sin llamar a verifyPin() del manager.
        every { securityManager.pinLockoutRemainingMillis() } returns 12_500L

        val viewModel = buildViewModel()
        viewModel.verifyPin("1234", "PIN incorrecto", "bloqueado %1\$d s")

        assertEquals("bloqueado 13 s", viewModel.uiState.value.error)
        assertEquals(SecurityScreenState.LOCKED, viewModel.uiState.value.screenState)
        verify(exactly = 0) { securityManager.verifyPin(any()) }
    }

    @Test
    fun `setupPin exitoso marca hasPin y desbloquea`() =
        runTest {
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
        viewModel.verifyPin("0000", "PIN incorrecto", "bloqueado %1\$d s")

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `dismissSuccess limpia el successMessage`() =
        runTest {
            val file =
                File(secureFolder.parentFile, "converted/foo.pdf").apply {
                    parentFile?.mkdirs()
                    writeText("x")
                }
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
    fun `importLocalFile exitoso con original borrado muestra successMessage`() =
        runTest {
            val file =
                File(secureFolder.parentFile, "converted/foo.pdf").apply {
                    parentFile?.mkdirs()
                    writeText("x")
                }
            every { securityManager.moveToSecure(file) } returns SecureMoveResult(success = true, originalDeleted = true)

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.importLocalFile(file, "Archivo protegido", "Error", "Original conservado")
                assertEquals("Archivo protegido", awaitItem().successMessage)
            }
        }

    // Pedido explícito del usuario 2026-09-12 (feedback de testers): este
    // aviso pasó de mostrarse como successMessage (mismo Snackbar genérico
    // que un éxito normal, fácil de perder) a un campo aparte que
    // SecurityScreen muestra en un diálogo bloqueante -- ver
    // originalNotDeletedWarning en SecurityViewModel.
    @Test
    fun `importLocalFile exitoso con original NO borrado muestra originalNotDeletedWarning`() =
        runTest {
            val file =
                File(secureFolder.parentFile, "converted/foo.pdf").apply {
                    parentFile?.mkdirs()
                    writeText("x")
                }
            every { securityManager.moveToSecure(file) } returns SecureMoveResult(success = true, originalDeleted = false)

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.importLocalFile(file, "Archivo protegido", "Error", "Original conservado")
                val state = awaitItem()
                assertEquals("Original conservado", state.originalNotDeletedWarning)
                assertNull(state.successMessage)
            }
        }

    @Test
    fun `importLocalFile fallido muestra error`() =
        runTest {
            val file =
                File(secureFolder.parentFile, "converted/foo.pdf").apply {
                    parentFile?.mkdirs()
                    writeText("x")
                }
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
    fun `protectPdfWithPassword exitoso actualiza pdfOutputFile y successMessage`() =
        runTest {
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
    fun `protectPdfWithPassword con error del UseCase actualiza pdfPasswordError`() =
        runTest {
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
    fun `removePdfPassword exitoso actualiza pdfOutputFile y successMessage`() =
        runTest {
            // Gap real: solo estaba cubierto el camino WrongPassword de
            // removePdfPassword() -- Success/Error son ramas propias del
            // mismo `when`, nunca ejercitadas.
            val context = mockk<Context>(relaxed = true)
            val uri = mockk<android.net.Uri>(relaxed = true)
            val outputFile = File(secureFolder, "abierto.pdf")
            coEvery {
                pdfPasswordUseCase.removePassword(context, uri, "1234", "doc", testMessages)
            } returns PdfPasswordResult.Success(outputFile, "Contraseña quitada")

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitItem() // estado inicial
                viewModel.removePdfPassword(context, uri, "1234", "doc", testMessages, "Contraseña incorrecta")

                val result = awaitUntil { !it.isPdfProcessing }
                assertEquals(outputFile, result.pdfOutputFile)
                assertEquals("Contraseña quitada", result.successMessage)
                assertNull(result.pdfPasswordError)
            }
        }

    @Test
    fun `removePdfPassword con error del UseCase actualiza pdfPasswordError`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val uri = mockk<android.net.Uri>(relaxed = true)
            coEvery {
                pdfPasswordUseCase.removePassword(context, uri, "1234", "doc", testMessages)
            } returns PdfPasswordResult.Error("No se pudo leer el PDF")

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitItem() // estado inicial
                viewModel.removePdfPassword(context, uri, "1234", "doc", testMessages, "Contraseña incorrecta")

                val result = awaitUntil { !it.isPdfProcessing }
                assertEquals("No se pudo leer el PDF", result.pdfPasswordError)
                assertNull(result.pdfOutputFile)
            }
        }

    @Test
    fun `removePdfPassword con contraseña incorrecta usa wrongPasswordMessage`() =
        runTest {
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
    fun `deleteFile llama a SecurityManager y recarga secureFiles`() =
        runTest {
            val file = File(secureFolder, "a.pdf")
            every { securityManager.getSecureFiles() } returns listOf(file)
            every { securityManager.deleteSecureFile(file) } returns true

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.deleteFile(file, "no se pudo eliminar")
                assertEquals(listOf(file), awaitItem().secureFiles)
            }
            verify { securityManager.deleteSecureFile(file) }
        }

    // Hallazgo real de la auditoría general 2026-09-17: deleteSecureFile()
    // puede devolver false sin lanzar excepción (File.delete() falla) --
    // antes se ignoraba el resultado y la metadata (favorito/alias/
    // anotaciones) se limpiaba igual aunque el archivo siguiera en disco.
    @Test
    fun `deleteFile no limpia metadata ni notifica exito si el borrado real falla`() =
        runTest {
            val file = File(secureFolder, "a.pdf")
            every { securityManager.getSecureFiles() } returns listOf(file)
            every { securityManager.deleteSecureFile(file) } returns false

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.deleteFile(file, "no se pudo eliminar")
                assertEquals("no se pudo eliminar", awaitItem().error)
            }
            coVerify(exactly = 0) { documentIdentityMaintenance.onPermanentlyDeleted(any()) }
        }

    @Test
    fun `restoreFile llama a SecurityManager y recarga secureFiles`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            every { context.filesDir } returns secureFolder.parentFile
            val file = File(secureFolder, "a.pdf")
            val destFile = File(secureFolder.parentFile, "converted/a.pdf")
            every { securityManager.moveFromSecure(file, any()) } returns
                SecureMoveResult(success = true, originalDeleted = true, destFile = destFile)
            every { securityManager.getSecureFiles() } returns listOf(file)

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.restoreFile(file, context, "no se pudo restaurar", "no se pudo eliminar la copia")
                assertEquals(listOf(file), awaitItem().secureFiles)
            }
            verify { securityManager.moveFromSecure(file, File(secureFolder.parentFile, "converted")) }
        }

    // Hallazgo real de la revisión adversarial de este mismo lote (M1): la
    // primera versión de restoreFile() solo miraba `originalDeleted`, no
    // `success` -- si moveFromSecure() fallaba por completo (destFile=null),
    // igual mostraba "Archivo restaurado, no se pudo borrar la copia" en vez
    // de un error real, mintiendo sobre un duplicado que nunca existió.
    @Test
    fun `restoreFile muestra un error y no toca secureFiles si moveFromSecure falla por completo`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            every { context.filesDir } returns secureFolder.parentFile
            val file = File(secureFolder, "a.pdf")
            every { securityManager.moveFromSecure(file, any()) } returns
                SecureMoveResult(success = false, originalDeleted = false, destFile = null)

            val viewModel = buildViewModel()
            viewModel.uiState.test {
                awaitItem()
                viewModel.restoreFile(file, context, "no se pudo restaurar", "no se pudo eliminar la copia")
                val afterFailure = awaitItem()
                assertEquals("no se pudo restaurar", afterFailure.error)
                assertNull(afterFailure.originalNotDeletedWarning)
                assertTrue(afterFailure.secureFiles.isEmpty(), "no debe tocar secureFiles si la restauración falló")
            }
            coVerify(exactly = 0) { documentIdentityMaintenance.onIdChanged(any(), any()) }
        }

    @Test
    fun `reloadFiles recarga secureFiles desde SecurityManager`() =
        runTest {
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

    // ── protectPdfWithPassword / removePdfPassword: guard de doble toque ──────

    @Test
    fun `protectPdfWithPassword ignora un segundo toque mientras la primera operacion sigue en curso`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val uri = mockk<android.net.Uri>(relaxed = true)
            coEvery { pdfPasswordUseCase.protect(any(), any(), any(), any(), any()) } coAnswers { awaitCancellation() }
            val viewModel = buildViewModel()

            viewModel.protectPdfWithPassword(context, uri, "1234", "doc", testMessages, "incorrecta")
            viewModel.protectPdfWithPassword(context, uri, "1234", "doc", testMessages, "incorrecta")

            assertTrue(viewModel.uiState.value.isPdfProcessing)
            coVerify(exactly = 1) { pdfPasswordUseCase.protect(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `removePdfPassword ignora un segundo toque mientras la primera operacion sigue en curso`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val uri = mockk<android.net.Uri>(relaxed = true)
            coEvery { pdfPasswordUseCase.removePassword(any(), any(), any(), any(), any()) } coAnswers
                { awaitCancellation() }
            val viewModel = buildViewModel()

            viewModel.removePdfPassword(context, uri, "1234", "doc", testMessages, "incorrecta")
            viewModel.removePdfPassword(context, uri, "1234", "doc", testMessages, "incorrecta")

            coVerify(exactly = 1) { pdfPasswordUseCase.removePassword(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `una nueva operacion PDF vuelve a estar permitida tras terminar la anterior`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val uri = mockk<android.net.Uri>(relaxed = true)
            coEvery { pdfPasswordUseCase.protect(any(), any(), any(), any(), any()) } returns
                PdfPasswordResult.WrongPassword
            val viewModel = buildViewModel()

            viewModel.protectPdfWithPassword(context, uri, "1234", "doc", testMessages, "incorrecta")
            assertFalse(viewModel.uiState.value.isPdfProcessing)
            viewModel.protectPdfWithPassword(context, uri, "1234", "doc", testMessages, "incorrecta")

            coVerify(exactly = 2) { pdfPasswordUseCase.protect(any(), any(), any(), any(), any()) }
            assertEquals("incorrecta", viewModel.uiState.value.pdfPasswordError)
        }

    @Test
    fun `dismissPdfResult limpia el archivo de salida y el error`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val uri = mockk<android.net.Uri>(relaxed = true)
            coEvery { pdfPasswordUseCase.protect(any(), any(), any(), any(), any()) } returns
                PdfPasswordResult.Success(File(secureFolder, "p.pdf"), "ok")
            val viewModel = buildViewModel()
            viewModel.protectPdfWithPassword(context, uri, "1234", "doc", testMessages, "incorrecta")
            assertTrue(viewModel.uiState.value.pdfOutputFile != null)

            viewModel.dismissPdfResult()

            assertNull(viewModel.uiState.value.pdfOutputFile)
            assertNull(viewModel.uiState.value.pdfPasswordError)
        }

    // ── previewFile ───────────────────────────────────────────────────────────

    @Test
    fun `previewFile emite la ruta de la copia efimera`() =
        runTest {
            val file = File(secureFolder, "a.pdf")
            val preview = File(secureFolder.parentFile, "secure_preview/a.pdf")
            every { securityManager.copyForPreview(file) } returns preview
            val viewModel = buildViewModel()

            viewModel.previewRequest.test {
                viewModel.previewFile(file, "no se pudo abrir")
                assertEquals(preview.absolutePath, awaitItem())
            }
        }

    @Test
    fun `previewFile muestra error si no se pudo crear la copia`() =
        runTest {
            val file = File(secureFolder, "a.pdf")
            every { securityManager.copyForPreview(file) } returns null
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.previewFile(file, "no se pudo abrir")
                assertEquals("no se pudo abrir", awaitItem().error)
            }
        }

    // ── RF-SEC-08: observer del proceso ───────────────────────────────────────

    @Test
    fun `ON_STOP del proceso bloquea la carpeta y borra la vista previa sin cifrar`() =
        runTest {
            every { securityManager.verifyPin("1234") } returns true
            val observerSlot = slot<LifecycleEventObserver>()
            val tracker = mockk<com.docsmart.core.util.AppLifecycleTracker>(relaxed = true)
            every { tracker.addObserver(capture(observerSlot)) } returns Unit
            val viewModel =
                SecurityViewModel(
                    securityManager,
                    pdfPasswordUseCase,
                    mediaDeletePermission,
                    documentIdentityMaintenance,
                    tracker,
                )
            viewModel.uiState.test {
                awaitItem()
                viewModel.verifyPin("1234", "PIN incorrecto", "bloqueado %1\$d s")
                assertEquals(SecurityScreenState.UNLOCKED, awaitItem().screenState)

                observerSlot.captured.onStateChanged(mockk(relaxed = true), Lifecycle.Event.ON_STOP)

                assertEquals(SecurityScreenState.LOCKED, awaitItem().screenState)
            }
            verify { securityManager.clearPreviewCache() }
        }

    @Test
    fun `otros eventos del proceso no bloquean ni borran la vista previa`() {
        val observerSlot = slot<LifecycleEventObserver>()
        val tracker = mockk<com.docsmart.core.util.AppLifecycleTracker>(relaxed = true)
        every { tracker.addObserver(capture(observerSlot)) } returns Unit
        SecurityViewModel(securityManager, pdfPasswordUseCase, mediaDeletePermission, documentIdentityMaintenance, tracker)

        observerSlot.captured.onStateChanged(mockk(relaxed = true), Lifecycle.Event.ON_START)

        verify(exactly = 0) { securityManager.clearPreviewCache() }
    }

    // ── importLocalFile / deleteFile / avisos ─────────────────────────────────

    @Test
    fun `dismissOriginalNotDeletedWarning limpia el aviso`() =
        runTest {
            val file =
                File(secureFolder.parentFile, "converted/foo.pdf").apply {
                    parentFile?.mkdirs()
                    writeText("x")
                }
            every { securityManager.moveToSecure(file) } returns SecureMoveResult(success = true, originalDeleted = false)
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.importLocalFile(file, "ok", "error", "original conservado")
                assertEquals("original conservado", awaitItem().originalNotDeletedWarning)

                viewModel.dismissOriginalNotDeletedWarning()

                assertNull(awaitItem().originalNotDeletedWarning)
            }
        }

    @Test
    fun `importLocalFile migra la identidad del documento a la ruta real elegida por SecurityManager`() =
        runTest {
            val file =
                File(secureFolder.parentFile, "converted/foo.pdf").apply {
                    parentFile?.mkdirs()
                    writeText("x")
                }
            val dest = File(secureFolder, "foo (1).pdf")
            every { securityManager.moveToSecure(file) } returns
                SecureMoveResult(success = true, originalDeleted = true, destFile = dest)
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.importLocalFile(file, "ok", "error", "conservado")
                awaitItem()
            }

            coVerify { documentIdentityMaintenance.onIdChanged(file.absolutePath, dest.absolutePath) }
        }

    @Test
    fun `deleteFile exitoso limpia la metadata del documento borrado`() =
        runTest {
            val file = File(secureFolder, "a.pdf")
            every { securityManager.deleteSecureFile(file) } returns true
            every { securityManager.getSecureFiles() } returns emptyList()
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.deleteFile(file, "no se pudo eliminar")
                // Sin cambio visible en el estado (lista vacia -> vacia): se espera a que el
                // hilo de IO termine verificando la llamada con un timeout.
                cancelAndIgnoreRemainingEvents()
            }

            coVerify(timeout = 2_000) { documentIdentityMaintenance.onPermanentlyDeleted(file.absolutePath) }
        }

    // ── copyStreamToSecureFile() ──────────────────────────────────────────────

    // Bug real corregido: openInputStream() devuelve null si el proveedor no
    // puede abrir el Uri -- antes la copia se saltaba en silencio y el flujo
    // seguia hasta borrar el ORIGINAL sin que existiera copia protegida.
    @Test
    fun `copyStreamToSecureFile con origen ilegible lanza y no deja archivo`() {
        val dest = File(secureFolder, "x.pdf")

        assertThrows(IllegalStateException::class.java) { copyStreamToSecureFile(null, dest) }

        assertFalse(dest.exists())
    }

    @Test
    fun `copyStreamToSecureFile copia todos los bytes`() {
        val dest = File(secureFolder, "x.pdf")
        val bytes = ByteArray(10_000) { (it % 251).toByte() }

        copyStreamToSecureFile(java.io.ByteArrayInputStream(bytes), dest)

        assertTrue(bytes.contentEquals(dest.readBytes()))
    }

    @Test
    fun `copyStreamToSecureFile elimina la copia parcial si la lectura falla a mitad`() {
        val dest = File(secureFolder, "x.pdf")
        val failing =
            object : java.io.InputStream() {
                private var sent = 0

                override fun read(): Int {
                    if (sent >= 5) throw java.io.IOException("se cayo la lectura")
                    sent++
                    return 1
                }
            }

        assertThrows(java.io.IOException::class.java) { copyStreamToSecureFile(failing, dest) }

        assertFalse(dest.exists(), "una copia a medias no debe quedar en Carpeta Segura")
    }
}
