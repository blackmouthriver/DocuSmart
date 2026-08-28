package com.docsmart.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
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

    // ── Carpeta segura ────────────────────────────────
    val secureFolder: File
        get() = File(context.filesDir, "secure").apply { mkdirs() }

    // ── PIN ───────────────────────────────────────────
    fun hasPin(): Boolean = prefs.getString("pin_hash", null) != null

    fun setPin(pin: String): Boolean {
        return try {
            val hash = hashPin(pin)
            prefs.edit().putString("pin_hash", hash).apply()
            Timber.d("SecurityManager: PIN configurado")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error configurando PIN")
            false
        }
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString("pin_hash", null) ?: return false
        return hashPin(pin) == storedHash
    }

    fun clearPin() {
        prefs.edit().remove("pin_hash").apply()
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

    private fun hashPin(pin: String): String {
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