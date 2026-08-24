package com.docsmart.core.ui

import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLanguage(
    val code: String,
    val label: String,
    val nativeLabel: String
) {
    SPANISH("es", "Español", "Español"),
    ENGLISH("en", "English", "English"),
    PORTUGUESE("pt", "Portugués", "Português"),
    GERMAN("de", "Alemán", "Deutsch"),
    RUSSIAN("ru", "Ruso", "Русский")
}

@Singleton
class LanguageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        "docusmart_language", Context.MODE_PRIVATE
    )

    private val _currentLanguage = MutableStateFlow(loadLanguage())
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    // ── Aplicar idioma guardado al iniciar ────────────
    fun applyLanguage(context: Context): Context {
        val language = _currentLanguage.value
        return updateContextLocale(context, language)
    }

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        prefs.edit().putString("language", language.code).apply()
        Timber.d("LanguageManager: idioma cambiado a ${language.label}")
    }

    /**
     * Idioma del dispositivo si es uno de los soportados, o español si no lo
     * es. Usado por "Restablecer configuración" en Ajustes — antes forzaba
     * español sin importar el idioma configurado del dispositivo, mismo tipo
     * de bug ya corregido en TTS y reconocimiento de voz (Modo Estudio).
     */
    fun deviceDefaultLanguage(): AppLanguage =
        AppLanguage.entries.find { it.code == Locale.getDefault().language } ?: AppLanguage.SPANISH

    private fun updateContextLocale(context: Context, language: AppLanguage): Context {
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun loadLanguage(): AppLanguage {
        val saved = prefs.getString("language", AppLanguage.SPANISH.code)
        return AppLanguage.entries.find { it.code == saved } ?: AppLanguage.SPANISH
    }
}