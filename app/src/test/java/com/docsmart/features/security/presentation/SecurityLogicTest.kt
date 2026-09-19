package com.docsmart.features.security.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityLogicTest {
    // ── PIN de desbloqueo ───────────────────────────────────────────────────

    @Test
    fun `unlockPinAppendDigit acumula digitos sin enviar hasta completar`() {
        val step = unlockPinAppendDigit("12", "3")

        assertEquals("123", step.pin)
        assertNull(step.submitted)
    }

    @Test
    fun `unlockPinAppendDigit al completar envia el PIN y vacia el campo`() {
        val step = unlockPinAppendDigit("123", "4")

        assertEquals("", step.pin)
        assertEquals("1234", step.submitted)
    }

    @Test
    fun `unlockPinAppendDigit ignora digitos si el PIN ya esta completo`() {
        val step = unlockPinAppendDigit("1234", "5")

        assertEquals("1234", step.pin)
        assertNull(step.submitted)
    }

    @Test
    fun `pinDeleteLast borra el ultimo digito y tolera el campo vacio`() {
        assertEquals("12", pinDeleteLast("123"))
        assertEquals("", pinDeleteLast(""))
    }

    // ── Asistente de crear PIN ──────────────────────────────────────────────

    private fun typeAll(
        initial: SetupPinState,
        digits: String,
    ): SetupPinStep {
        var step = SetupPinStep(initial, SetupPinEvent.NONE)
        digits.forEach { step = setupPinAppendDigit(step.state, it.toString()) }
        return step
    }

    @Test
    fun `setupPin al completar el primer PIN pasa a confirmar`() {
        val step = typeAll(SetupPinState(), "1234")

        assertEquals(SetupPinEvent.ADVANCED_TO_CONFIRM, step.event)
        assertEquals(SetupPinState(pin = "1234", isConfirming = true), step.state)
    }

    @Test
    fun `setupPin con menos de cuatro digitos no avanza`() {
        val step = typeAll(SetupPinState(), "123")

        assertEquals(SetupPinEvent.NONE, step.event)
        assertFalse(step.state.isConfirming)
        assertEquals("123", step.state.pin)
    }

    @Test
    fun `setupPin ignora un quinto digito en la fase de crear`() {
        val step = setupPinAppendDigit(SetupPinState(pin = "1234"), "5")

        assertEquals(SetupPinEvent.NONE, step.event)
        assertEquals("1234", step.state.pin)
    }

    @Test
    fun `setupPin con confirmacion igual completa el asistente`() {
        val step = typeAll(SetupPinState(pin = "1234", isConfirming = true), "1234")

        assertEquals(SetupPinEvent.COMPLETED, step.event)
        assertEquals("1234", step.state.pin)
    }

    @Test
    fun `setupPin con confirmacion distinta avisa el desacuerdo y limpia solo la confirmacion`() {
        val step = typeAll(SetupPinState(pin = "1234", isConfirming = true), "1235")

        assertEquals(SetupPinEvent.MISMATCH, step.event)
        assertEquals(SetupPinState(pin = "1234", confirmPin = "", isConfirming = true), step.state)
    }

    @Test
    fun `setupPin ignora digitos extra con la confirmacion ya completa`() {
        val full = SetupPinState(pin = "1234", confirmPin = "1234", isConfirming = true)

        val step = setupPinAppendDigit(full, "9")

        assertEquals(SetupPinEvent.NONE, step.event)
        assertEquals(full, step.state)
    }

    @Test
    fun `setupPinDeleteLast borra del campo de la fase activa`() {
        assertEquals(SetupPinState(pin = "12"), setupPinDeleteLast(SetupPinState(pin = "123")))
        assertEquals(
            SetupPinState(pin = "1234", confirmPin = "1", isConfirming = true),
            setupPinDeleteLast(SetupPinState(pin = "1234", confirmPin = "12", isConfirming = true)),
        )
    }

    // ── Bloqueo por intentos ────────────────────────────────────────────────

    @Test
    fun `lockoutRemainingSeconds redondea hacia arriba`() {
        assertEquals(1, lockoutRemainingSeconds(1L))
        assertEquals(1, lockoutRemainingSeconds(1_000L))
        assertEquals(2, lockoutRemainingSeconds(1_001L))
        assertEquals(30, lockoutRemainingSeconds(29_001L))
    }

    // ── Archivo pendiente ───────────────────────────────────────────────────

    @Test
    fun `pendingImportRouteFor un file con ruta se mueve como archivo local`() {
        assertEquals(PendingImportRoute.LocalFile("/data/a.pdf"), pendingImportRouteFor("file", "/data/a.pdf"))
    }

    @Test
    fun `pendingImportRouteFor un file sin ruta se ignora`() {
        assertEquals(PendingImportRoute.Ignore, pendingImportRouteFor("file", null))
    }

    @Test
    fun `pendingImportRouteFor cualquier otro esquema usa el flujo de Uri`() {
        assertEquals(PendingImportRoute.ContentUri, pendingImportRouteFor("content", "/x"))
        assertEquals(PendingImportRoute.ContentUri, pendingImportRouteFor(null, null))
    }

    @Test
    fun `isContentDocumentId distingue content de ruta local`() {
        assertTrue(isContentDocumentId("content://media/external/1"))
        assertFalse(isContentDocumentId("/data/user/0/app/files/a.pdf"))
    }

    // ── Formularios de contraseña PDF ───────────────────────────────────────

    @Test
    fun `canProtectPdf exige archivo, contrasenas iguales y no vacias, y sin proceso en curso`() {
        assertTrue(canProtectPdf(true, "abc", "abc", false))
        assertFalse(canProtectPdf(false, "abc", "abc", false))
        assertFalse(canProtectPdf(true, "abc", "abd", false))
        assertFalse(canProtectPdf(true, "", "", false))
        assertFalse(canProtectPdf(true, "   ", "   ", false))
        assertFalse(canProtectPdf(true, "abc", "abc", true))
    }

    @Test
    fun `canRemovePdfPassword exige archivo y contrasena no vacia, y sin proceso en curso`() {
        assertTrue(canRemovePdfPassword(true, "abc", false))
        assertFalse(canRemovePdfPassword(false, "abc", false))
        assertFalse(canRemovePdfPassword(true, " ", false))
        assertFalse(canRemovePdfPassword(true, "abc", true))
    }

    @Test
    fun `passwordsMismatch solo avisa cuando ya se escribio la confirmacion`() {
        assertFalse(passwordsMismatch("abc", ""))
        assertFalse(passwordsMismatch("abc", "abc"))
        assertTrue(passwordsMismatch("abc", "ab"))
    }

    // ── Tamaño de archivo ───────────────────────────────────────────────────

    @Test
    fun `secureFileSizeOf usa bytes por debajo de 1 KB`() {
        assertEquals(SecureFileSize.Bytes(0L), secureFileSizeOf(0L))
        assertEquals(SecureFileSize.Bytes(1023L), secureFileSizeOf(1023L))
    }

    @Test
    fun `secureFileSizeOf usa KB entre 1 KB y 1 MB`() {
        assertEquals(SecureFileSize.Kilobytes(1L), secureFileSizeOf(1024L))
        assertEquals(SecureFileSize.Kilobytes(1023L), secureFileSizeOf(1024L * 1024 - 1))
    }

    @Test
    fun `secureFileSizeOf usa MB desde 1 MB`() {
        assertEquals(SecureFileSize.Megabytes(1.0), secureFileSizeOf(1024L * 1024))
        assertEquals(SecureFileSize.Megabytes(2.5), secureFileSizeOf((2.5 * 1024 * 1024).toLong()))
    }
}
