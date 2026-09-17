package com.docsmart.core.security

import android.content.Context
import android.os.SystemClock
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

data class SecureMoveResult(
    val success: Boolean,
    val originalDeleted: Boolean,
    // Hallazgo real de la revisión general 2026-09-16 (#61): antes el
    // llamador recalculaba File(secureFolder, file.name) por su cuenta para
    // migrar anotaciones -- si acá se eligió un nombre único distinto
    // (colisión), esa migración terminaba apuntando al archivo equivocado.
    // Se expone el destino real para que el llamador nunca tenga que
    // adivinarlo.
    val destFile: File? = null
)

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
    //
    // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
    // Media): calcular esto solo con System.currentTimeMillis() (reloj de
    // pared, ajustable por el usuario) permitía evadir el bloqueo
    // adelantando la fecha del dispositivo.
    //
    // Hallazgo real de la revisión adversarial de seguridad sobre el
    // primer fix (combinar con SystemClock.elapsedRealtime() y tomar el
    // máximo): un ataque de 2 pasos seguía funcionando -- adelantar el
    // reloj (elapsedRealtime todavía protegía) y LUEGO reiniciar el
    // dispositivo, lo que reinicia elapsedRealtime a un valor chico y
    // descarta esa protección, cayendo de nuevo al reloj de pared ya
    // manipulado (y que sigue así tras el reinicio, el RTC no se
    // autocorrige). Se reemplaza por un "reloj de confianza" anclado:
    // `trustedNowMillis()` reconstruye el tiempo real transcurrido desde
    // el último punto de confianza usando SOLO elapsedRealtime (que el
    // usuario no puede adelantar), y usa el MÍNIMO contra el reloj de
    // pared actual -- así, adelantar el reloj de pared nunca puede hacer
    // avanzar el tiempo "de confianza" más rápido que el tiempo real. Si
    // se detecta un reinicio (elapsedRealtime retrocedió), el ancla se
    // congela en su último valor de confianza en vez de saltar al reloj
    // de pared ya manipulado -- vuelve a avanzar en tiempo real desde ahí
    // con el elapsedRealtime del arranque nuevo. Costo aceptado: si el
    // dispositivo se reinicia de verdad a mitad de un bloqueo legítimo
    // (OS update, batería), el tiempo transcurrido durante ese hueco no
    // cuenta -- en el peor caso se espera el bloqueo completo de nuevo,
    // acotado por PIN_MAX_LOCKOUT_MS (unos minutos), un costo de UX menor
    // aceptable para cerrar un bypass real de seguridad.
    fun pinLockoutRemainingMillis(): Long {
        val lockUntilWall = prefs.getLong("pin_lockout_until", 0L)
        return (lockUntilWall - trustedNowMillis()).coerceAtLeast(0L)
    }

    // Ver el comentario de pinLockoutRemainingMillis(). El ancla nunca
    // retrocede (persiste el máximo de confianza visto hasta ahora), y se
    // reestablece en el punto de confianza anterior (no en el reloj de
    // pared, potencialmente manipulado) apenas se detecta un reinicio.
    private fun trustedNowMillis(): Long {
        val anchorWall    = prefs.getLong("pin_trust_anchor_wall", 0L)
        val anchorElapsed = prefs.getLong("pin_trust_anchor_elapsed", 0L)
        val currentWall    = System.currentTimeMillis()
        val currentElapsed = elapsedRealtimeMillis()

        if (anchorWall == 0L) {
            prefs.edit()
                .putLong("pin_trust_anchor_wall", currentWall)
                .putLong("pin_trust_anchor_elapsed", currentElapsed)
                .apply()
            return currentWall
        }

        val rebooted = currentElapsed < anchorElapsed
        val reconstructedNow = if (rebooted) anchorWall else anchorWall + (currentElapsed - anchorElapsed)
        val trustedNow = minOf(currentWall, reconstructedNow)

        if (trustedNow > anchorWall || rebooted) {
            prefs.edit()
                .putLong("pin_trust_anchor_wall", trustedNow)
                .putLong("pin_trust_anchor_elapsed", currentElapsed)
                .apply()
        }
        return trustedNow
    }

    // SystemClock.elapsedRealtime() no está disponible sin Robolectric en
    // los tests unitarios JVM de este proyecto (lanza RuntimeException,
    // "not mocked") -- se degrada con gracia a currentTimeMillis() en ese
    // caso. En un dispositivo real nunca lanza, así que esto no cambia el
    // comportamiento en producción.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun elapsedRealtimeMillis(): Long =
        try {
            SystemClock.elapsedRealtime()
        } catch (e: RuntimeException) {
            System.currentTimeMillis()
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
                prefs.edit().putInt("pin_fail_count", 0)
                    .remove("pin_lockout_until")
                    .apply()
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
            // trustedNowMillis(), no currentTimeMillis() -- ver el
            // comentario de pinLockoutRemainingMillis().
            editor.putLong("pin_lockout_until", trustedNowMillis() + lockoutMs)
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
            val dest = uniqueDestination(secureFolder, file.name)
            file.copyTo(dest, overwrite = false)
            val originalDeleted = file.delete()
            if (originalDeleted) {
                Timber.d("SecurityManager: archivo movido a carpeta segura: ${dest.name}")
            } else {
                Timber.w("SecurityManager: archivo copiado pero no se pudo eliminar el original: ${dest.name}")
            }
            SecureMoveResult(success = true, originalDeleted = originalDeleted, destFile = dest)
        } catch (e: Exception) {
            Timber.e(redactedForLog(e), "Error moviendo archivo a carpeta segura")
            SecureMoveResult(success = false, originalDeleted = false)
        }
    }

    // Hallazgo real de la auditoría general 2026-09-17 (M1): mismo problema
    // que moveToSecure() antes de RNF-SEC-01 -- file.delete() puede fallar
    // sin lanzar excepción, y el resultado se ignoraba. Si falla, el
    // archivo queda duplicado (restaurado en `destDir` Y todavía protegido
    // en `secure/`), sin ningún aviso. Reutiliza SecureMoveResult (mismo
    // shape que ya usa moveToSecure) para que el llamador pueda avisar.
    fun moveFromSecure(file: File, destDir: File): SecureMoveResult {
        return try {
            val dest = uniqueDestination(destDir, file.name)
            file.copyTo(dest, overwrite = false)
            val originalDeleted = file.delete()
            if (originalDeleted) {
                Timber.d("SecurityManager: archivo restaurado: ${dest.name}")
            } else {
                Timber.w("SecurityManager: archivo restaurado pero no se pudo eliminar de Carpeta Segura: ${dest.name}")
            }
            SecureMoveResult(success = true, originalDeleted = originalDeleted, destFile = dest)
        } catch (e: Exception) {
            Timber.e(redactedForLog(e), "Error restaurando archivo")
            SecureMoveResult(success = false, originalDeleted = false)
        }
    }

    // Hallazgo real de la revisión general 2026-09-16 (cuarta pasada):
    // IOException/FileNotFoundException reales de Java traen la ruta
    // absoluta completa en su propio `.message` (ej. disco lleno, permiso
    // denegado, colisión de nombre) -- CrashlyticsTree reenvía CUALQUIER
    // Throwable pasado a Timber.e(t, ...) a
    // FirebaseCrashlytics.recordException(t), que lo sube a un servidor de
    // Google. Para los catches de esta clase (todos sobre archivos de
    // Carpeta Segura), eso filtraba la ruta real -- y con ella la
    // existencia/nombre de un documento protegido -- contradiciendo la
    // promesa "100% local" sin ningún aviso al usuario. Se registra un
    // Throwable nuevo (mismo tipo, stack trace del propio catch) en vez del
    // original, para conservar valor de diagnóstico sin filtrar la ruta.
    private fun redactedForLog(e: Exception) = RuntimeException("SecurityManager: ${e.javaClass.simpleName}")

    // Hallazgo real de la revisión general 2026-09-16 (#61): moveToSecure()/
    // moveFromSecure() usaban File(dir, file.name) con overwrite=true -- si
    // dos documentos distintos comparten nombre (plausible: "Scan.pdf",
    // "Documento.pdf") y ambos se mueven a/desde Carpeta Segura, el segundo
    // sobrescribía en silencio el contenido del primero, sin avisar al
    // usuario. Si el nombre ya existe en destino, agrega un sufijo numérico
    // antes de la extensión hasta encontrar uno libre.
    private fun uniqueDestination(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dotIndex = name.lastIndexOf('.')
        val base = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val ext  = if (dotIndex > 0) name.substring(dotIndex) else ""
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($suffix)$ext")
            suffix++
        }
        return candidate
    }

    // Usado por importFileToSecure() (SecurityViewModel) -- ese camino copia
    // directo desde un content:// de SAF, sin pasar por moveToSecure(), pero
    // necesita el mismo criterio de nombre único que #61 corrigió acá.
    fun uniqueSecureDestination(fileName: String): File = uniqueDestination(secureFolder, fileName)

    // Hallazgo real de la auditoría general 2026-09-17: igual que
    // moveToSecure()/moveFromSecure(), File.delete() puede fallar sin
    // lanzar excepción (devuelve false) -- antes se ignoraba el resultado
    // y siempre se reportaba éxito, aunque el archivo protegido hubiera
    // quedado intacto en disco mientras su metadata (favorito/alias/
    // anotaciones) ya se había limpiado en SecurityViewModel.deleteFile().
    fun deleteSecureFile(file: File): Boolean {
        return try {
            val deleted = file.delete()
            if (deleted) {
                Timber.d("SecurityManager: archivo eliminado: ${file.name}")
            } else {
                Timber.w("SecurityManager: no se pudo eliminar el archivo seguro: ${file.name}")
            }
            deleted
        } catch (e: Exception) {
            Timber.e(redactedForLog(e), "Error eliminando archivo seguro")
            false
        }
    }

    fun getSecureFolderSize(): Long {
        return secureFolder.listFiles()?.sumOf { it.length() } ?: 0L
    }

    // Hallazgo real de la revisión general 2026-09-16 (#53): antes la única
    // forma de ver un archivo protegido era restaurarlo primero (sacándolo de
    // Carpeta Segura de forma permanente). Se copia a una carpeta de caché
    // aparte, EXCLUIDA a propósito de file_provider_paths.xml (hallazgo #59)
    // -- el Visor interno la lee como archivo local, sin pasar por
    // FileProvider, así que no hace falta declararla ahí. clearPreviewCache()
    // se llama antes de crear una copia nueva y también al bloquear la
    // Carpeta Segura (ver SecurityScreen.kt, RF-SEC-08), para que nunca quede
    // una copia sin cifrar más tiempo del necesario para la vista previa.
    private val previewCacheFolder: File
        get() = File(context.cacheDir, "secure_preview").apply { mkdirs() }

    fun clearPreviewCache() {
        previewCacheFolder.listFiles()?.forEach { it.delete() }
    }

    fun copyForPreview(file: File): File? {
        return try {
            clearPreviewCache()
            val dest = File(previewCacheFolder, file.name)
            file.copyTo(dest, overwrite = true)
            dest
        } catch (e: Exception) {
            Timber.e(redactedForLog(e), "Error copiando archivo seguro para vista previa")
            null
        }
    }
}