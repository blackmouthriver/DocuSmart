package com.docsmart.features.scanner.presentation

import com.docsmart.features.scanner.domain.QrCrypto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QrContentSafetyTest {
    @Test
    fun `normalizeUriScheme baja solo el esquema a minusculas`() {
        assertEquals("https://Sitio.com/Ruta", normalizeUriScheme("  HTTPS://Sitio.com/Ruta "))
        assertEquals("sin esquema", normalizeUriScheme("sin esquema"))
    }

    @Test
    fun `solo http https mailto y tel son abribles`() {
        assertTrue(isOpenableScheme("HTTPS://a.com"))
        assertTrue(isOpenableScheme("mailto:a@b.com"))
        assertTrue(isOpenableScheme("tel:+57300"))
        assertFalse(isOpenableScheme("intent://x#Intent;end"))
        assertFalse(isOpenableScheme("javascript:alert(1)"))
        assertFalse(isOpenableScheme("file:///sdcard/x"))
        assertFalse(isOpenableScheme("texto sin esquema"))
    }

    @Test
    fun `isRemoteHttpUrl rechaza file content y ftp`() {
        assertTrue(isRemoteHttpUrl(" HTTP://a.com/x.png"))
        assertFalse(isRemoteHttpUrl("file:///x.png"))
        assertFalse(isRemoteHttpUrl("content://a/x"))
        assertFalse(isRemoteHttpUrl("ftp://a/x"))
    }

    @Test
    fun `contentUriAuthority extrae la autoridad sin usuario ni ruta`() {
        assertEquals("com.docsmart.fileprovider", contentUriAuthority("content://com.docsmart.fileprovider/x/y"))
        assertEquals("evil", contentUriAuthority("content://com.docsmart.fileprovider@evil/x"))
        assertNull(contentUriAuthority("https://a.com"))
    }

    @Test
    fun `isOwnContentUri detecta proveedores del propio paquete`() {
        assertTrue(isOwnContentUri("content://com.docsmart.fileprovider/f", "com.docsmart"))
        assertTrue(isOwnContentUri("content://COM.DOCSMART/f", "com.docsmart"))
        assertFalse(isOwnContentUri("content://com.docsmartfake/f", "com.docsmart"))
        assertFalse(isOwnContentUri("content://media/external/1", "com.docsmart"))
        assertFalse(isOwnContentUri("https://a.com", "com.docsmart"))
    }

    @Test
    fun `isProtectedPayload acepta lo generado por encrypt y rechaza texto de terceros`() {
        val protectedValue = QrCrypto.PREFIX + QrCrypto.encrypt("secreto", "clave1234")

        assertTrue(QrCrypto.isProtectedPayload(protectedValue))
        assertFalse(QrCrypto.isProtectedPayload("PROTECTED: no pasar"))
        assertFalse(QrCrypto.isProtectedPayload(QrCrypto.PREFIX + "AAAA"))
        assertFalse(QrCrypto.isProtectedPayload("texto normal"))
    }

    @Test
    fun `decrypt de un payload truncado devuelve null sin lanzar`() {
        assertNull(QrCrypto.decrypt("AAAA", "clave1234"))
    }
}
