package com.docsmart.core.security

import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Cubre RF-SEC-01/02/09, RNF-SEC-04 y RF-SEC-05 (docs/requirements/security.md).
 * `isBiometricAvailable()` queda fuera de alcance aquí: depende de PackageManager,
 * que requiere Robolectric/instrumentación — no un unit test JVM puro.
 */
class SecurityManagerTest {

    private lateinit var filesDir: File
    private lateinit var securityManager: SecurityManager

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_security_test_").toFile()
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns fakeSharedPreferences()
        every { context.filesDir } returns filesDir
        securityManager = SecurityManager(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    // ── PIN (RF-SEC-01/02, RNF-SEC-04) ────────────────────────────────────────

    @Test
    fun `hasPin es false antes de configurar un PIN`() {
        assertFalse(securityManager.hasPin())
    }

    @Test
    fun `setPin seguido de verifyPin con el mismo PIN es exitoso`() {
        securityManager.setPin("1234")

        assertTrue(securityManager.hasPin())
        assertTrue(securityManager.verifyPin("1234"))
    }

    @Test
    fun `verifyPin rechaza un PIN incorrecto`() {
        securityManager.setPin("1234")

        assertFalse(securityManager.verifyPin("9999"))
    }

    @Test
    fun `verifyPin es false si nunca se configuro un PIN`() {
        assertFalse(securityManager.verifyPin("0000"))
    }

    @Test
    fun `clearPin elimina el PIN configurado`() {
        securityManager.setPin("1234")

        securityManager.clearPin()

        assertFalse(securityManager.hasPin())
        assertFalse(securityManager.verifyPin("1234"))
    }

    // ── Biometría (preferencia, no disponibilidad del sensor) ────────────────

    @Test
    fun `isBiometricEnabled es false por defecto`() {
        assertFalse(securityManager.isBiometricEnabled())
    }

    @Test
    fun `setBiometricEnabled persiste el valor`() {
        securityManager.setBiometricEnabled(true)

        assertTrue(securityManager.isBiometricEnabled())
    }

    // ── Carpeta segura — RF-SEC-05: proteger debe copiar Y borrar el original ──

    @Test
    fun `moveToSecure copia el archivo a la carpeta segura y elimina el original`() {
        val original = File(filesDir, "documento.pdf").apply { writeText("contenido de prueba") }

        val result = securityManager.moveToSecure(original)

        assertTrue(result.success)
        assertTrue(result.originalDeleted, "RNF-SEC-01: debe reportar si el original se pudo eliminar")
        assertFalse(original.exists(), "el archivo original debe eliminarse tras protegerlo")
        val secureFile = File(securityManager.secureFolder, "documento.pdf")
        assertTrue(secureFile.exists())
        assertEquals("contenido de prueba", secureFile.readText())
    }

    @Test
    fun `moveToSecure falla limpiamente si el archivo original no existe`() {
        // No se puede forzar de forma confiable y multiplataforma que
        // File#delete() falle tras una copia exitosa (el comportamiento de
        // permisos difiere entre Windows y Linux/CI) — este test cubre la
        // otra vía real de fallo: el archivo desaparece antes de copiarlo.
        val inexistente = File(filesDir, "no_existe.pdf")

        val result = securityManager.moveToSecure(inexistente)

        assertFalse(result.success)
        assertFalse(result.originalDeleted)
    }

    @Test
    fun `getSecureFiles lista los archivos protegidos, el mas reciente primero`() {
        val fileA = File(filesDir, "a.pdf").apply { writeText("a") }
        val fileB = File(filesDir, "b.pdf").apply { writeText("b") }
        securityManager.moveToSecure(fileA)
        Thread.sleep(10)
        securityManager.moveToSecure(fileB)

        val files = securityManager.getSecureFiles()

        assertEquals(2, files.size)
        assertEquals("b.pdf", files.first().name)
    }

    @Test
    fun `moveFromSecure restaura el archivo y lo quita de la carpeta segura`() {
        val original = File(filesDir, "restaurar.pdf").apply { writeText("x") }
        securityManager.moveToSecure(original)
        val secureFile = File(securityManager.secureFolder, "restaurar.pdf")
        val destDir = File(filesDir, "converted").apply { mkdirs() }

        val restored = securityManager.moveFromSecure(secureFile, destDir)

        assertTrue(restored)
        assertFalse(secureFile.exists())
        assertTrue(File(destDir, "restaurar.pdf").exists())
    }

    @Test
    fun `deleteSecureFile elimina permanentemente el archivo protegido`() {
        val original = File(filesDir, "eliminar.pdf").apply { writeText("x") }
        securityManager.moveToSecure(original)
        val secureFile = File(securityManager.secureFolder, "eliminar.pdf")

        val deleted = securityManager.deleteSecureFile(secureFile)

        assertTrue(deleted)
        assertFalse(secureFile.exists())
    }

    @Test
    fun `getSecureFolderSize suma el tamano de todos los archivos protegidos`() {
        val fileA = File(filesDir, "a.txt").apply { writeText("12345") }      // 5 bytes
        val fileB = File(filesDir, "b.txt").apply { writeText("1234567890") } // 10 bytes
        securityManager.moveToSecure(fileA)
        securityManager.moveToSecure(fileB)

        assertEquals(15L, securityManager.getSecureFolderSize())
    }

    // ── Fake mínimo de SharedPreferences con semántica real de lectura/escritura ──

    private fun fakeSharedPreferences(): SharedPreferences {
        val store = mutableMapOf<String, Any?>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String?>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Boolean>()
            editor
        }
        every { editor.remove(any()) } answers {
            store.remove(firstArg<String>())
            editor
        }
        every { editor.apply() } just Runs

        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers {
            (store[firstArg<String>()] as? String) ?: secondArg()
        }
        every { prefs.getBoolean(any(), any()) } answers {
            (store[firstArg<String>()] as? Boolean) ?: secondArg()
        }
        return prefs
    }
}
