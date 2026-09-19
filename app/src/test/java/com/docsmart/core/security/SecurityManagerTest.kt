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
import org.junit.jupiter.api.Assertions.assertNotNull
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
    private lateinit var cacheDir: File
    private lateinit var securityManager: SecurityManager
    private lateinit var prefsStore: MutableMap<String, Any?>

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_security_test_").toFile()
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns fakeSharedPreferences()
        cacheDir = Files.createTempDirectory("docsmart_security_cache_").toFile()
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        securityManager = SecurityManager(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
        cacheDir.deleteRecursively()
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
    fun `setPin guarda un salt propio y no el hash en texto plano derivable sin el`() {
        // Bug real encontrado 2026-09-14 (repaso general): el hash del PIN
        // era SHA-256 de una sola pasada sin salt -- ahora debe guardarse un
        // salt aleatorio por instalación junto al hash salteado.
        securityManager.setPin("1234")

        val salt = prefsStore["pin_salt"] as? String
        assertTrue(salt != null && salt.isNotBlank())
    }

    @Test
    fun `verifyPin migra en silencio un hash legado (sin salt) al esquema salteado`() {
        // Simula una instalación previa a este fix: pin_hash con SHA-256 de
        // una sola pasada, sin pin_salt.
        val legacyHash =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest("1234".toByteArray())
                .joinToString("") { "%02x".format(it) }
        prefsStore["pin_hash"] = legacyHash

        assertTrue(securityManager.verifyPin("1234"), "debe aceptar el PIN correcto con el hash legado")
        assertTrue((prefsStore["pin_salt"] as? String)?.isNotBlank() == true, "debe migrar a un hash salteado")

        // El PIN sigue siendo válido tras la migración, ahora vía el
        // esquema nuevo (ya no queda ningún pin_salt nulo).
        assertTrue(securityManager.verifyPin("1234"))
    }

    @Test
    fun `verifyPin bloquea tras 5 intentos fallidos seguidos`() {
        // Bug real encontrado 2026-09-14 (repaso general): el PIN de 4
        // dígitos no tenía límite de intentos -- ahora debe bloquearse tras
        // PIN_MAX_FREE_ATTEMPTS (5) fallos seguidos.
        securityManager.setPin("1234")
        repeat(5) { assertFalse(securityManager.verifyPin("0000")) }

        assertTrue(securityManager.pinLockoutRemainingMillis() > 0)
        // Con el bloqueo activo, ni siquiera el PIN correcto debe pasar.
        assertFalse(securityManager.verifyPin("1234"))
    }

    @Test
    fun `verifyPin exitoso resetea el contador de intentos fallidos`() {
        securityManager.setPin("1234")
        repeat(4) { securityManager.verifyPin("0000") }

        assertTrue(securityManager.verifyPin("1234"))

        assertEquals(0, prefsStore["pin_fail_count"])
        assertEquals(0L, securityManager.pinLockoutRemainingMillis())
    }

    @Test
    fun `clearPin elimina el PIN configurado`() {
        securityManager.setPin("1234")

        securityManager.clearPin()

        assertFalse(securityManager.hasPin())
        assertFalse(securityManager.verifyPin("1234"))
    }

    @Test
    fun `resetPinAndWipeFiles elimina el PIN y todos los archivos de la carpeta segura`() {
        securityManager.setPin("1234")
        val fileA = File(filesDir, "a.pdf").apply { writeText("a") }
        val fileB = File(filesDir, "b.pdf").apply { writeText("b") }
        securityManager.moveToSecure(fileA)
        securityManager.moveToSecure(fileB)
        assertEquals(2, securityManager.getSecureFiles().size)

        securityManager.resetPinAndWipeFiles()

        assertFalse(securityManager.hasPin())
        assertFalse(securityManager.verifyPin("1234"))
        assertTrue(securityManager.getSecureFiles().isEmpty())
    }

    @Test
    fun `resetPinAndWipeFiles tambien borra la copia en claro de la vista previa`() {
        // Ronda 16: cacheDir/secure_preview sobrevivia al restablecer el PIN.
        val secured = File(filesDir, "doc.pdf").apply { writeText("secreto") }
        val moved = securityManager.moveToSecure(secured).destFile!!
        val preview = securityManager.copyForPreview(moved)!!
        assertTrue(preview.exists())

        securityManager.resetPinAndWipeFiles()

        assertFalse(preview.exists())
    }

    @Test
    fun `verifyPin con pin_salt corrupto devuelve false sin lanzar y cuenta como intento fallido`() {
        // Ronda 16: Base64 invalido lanzaba IllegalArgumentException.
        securityManager.setPin("1234")
        prefsStore["pin_salt"] = "!!!no es base64!!!"

        assertFalse(securityManager.verifyPin("1234"))
        assertEquals(1, prefsStore["pin_fail_count"])
    }

    @Test
    fun `el bloqueo se duplica en cada fallo tras los intentos libres`() {
        securityManager.setPin("1234")
        repeat(5) { securityManager.verifyPin("0000") }
        val first = securityManager.pinLockoutRemainingMillis()
        assertTrue(first in 1..30_000L)

        // Expira el bloqueo y falla otra vez: el siguiente bloqueo es de 60 s.
        prefsStore["pin_lockout_until"] = 0L
        assertFalse(securityManager.verifyPin("0000"))

        assertTrue(securityManager.pinLockoutRemainingMillis() > 30_000L)
    }

    @Test
    fun `tras expirar el bloqueo el PIN correcto entra y limpia el estado`() {
        securityManager.setPin("1234")
        repeat(5) { securityManager.verifyPin("0000") }
        prefsStore["pin_lockout_until"] = 0L

        assertTrue(securityManager.verifyPin("1234"))

        assertEquals(0, prefsStore["pin_fail_count"])
        assertFalse(prefsStore.containsKey("pin_lockout_until"))
    }

    @Test
    fun `moveToSecure con un destino ocupado no pisa y devuelve el destino real`() {
        val first = File(filesDir, "Scan.pdf").apply { writeText("uno") }
        val second = File(filesDir, "sub").apply { mkdirs() }.let { File(it, "Scan.pdf").apply { writeText("dos") } }

        val r1 = securityManager.moveToSecure(first)
        val r2 = securityManager.moveToSecure(second)

        assertEquals("uno", r1.destFile!!.readText())
        assertEquals("dos", r2.destFile!!.readText())
        assertEquals("Scan (1).pdf", r2.destFile!!.name)
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

        val result = securityManager.moveFromSecure(secureFile, destDir)

        assertTrue(result.success)
        assertTrue(result.originalDeleted)
        assertNotNull(result.destFile)
        assertFalse(secureFile.exists())
        assertTrue(File(destDir, "restaurar.pdf").exists())
    }

    @Test
    fun `moveFromSecure falla limpiamente si el archivo protegido no existe`() {
        // Mismo límite ya documentado para moveToSecure() arriba: no se
        // puede forzar de forma confiable y multiplataforma que
        // File#delete() falle tras una copia exitosa -- este test cubre
        // la otra vía real de fallo (M1: el resultado ahora se propaga en
        // vez de ignorarse, igual que ya hacía moveToSecure()).
        val inexistente = File(securityManager.secureFolder, "no_existe.pdf")
        val destDir = File(filesDir, "converted").apply { mkdirs() }

        val result = securityManager.moveFromSecure(inexistente, destDir)

        assertFalse(result.success)
        assertFalse(result.originalDeleted)
    }

    @Test
    fun `moveToSecure y moveFromSecure no se pisan si dos archivos comparten nombre`() {
        // Hallazgo real de la revisión general 2026-09-16 (#61): antes
        // sobrescribían en silencio con overwrite=true si el nombre ya
        // existía en destino.
        val dirA = File(filesDir, "dirA").apply { mkdirs() }
        val dirB = File(filesDir, "dirB").apply { mkdirs() }
        val fileA = File(dirA, "mismo_nombre.pdf").apply { writeText("contenido A") }
        val fileB = File(dirB, "mismo_nombre.pdf").apply { writeText("contenido B") }

        val resultA = securityManager.moveToSecure(fileA)
        val resultB = securityManager.moveToSecure(fileB)

        assertTrue(resultA.success)
        assertTrue(resultB.success)
        assertNotNull(resultA.destFile)
        assertNotNull(resultB.destFile)
        assertTrue(resultA.destFile!!.absolutePath != resultB.destFile!!.absolutePath)
        assertEquals("contenido A", resultA.destFile!!.readText())
        assertEquals("contenido B", resultB.destFile!!.readText())
        assertEquals(2, securityManager.getSecureFiles().size)
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
        val fileA = File(filesDir, "a.txt").apply { writeText("12345") } // 5 bytes
        val fileB = File(filesDir, "b.txt").apply { writeText("1234567890") } // 10 bytes
        securityManager.moveToSecure(fileA)
        securityManager.moveToSecure(fileB)

        assertEquals(15L, securityManager.getSecureFolderSize())
    }

    // ── Fake mínimo de SharedPreferences con semántica real de lectura/escritura ──

    private fun fakeSharedPreferences(): SharedPreferences {
        val store = mutableMapOf<String, Any?>()
        prefsStore = store
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String?>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Boolean>()
            editor
        }
        // Bug real encontrado 2026-09-14: el fake solo soportaba
        // String/Boolean -- el fix de bloqueo de intentos (pin_fail_count,
        // pin_lockout_until) usa Int/Long, y sin estos stubs cualquier test
        // que llamara a setPin()/verifyPin() fallaba con una llamada no
        // mockeada del editor.
        every { editor.putInt(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Int>()
            editor
        }
        every { editor.putLong(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Long>()
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
        every { prefs.getInt(any(), any()) } answers {
            (store[firstArg<String>()] as? Int) ?: secondArg()
        }
        every { prefs.getLong(any(), any()) } answers {
            (store[firstArg<String>()] as? Long) ?: secondArg()
        }
        return prefs
    }
}
