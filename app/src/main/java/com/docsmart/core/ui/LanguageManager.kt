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
    val nativeLabel: String,
    // Pedido explícito del usuario 2026-09-14 (rediseño de Ajustes): bandera
    // por idioma en el selector. Catalán y euskera no tienen bandera propia
    // en el estándar Unicode de emoji regionales (solo existen para países,
    // no regiones/comunidades autónomas) -- se reutiliza la de España, el
    // territorio donde ambos son oficiales.
    val flagEmoji: String,
    // Backlog UX 2026-09-16 (seguimiento #66, rediseño del modal de idioma
    // con tarjetas tipo referencia visual del usuario): nombre del país/
    // región en el propio idioma (mismo criterio que nativeLabel), mostrado
    // como subtítulo de cada tarjeta -- coherente con flagEmoji de arriba,
    // no necesariamente igual a la referencia visual (que mostraba países
    // distintos a los que ya elegía flagEmoji, ej. Brasil para portugués en
    // vez de Portugal).
    val regionLabel: String,
) {
    SPANISH("es", "Español", "Español", "🇪🇸", "España"),
    ENGLISH("en", "English", "English", "🇬🇧", "United Kingdom"),
    PORTUGUESE("pt", "Portugués", "Português", "🇵🇹", "Portugal"),
    GERMAN("de", "Alemán", "Deutsch", "🇩🇪", "Deutschland"),
    RUSSIAN("ru", "Ruso", "Русский", "🇷🇺", "Россия"),
    JAPANESE("ja", "Japonés", "日本語", "🇯🇵", "日本"),
    KOREAN("ko", "Coreano", "한국어", "🇰🇷", "대한민국"),
    CHINESE("zh", "Chino", "中文", "🇨🇳", "中国"),
    ITALIAN("it", "Italiano", "Italiano", "🇮🇹", "Italia"),
    FRENCH("fr", "Francés", "Français", "🇫🇷", "France"),
    CATALAN("ca", "Catalán", "Català", "🇪🇸", "Espanya"),
    BASQUE("eu", "Euskera", "Euskara", "🇪🇸", "Espainia"),
}

@Singleton
class LanguageManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs =
            context.getSharedPreferences(
                "docusmart_language",
                Context.MODE_PRIVATE,
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
         * es. Usado por "Restablecer configuración" en Ajustes y, desde RF-SET-06,
         * también como valor por defecto en una instalación nueva sin idioma
         * guardado todavía (ver loadLanguage()) — antes ambos casos forzaban
         * español sin importar el idioma configurado del dispositivo, mismo tipo
         * de bug ya corregido en TTS y reconocimiento de voz (Modo Estudio).
         */
        fun deviceDefaultLanguage(): AppLanguage =
            AppLanguage.entries.find { it.code == Locale.getDefault().language } ?: AppLanguage.SPANISH

        private fun updateContextLocale(
            context: Context,
            language: AppLanguage,
        ): Context {
            val locale = Locale(language.code)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }

        // RF-SET-06: en una instalación nueva (sin idioma guardado todavía) el
        // idioma por defecto debe ser el del dispositivo, no español fijo — misma
        // señal que ya usa deviceDefaultLanguage() para "Restablecer configuración"
        // (RNF-SET-01). Detección geográfica real de Play Store no es verificable
        // desde el cliente, así que se usa el idioma del dispositivo como estándar
        // de facto.
        private fun loadLanguage(): AppLanguage {
            val saved = prefs.getString("language", null)
            return AppLanguage.entries.find { it.code == saved } ?: deviceDefaultLanguage()
        }
    }
