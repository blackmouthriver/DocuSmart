package com.docsmart.core.ads

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestiona los límites diarios de operaciones para usuarios free.
 * Persiste contadores en SharedPreferences, se resetean cada día.
 */
@Singleton
class DailyLimitManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME        = "docusmart_daily_limits"
        private const val KEY_DATE          = "current_date"
        private const val KEY_CONVERSIONS   = "count_conversions"
        private const val KEY_MERGE         = "count_merge"
        private const val KEY_SPLIT         = "count_split"
        private const val KEY_COMPRESS      = "count_compress"
        private const val KEY_ROTATE        = "count_rotate"
        private const val KEY_NUMBER_PAGES  = "count_number_pages"
        private const val KEY_WATERMARK     = "count_watermark"
        private const val KEY_REORDER_PAGES = "count_reorder_pages"
        private const val KEY_COMPARE       = "count_compare"
        private const val KEY_REDACT        = "count_redact"
        private const val KEY_CROP          = "count_crop"
        private const val KEY_EDIT_TEXT      = "count_edit_text"
        private const val KEY_EXTRA_CONVERSIONS = "extra_conversions"
        private const val KEY_EXTRA_PDF_TOOLS   = "extra_pdf_tools"

        // ── Límites diarios ───────────────────────────────────────────────────
        const val LIMIT_CONVERSIONS = 5
        const val LIMIT_PDF_TOOLS   = 3
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ── Verificar y resetear si cambió el día ─────────────────────────────────
    private fun checkAndResetIfNewDay() {
        val today     = dateFormat.format(Date())
        val savedDate = prefs.getString(KEY_DATE, "")
        if (savedDate != today) {
            Timber.d("DailyLimitManager: nuevo día — reseteando contadores")
            prefs.edit()
                .putString(KEY_DATE,        today)
                .putInt(KEY_CONVERSIONS,    0)
                .putInt(KEY_MERGE,          0)
                .putInt(KEY_SPLIT,          0)
                .putInt(KEY_COMPRESS,       0)
                .putInt(KEY_ROTATE,         0)
                .putInt(KEY_NUMBER_PAGES,   0)
                .putInt(KEY_WATERMARK,      0)
                .putInt(KEY_REORDER_PAGES,  0)
                .putInt(KEY_COMPARE,        0)
                .putInt(KEY_REDACT,         0)
                .putInt(KEY_CROP,           0)
                .putInt(KEY_EDIT_TEXT,      0)
                .putInt(KEY_EXTRA_CONVERSIONS, 0)
                .putInt(KEY_EXTRA_PDF_TOOLS, 0)
                .apply()
        }
    }

    // ── Verificar si puede realizar la operación ──────────────────────────────
    fun canConvert(): Boolean {
        checkAndResetIfNewDay()
        val count  = prefs.getInt(KEY_CONVERSIONS, 0)
        val extras = prefs.getInt(KEY_EXTRA_CONVERSIONS, 0)
        val canDo  = count < LIMIT_CONVERSIONS + extras
        Timber.d("DailyLimitManager: canConvert=$canDo ($count/${LIMIT_CONVERSIONS + extras})")
        return canDo
    }

    fun canUsePdfTool(toolKey: String): Boolean {
        checkAndResetIfNewDay()
        val key    = getPdfToolKey(toolKey)
        val count  = prefs.getInt(key, 0)
        val extras = prefs.getInt(KEY_EXTRA_PDF_TOOLS, 0)
        val canDo  = count < LIMIT_PDF_TOOLS + extras
        Timber.d("DailyLimitManager: canUsePdfTool[$toolKey]=$canDo ($count/${LIMIT_PDF_TOOLS + extras})")
        return canDo
    }

    // ── Registrar uso ─────────────────────────────────────────────────────────
    fun registerConversion() {
        checkAndResetIfNewDay()
        val current = prefs.getInt(KEY_CONVERSIONS, 0)
        prefs.edit().putInt(KEY_CONVERSIONS, current + 1).apply()
        Timber.d("DailyLimitManager: conversión registrada → ${current + 1}")
    }

    fun registerPdfTool(toolKey: String) {
        checkAndResetIfNewDay()
        val key     = getPdfToolKey(toolKey)
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
        Timber.d("DailyLimitManager: pdfTool[$toolKey] registrado → ${current + 1}")
    }

    // ── Agregar conversión extra (reward por ver anuncio) ─────────────────────
    fun addRewardedConversion() {
        checkAndResetIfNewDay()
        val current = prefs.getInt(KEY_EXTRA_CONVERSIONS, 0)
        prefs.edit().putInt(KEY_EXTRA_CONVERSIONS, current + 1).apply()
        Timber.d("DailyLimitManager: +1 extra por rewarded → ${current + 1} extras")
    }

    // ── Agregar uso extra de herramienta PDF (reward por ver anuncio) ─────────
    fun addRewardedPdfTool() {
        checkAndResetIfNewDay()
        val current = prefs.getInt(KEY_EXTRA_PDF_TOOLS, 0)
        prefs.edit().putInt(KEY_EXTRA_PDF_TOOLS, current + 1).apply()
        Timber.d("DailyLimitManager: +1 extra de herramienta PDF por rewarded → ${current + 1} extras")
    }

    // ── Obtener contadores para mostrar en UI ─────────────────────────────────
    fun getConversionCount(): Int {
        checkAndResetIfNewDay()
        return prefs.getInt(KEY_CONVERSIONS, 0)
    }

    fun getConversionLimit(): Int {
        checkAndResetIfNewDay()
        val extras = prefs.getInt(KEY_EXTRA_CONVERSIONS, 0)
        return LIMIT_CONVERSIONS + extras
    }

    fun getPdfToolCount(toolKey: String): Int {
        checkAndResetIfNewDay()
        return prefs.getInt(getPdfToolKey(toolKey), 0)
    }

    fun getPdfToolLimit(): Int {
        checkAndResetIfNewDay()
        val extras = prefs.getInt(KEY_EXTRA_PDF_TOOLS, 0)
        return LIMIT_PDF_TOOLS + extras
    }

    private fun getPdfToolKey(toolKey: String): String = when (toolKey) {
        "MERGE"         -> KEY_MERGE
        "SPLIT"         -> KEY_SPLIT
        "COMPRESS"      -> KEY_COMPRESS
        "ROTATE"        -> KEY_ROTATE
        "NUMBER_PAGES"  -> KEY_NUMBER_PAGES
        "WATERMARK"     -> KEY_WATERMARK
        "REORDER_PAGES" -> KEY_REORDER_PAGES
        "COMPARE"       -> KEY_COMPARE
        "REDACT"        -> KEY_REDACT
        "CROP"          -> KEY_CROP
        "EDIT_TEXT"     -> KEY_EDIT_TEXT
        else            -> KEY_CONVERSIONS
    }
}