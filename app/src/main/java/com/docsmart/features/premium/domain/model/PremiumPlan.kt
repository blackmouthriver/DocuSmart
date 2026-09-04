package com.docsmart.features.premium.domain.model

import androidx.annotation.StringRes
import com.docsmart.R

data class PremiumPlan(
    val id: String,
    @StringRes val titleRes: Int,
    val price: String,
    @StringRes val periodRes: Int,
    @StringRes val savingsLabelRes: Int? = null,
    val isPopular: Boolean = false,
    val productId: String // ID de Play Store Billing
)

// Funciones premium bloqueadas para usuarios free
enum class PremiumFeature(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val isAvailableFree: Boolean = false
) {
    NO_ADS(
        titleRes = R.string.premium_feature_no_ads_title,
        descRes  = R.string.premium_feature_no_ads_desc
    ),
    PDF_TO_WORD(
        titleRes = R.string.premium_feature_pdf_word_title,
        descRes  = R.string.premium_feature_pdf_word_desc
    ),
    ADVANCED_OCR(
        titleRes = R.string.premium_feature_ocr_title,
        descRes  = R.string.premium_feature_ocr_desc
    ),
    ADVANCED_COMPRESS(
        titleRes = R.string.premium_feature_compress_title,
        descRes  = R.string.premium_feature_compress_desc
    ),
    UNLIMITED_CONVERT(
        titleRes        = R.string.premium_feature_unlimited_title,
        descRes         = R.string.premium_feature_unlimited_desc,
        isAvailableFree = true
    )
}