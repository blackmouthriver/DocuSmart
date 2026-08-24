package com.docsmart.features.scanner.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Cubre RF-SEC-13/14 (HU-SEC-09/10, docs/requirements/security.md). */
class QrCryptoTest {

    @Test
    fun `encrypt y decrypt con la misma contrasena recuperan el contenido original`() {
        val original = "https://ejemplo.com/documento-secreto"

        val encrypted = QrCrypto.encrypt(original, "clave123")
        val decrypted = QrCrypto.decrypt(encrypted, "clave123")

        assertEquals(original, decrypted)
    }

    @Test
    fun `decrypt con contrasena incorrecta devuelve null`() {
        val encrypted = QrCrypto.encrypt("contenido secreto", "correcta")

        val decrypted = QrCrypto.decrypt(encrypted, "incorrecta")

        assertNull(decrypted)
    }

    @Test
    fun `decrypt de datos corruptos devuelve null en vez de lanzar excepcion`() {
        val decrypted = QrCrypto.decrypt("esto-no-es-base64-valido!!!", "cualquiera")

        assertNull(decrypted)
    }

    @Test
    fun `cada cifrado produce un resultado distinto aunque el contenido y la clave sean iguales`() {
        val a = QrCrypto.encrypt("mismo contenido", "misma-clave")
        val b = QrCrypto.encrypt("mismo contenido", "misma-clave")

        assertNotEquals(a, b) // salt e IV aleatorios en cada cifrado
    }

    @Test
    fun `funciona con texto largo y caracteres especiales`() {
        val original = "Área de trabajo: día 25/12 — 100% completado ñáéíóú 你好"

        val encrypted = QrCrypto.encrypt(original, "unicode-ok")

        assertEquals(original, QrCrypto.decrypt(encrypted, "unicode-ok"))
    }

    @Test
    fun `el prefijo PROTECTED es el esperado por el flujo de lectura`() {
        assertEquals("PROTECTED:", QrCrypto.PREFIX)
    }
}
