package com.docsmart.features.scanner.domain

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado del contenido de un QR protegido con contraseña (RF-SEC-13/14).
 * AES-256/GCM con clave derivada de la contraseña vía PBKDF2 (salt e IV
 * aleatorios por cada cifrado, empaquetados junto al texto cifrado).
 * Sin dependencias de Android — testeable como JVM puro.
 */
object QrCrypto {

    const val PREFIX = "PROTECTED:"

    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    fun encrypt(content: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(content.toByteArray(Charsets.UTF_8))

        val combined = salt + iv + cipherText
        return Base64.getEncoder().encodeToString(combined)
    }

    /** Devuelve el contenido original, o null si la contraseña es incorrecta o los datos están corruptos. */
    // Contraseña incorrecta o datos corruptos deben verse igual para quien llama:
    // Base64/GCM/BadPadding/IndexOutOfBounds son todos "no se pudo descifrar".
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun decrypt(encoded: String, password: String): String? {
        return try {
            val combined = Base64.getDecoder().decode(encoded)
            if (combined.size < SALT_LENGTH_BYTES + IV_LENGTH_BYTES) return null

            val salt = combined.copyOfRange(0, SALT_LENGTH_BYTES)
            val iv = combined.copyOfRange(SALT_LENGTH_BYTES, SALT_LENGTH_BYTES + IV_LENGTH_BYTES)
            val cipherText = combined.copyOfRange(SALT_LENGTH_BYTES + IV_LENGTH_BYTES, combined.size)
            val key = deriveKey(password, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
