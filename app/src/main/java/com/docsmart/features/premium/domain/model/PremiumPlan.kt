package com.docsmart.features.premium.domain.model

import androidx.annotation.StringRes
import com.docsmart.R

// Id del plan mensual: el anual calcula su ahorro contra el precio real de este.
const val MONTHLY_PLAN_ID = "monthly"

data class PremiumPlan(
    val id: String,
    @StringRes val titleRes: Int,
    val price: String,
    @StringRes val periodRes: Int,
    // Porcentaje que se ahorra frente a pagar el plan mensual 12 veces (badge "Ahorra X%"), o null si
    // no hay badge. Se calcula con los precios reales de Play (ver annualSavingsPercent).
    val savingsPercent: Int? = null,
    val isPopular: Boolean = false,
    // ID de Play Store Billing
    val productId: String,
    // HU-54: días de prueba gratuita configurados en Play Console para este
    // plan, o null si no tiene (o si Play Billing todavía no respondió).
    // Viene de BillingManager, nunca hardcodeado -- si se quita el trial en
    // la consola, desaparece solo, sin publicar una actualización.
    val trialDays: Int? = null,
)

// Funciones premium bloqueadas para usuarios free
enum class PremiumFeature(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val isAvailableFree: Boolean = false,
) {
    NO_ADS(
        titleRes = R.string.premium_feature_no_ads_title,
        descRes = R.string.premium_feature_no_ads_desc,
    ),
    PDF_TO_WORD(
        titleRes = R.string.premium_feature_pdf_word_title,
        descRes = R.string.premium_feature_pdf_word_desc,
    ),
    ADVANCED_OCR(
        titleRes = R.string.premium_feature_ocr_title,
        descRes = R.string.premium_feature_ocr_desc,
    ),
    ADVANCED_COMPRESS(
        titleRes = R.string.premium_feature_compress_title,
        descRes = R.string.premium_feature_compress_desc,
    ),

    // Bug real corregido 2026-09-08: estaba marcado `isAvailableFree = true`,
    // así que la pantalla Premium mostraba "Conversiones ilimitadas" como ya
    // desbloqueado para cualquier usuario gratis -- pero el límite diario de
    // 5 conversiones (`DailyLimitManager.LIMIT_CONVERSIONS`) sí se aplica de
    // verdad en `ConverterViewModel`. Le mentía al usuario sobre lo que
    // Premium realmente ofrece.
    UNLIMITED_CONVERT(
        titleRes = R.string.premium_feature_unlimited_title,
        descRes = R.string.premium_feature_unlimited_desc,
    ),
}
