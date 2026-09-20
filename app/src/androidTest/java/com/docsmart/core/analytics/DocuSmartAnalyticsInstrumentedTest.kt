package com.docsmart.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Con Bundle y Firebase reales (el build de debug tiene la analítica desactivada por manifiesto, así que
 * ningún evento sale del dispositivo): cada evento se construye y se registra sin propagar excepciones.
 */
class DocuSmartAnalyticsInstrumentedTest {
    @Test
    fun cadaEventoSeRegistraSinLanzarExcepciones() {
        DocuSmartAnalytics.logScreenView("Prueba")
        DocuSmartAnalytics.logConversion("IMAGE_TO_PDF")
        DocuSmartAnalytics.logConversionSuccess("IMAGE_TO_PDF", 120)
        DocuSmartAnalytics.logConversionSuccess("PDF_TO_WORD")
        DocuSmartAnalytics.logConversionError("IMAGE_TO_PDF", "fallo en /data/user/0/com.docsmart/files/x.pdf")
        DocuSmartAnalytics.logPdfTool("merge")
        DocuSmartAnalytics.logDocumentOpened("pdf")
        DocuSmartAnalytics.logDocumentFavorited("pdf")
        DocuSmartAnalytics.logScanCompleted(3)
        DocuSmartAnalytics.logQrScanned("url")
        DocuSmartAnalytics.logQrCreated("wifi", hasPassword = true)
        DocuSmartAnalytics.logQrCreated("texto", hasPassword = false)
        DocuSmartAnalytics.logStudySessionStarted()
        DocuSmartAnalytics.logNoteCreated()
        DocuSmartAnalytics.logPomodoroCompleted(2)
        DocuSmartAnalytics.logPremiumScreenViewed()
        DocuSmartAnalytics.logPremiumPurchaseAttempt("monthly")
        DocuSmartAnalytics.logPremiumPurchaseSuccess("premium_monthly")
        DocuSmartAnalytics.logError("Prueba", "content://media/external/file/1 no se pudo abrir")
        // Llegar hasta aquí sin excepción es el contrato de safely{}.
        assertEquals(100, sanitizeAnalyticsText("x".repeat(250)).length)
    }

    @Test
    fun sanitizeAnalyticsTextEnmascaraUrisYRutas() {
        assertEquals("abrir <uri> fallo", sanitizeAnalyticsText("abrir content://media/external/file/1 fallo"))
        assertEquals("error en <path>", sanitizeAnalyticsText("error en /data/user/0/com.docsmart/files/x.pdf"))
    }
}
