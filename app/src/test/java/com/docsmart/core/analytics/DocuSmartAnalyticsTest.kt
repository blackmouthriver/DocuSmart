package com.docsmart.core.analytics

import android.util.Log
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import timber.log.Timber

/**
 * En un test JVM plano Firebase no esta inicializado y `Bundle` es el stub de
 * Android ("not mocked"), asi que `safely {}` siempre entra por su `catch`:
 * eso permite verificar el contrato real -- ningun evento propaga una
 * excepcion al llamador y lo que se registra a nivel WARN (que CrashlyticsTree
 * reenvia a Firebase) no lleva el Throwable adjunto.
 *
 * No se verifica el contenido de cada evento enviado a Firebase: exigiria
 * mockear `Firebase.analytics` y `Bundle` estaticos, y un test asi solo
 * reflejaria la implementacion linea por linea.
 */
class DocuSmartAnalyticsTest {
    private class CapturingTree : Timber.Tree() {
        val logs = mutableListOf<Triple<Int, String, Throwable?>>()

        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            logs += Triple(priority, message, t)
        }
    }

    private inline fun withCapturedLogs(block: () -> Unit): List<Triple<Int, String, Throwable?>> {
        val tree = CapturingTree()
        Timber.plant(tree)
        try {
            block()
        } finally {
            Timber.uproot(tree)
        }
        return tree.logs
    }

    @Test
    fun `ningun evento propaga excepciones al llamador aunque Firebase no este disponible`() {
        assertDoesNotThrow {
            DocuSmartAnalytics.logScreenView("Home")
            DocuSmartAnalytics.logConversion("IMAGE_TO_PDF")
            DocuSmartAnalytics.logConversionSuccess("IMAGE_TO_PDF", 120)
            DocuSmartAnalytics.logConversionError("IMAGE_TO_PDF", "fallo")
            DocuSmartAnalytics.logPdfTool("merge")
            DocuSmartAnalytics.logDocumentOpened("pdf")
            DocuSmartAnalytics.logDocumentFavorited("pdf")
            DocuSmartAnalytics.logScanCompleted(3)
            DocuSmartAnalytics.logQrScanned("url")
            DocuSmartAnalytics.logQrCreated("wifi", hasPassword = true)
            DocuSmartAnalytics.logStudySessionStarted()
            DocuSmartAnalytics.logNoteCreated()
            DocuSmartAnalytics.logPomodoroCompleted(2)
            DocuSmartAnalytics.logPremiumScreenViewed()
            DocuSmartAnalytics.logPremiumPurchaseAttempt("annual")
            DocuSmartAnalytics.logPremiumPurchaseSuccess("com.docsmart.premium.annual")
            DocuSmartAnalytics.logError("ctx", "mensaje")
        }
    }

    // Un fallo de Firebase/Bundle no debe reenviar el Throwable a Crashlytics
    // (CrashlyticsTree reenvia todo >= WARN): solo el tipo de excepcion.
    @Test
    fun `el fallo interno se registra a nivel WARN sin adjuntar el Throwable`() {
        val logs = withCapturedLogs { DocuSmartAnalytics.logScreenView("Home") }

        val warnings = logs.filter { it.first == Log.WARN }
        assertTrue(warnings.isNotEmpty(), "en un test JVM el evento siempre falla y debe registrarse")
        warnings.forEach { (_, message, throwable) ->
            assertNull(throwable, "el Throwable no debe viajar a Crashlytics: $message")
            assertTrue(message.contains("Exception") || message.contains("Error"), "debe incluir el tipo: $message")
        }
    }

    // ── sanitizeAnalyticsText() ───────────────────────────────────────────────

    @Test
    fun `un mensaje sin rutas ni URIs queda intacto`() {
        assertEquals("No se pudo convertir el archivo", sanitizeAnalyticsText("No se pudo convertir el archivo"))
    }

    @Test
    fun `una URI content se enmascara`() {
        val result = sanitizeAnalyticsText("No se pudo abrir content://com.android.providers.media/document/123 ahora")

        assertFalse(result.contains("content://"))
        assertFalse(result.contains("providers"))
        assertTrue(result.contains("<uri>"))
        assertTrue(result.startsWith("No se pudo abrir"))
    }

    @Test
    fun `una URI file se enmascara`() {
        assertEquals("Error en <uri>", sanitizeAnalyticsText("Error en file:///storage/emulated/0/contrato.pdf"))
    }

    @Test
    fun `una ruta absoluta de Android se enmascara`() {
        val result = sanitizeAnalyticsText("FileNotFound: /storage/emulated/0/Download/pasaporte.pdf (No such file)")

        assertFalse(result.contains("pasaporte"))
        assertFalse(result.contains("/storage"))
        assertTrue(result.contains("<path>"))
    }

    // Revisión adversarial ronda 15: el regex anterior cortaba en el primer espacio y el
    // resto del nombre viajaba a Firebase.
    @Test
    fun `una ruta con espacios en el nombre se enmascara completa`() {
        val text = "FileNotFound: /storage/emulated/0/Download/Mi Contrato Juan.pdf: open failed"
        val result = sanitizeAnalyticsText(text)

        assertFalse(result.contains("Contrato"))
        assertFalse(result.contains("Juan"))
        assertTrue(result.contains("<path>"))
    }

    @Test
    fun `una ruta de Windows se enmascara`() {
        val result = sanitizeAnalyticsText("Falla en C:\\Users\\ana\\Documents\\tesis.docx")

        assertFalse(result.contains("tesis"))
        assertFalse(result.contains("ana"))
        assertTrue(result.contains("<path>"))
    }

    @Test
    fun `una ruta de un solo nivel o un texto con barra simple no se toca`() {
        assertEquals("fallo de lectura/escritura", sanitizeAnalyticsText("fallo de lectura/escritura"))
    }

    @Test
    fun `el resultado se recorta al limite de 100 caracteres de Firebase`() {
        val result = sanitizeAnalyticsText("x".repeat(500))

        assertEquals(100, result.length)
    }

    @Test
    fun `el enmascarado ocurre antes del recorte para no dejar un fragmento de ruta`() {
        // La ruta empieza antes del caracter 100 y termina despues: si se recortara
        // primero, quedaria un pedazo de la ruta sin enmascarar.
        val text = "a".repeat(90) + " /data/user/0/com.docsmart/files/mi_secreto_largo.pdf"

        val result = sanitizeAnalyticsText(text)

        assertFalse(result.contains("/data"))
        assertFalse(result.contains("secreto"))
    }

    @Test
    fun `una cadena vacia sigue vacia`() {
        assertEquals("", sanitizeAnalyticsText(""))
    }
}
