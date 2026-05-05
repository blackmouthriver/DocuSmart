package com.docsmart.features.premium.domain.model

data class PremiumPlan(
    val id: String,
    val title: String,
    val price: String,
    val period: String,
    val savingsLabel: String? = null,
    val isPopular: Boolean = false,
    val productId: String // ID de Play Store Billing
)

// Funciones premium bloqueadas para usuarios free
enum class PremiumFeature(
    val title: String,
    val description: String,
    val isAvailableFree: Boolean = false
) {
    NO_ADS(
        title = "Sin anuncios",
        description = "Disfruta DocuSmart sin interrupciones"
    ),
    PDF_TO_WORD(
        title = "PDF a Word",
        description = "Convierte PDFs a documentos editables"
    ),
    PDF_TO_EXCEL(
        title = "PDF a Excel",
        description = "Extrae tablas de PDFs a hojas de cálculo"
    ),
    PDF_TO_PPT(
        title = "PDF a PowerPoint",
        description = "Convierte presentaciones fácilmente"
    ),
    ADVANCED_OCR(
        title = "OCR avanzado",
        description = "Reconocimiento de texto en 50+ idiomas"
    ),
    CLOUD_SYNC(
        title = "Nube integrada",
        description = "Sincroniza con Google Drive y Dropbox"
    ),
    ADVANCED_COMPRESS(
        title = "Compresión avanzada",
        description = "Reduce PDFs hasta un 90% sin pérdida visible"
    ),
    UNLIMITED_CONVERT(
        title = "Conversiones ilimitadas",
        description = "Sin límite diario de conversiones",
        isAvailableFree = true
    )
}