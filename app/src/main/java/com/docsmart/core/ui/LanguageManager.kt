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

/**
 * Código de idioma a aplicar: el guardado si es soportado; si no (instalación
 * nueva o valor corrupto), el del dispositivo si es soportado; si no, español.
 * Compartido con MainActivity.attachBaseContext() para que la Activity y el
 * selector de Ajustes nunca muestren idiomas distintos.
 */
internal fun resolveLanguageCode(
    saved: String?,
    deviceLanguage: String?,
): String =
    AppLanguage.entries.firstOrNull { it.code == saved }?.code
        ?: AppLanguage.entries.firstOrNull { it.code == deviceLanguage }?.code
        ?: AppLanguage.SPANISH.code

/**
 * Código de idioma de la app: el guardado si es soportado; si no, el del dispositivo (tomado de
 * [deviceConfig]) si es soportado; si no, español. Misma regla que usa `MainActivity` para su
 * propio contexto, para que la Activity y la Application nunca muestren idiomas distintos.
 */
internal fun savedOrDeviceLanguageCode(
    context: Context,
    deviceConfig: Configuration,
): String {
    val prefs = context.getSharedPreferences("docusmart_language", Context.MODE_PRIVATE)
    return resolveLanguageCode(
        saved = prefs.getString("language", null),
        deviceLanguage = deviceConfig.locales[0]?.language,
    )
}

/**
 * Hace que los recursos de la `Application` usen [languageCode].
 *
 * El idioma elegido dentro de la app solo se aplicaba al contexto de la Activity
 * (`MainActivity.attachBaseContext()`): todo lo que se inyecta con `@ApplicationContext`
 * -- casos de uso del Convertidor, ViewModels de Inicio/Biblioteca/Papelera, receptores de
 * notificaciones -- leía los recursos de la Application, que seguían en el idioma del
 * TELÉFONO. Resultado: mensajes de error (y textos escritos dentro de archivos generados, como
 * "Página N") en un idioma distinto al elegido (mismo defecto que H8 en ScanSessionManager y
 * que el de ViewerViewModel). Se corrige en el origen en vez de parchear cada clase.
 *
 * `updateConfiguration` está deprecado pero sigue siendo la forma de cambiar el idioma de los
 * recursos de la Application sin migrar a idiomas por app (AppCompatDelegate), un cambio mayor.
 * El sistema vuelve a escribir su configuración en la Application en cada cambio (rotación,
 * modo oscuro, idioma del sistema): por eso también se llama desde `onConfigurationChanged`.
 */
internal fun applyLanguageToApplicationResources(
    appContext: Context,
    languageCode: String,
) {
    val resources = appContext.resources
    val config = Configuration(resources.configuration)
    config.setLocale(Locale(languageCode))
    @Suppress("DEPRECATION")
    resources.updateConfiguration(config, resources.displayMetrics)
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
            // Sin esto los textos generados con @ApplicationContext seguirían en el idioma anterior
            // hasta reiniciar el proceso.
            applyLanguageToApplicationResources(context, language.code)
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
