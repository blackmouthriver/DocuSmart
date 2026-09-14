package com.docsmart.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class SecureMoveResult(val success: Boolean, val originalDeleted: Boolean)

@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        "docusmart_security", Context.MODE_PRIVATE
    )

    companion object {
        // Bug real encontrado 2026-09-14 (repaso general): el PIN de 4
        // dígitos (10.000 combinaciones) no tenía ningún límite de intentos
        // ni backoff -- nada throttleaba reintentos programáticos, a
        // diferencia del desbloqueo biométrico (limitado por el propio SO).
        private const val PIN_MAX_FREE_ATTEMPTS = 5
        private const val PIN_BASE_LOCKOUT_MS = 30_000L
        private const val PIN_MAX_LOCKOUT_MS = 5 * 60_000L

        // Bug real encontrado 2026-09-14 (repaso general): el hash del PIN
        // era SHA-256 de una sola pasada sin salt -- si `SharedPreferences`
        // "docusmart_security" se expone (backup, dispositivo rooteado), el
        // espacio de 4 dígitos se prueba offline en microsegundos (sin
        // salt, además, un rainbow table de las 10.000 combinaciones se
        // precalcula una sola vez para todos los usuarios). PBKDF2 con salt
        // aleatorio por instalación y 10.000 iteraciones hace que cada
        // intento offline cueste órdenes de magnitud más, y obliga a
        // recalcular por dispositivo.
        private const val PIN_HASH_ITERATIONS = 10_000
        private const val PIN_HASH_KEY_LENGTH_BITS = 256
    }

    // ── Carpeta segura ────────────────────────────────
    val secureFolder: File
        get() = File(context.filesDir, "secure").apply { mkdirs() }

    // ── PIN ───────────────────────────────────────────
    fun hasPin(): Boolean = prefs.getString("pin_hash", null) != null

    fun setPin(pin: String): Boolean {
        return try {
            val salt = generateSalt()
            val hash = hashPinWithSalt(pin, salt)
            prefs.edit()
                .putString("pin_hash", hash)
                .putString("pin_salt", Base64.getEncoder().encodeToString(salt))
                .putInt("pin_fail_count", 0)
                .remove("pin_lockout_until")
                .apply()
            Timber.d("SecurityManager: PIN configurado")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error configurando PIN")
            false
        }
    }

    // Milisegundos restantes de bloqueo por intentos fallidos (0 si no hay
    // bloqueo activo). El llamador debe consultarlo antes de aceptar un
    // nuevo intento de PIN.
    fun pinLockoutRemainingMillis(): Long {
        val lockUntil = prefs.getLong("pin_lockout_until", 0L)
        val remaining = lockUntil - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString("pin_hash", null)
        // Solo se intenta verificar (y solo se cuenta como intento fallido
        // en caso de no coincidir) si no hay un bloqueo activo y ya existe
        // un PIN configurado -- evita seguir acumulando bloqueo sobre un
        // bloqueo ya activo, o registrar intentos cuando nunca se configuró
        // un PIN.
        val canAttempt = pinLockoutRemainingMillis() == 0L && storedHash != null

        val matches = canAttempt && run {
            val saltB64 = prefs.getString("pin_salt", null)
            if (saltB64 != null) {
                val salt = Base64.getDecoder().decode(saltB64)
                hashPinWithSalt(pin, salt) == storedHash
            } else {
                // Formato legado (SHA-256 de una sola pasada, sin salt, de
                // instalaciones previas a este fix) -- se migra en silencio
                // al esquema salteado en el primer login correcto, sin
                // forzar al usuario a restablecer su PIN.
                val legacyMatches = hashPinLegacy(pin) == storedHash
                if (legacyMatches) setPin(pin)
                legacyMatches
            }
        }

        if (canAttempt) {
            if (matches) {
                prefs.edit().putInt("pin_fail_count", 0).remove("pin_lockout_until").apply()
            } else {
                registerFailedPinAttempt()
            }
        }
        return matches
    }

    private fun registerFailedPinAttempt() {
        val failCount = prefs.getInt("pin_fail_count", 0) + 1
        val editor = prefs.edit().putInt("pin_fail_count", failCount)
        if (failCount >= PIN_MAX_FREE_ATTEMPTS) {
            val extraFailures = (failCount - PIN_MAX_FREE_ATTEMPTS).coerceAtMost(10)
            val lockoutMs = (PIN_BASE_LOCKOUT_MS shl extraFailures).coerceAtMost(PIN_MAX_LOCKOUT_MS)
            editor.putLong("pin_lockout_until", System.currentTimeMillis() + lockoutMs)
            Timber.w("SecurityManager: PIN bloqueado ${lockoutMs}ms tras $failCount intentos fallidos")
        }
        editor.apply()
    }

    fun clearPin() {
        prefs.edit()
            .remove("pin_hash")
            .remove("pin_salt")
            .remove("pin_fail_count")
            .remove("pin_lockout_until")
            .apply()
        Timber.d("SecurityManager: PIN eliminado")
    }

    // RF-SEC-09/HU-SEC-06: único mecanismo de "recuperación" de PIN permitido
    // -- restablecer implica perder los archivos protegidos (RNF-SEC-02, es
    // una decisión de seguridad deliberada, no hay recuperación sin pérdida).
    fun resetPinAndWipeFiles() {
        secureFolder.listFiles()?.forEach { it.delete() }
        clearPin()
        Timber.d("SecurityManager: PIN restablecido y carpeta segura vaciada")
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashPinWithSalt(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_HASH_ITERATIONS, PIN_HASH_KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val hash = factory.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hashPinLegacy(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ── Biometría ─────────────────────────────────────
    fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isBiometricEnabled(): Boolean =
        prefs.getBoolean("biometric_enabled", false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }

    // ── Archivos seguros ──────────────────────────────
    fun getSecureFiles(): List<File> {
        return secureFolder.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    // RNF-SEC-01: File.delete() puede fallar sin lanzar excepción (devuelve
    // false) — antes se ignoraba el resultado y siempre se reportaba éxito,
    // aunque el original hubiera quedado accesible en su ubicación. Ahora se
    // propaga para que el llamador pueda avisar en vez de fallar en silencio,
    // igual que ya hacía importFileToSecure() para Uris de SAF.
    fun moveToSecure(file: File): SecureMoveResult {
        return try {
            val dest = File(secureFolder, file.name)
            file.copyTo(dest, overwrite = true)
            val originalDeleted = file.delete()
            if (originalDeleted) {
                Timber.d("SecurityManager: archivo movido a carpeta segura: ${file.name}")
            } else {
                Timber.w("SecurityManager: archivo copiado pero no se pudo eliminar el original: ${file.name}")
            }
            SecureMoveResult(success = true, originalDeleted = originalDeleted)
        } catch (e: Exception) {
            Timber.e(e, "Error moviendo archivo a carpeta segura")
            SecureMoveResult(success = false, originalDeleted = false)
        }
    }

    fun moveFromSecure(file: File, destDir: File): Boolean {
        return try {
            val dest = File(destDir, file.name)
            file.copyTo(dest, overwrite = true)
            file.delete()
            Timber.d("SecurityManager: archivo restaurado: ${file.name}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error restaurando archivo")
            false
        }
    }

    fun deleteSecureFile(file: File): Boolean {
        return try {
            file.delete()
            Timber.d("SecurityManager: archivo eliminado: ${file.name}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error eliminando archivo seguro")
            false
        }
    }

    fun getSecureFolderSize(): Long {
        return secureFolder.listFiles()?.sumOf { it.length() } ?: 0L
    }
}