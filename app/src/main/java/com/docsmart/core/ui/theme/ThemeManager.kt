package com.docsmart.core.ui.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class AppTheme(val label: String) {
    LIGHT("Claro"),
    DARK("Oscuro"),
    SYSTEM("Sistema")
}

// HU-UX-05 (backlog UX 2026-08-30): tamaño de letra ajustable -- multiplicador
// sobre la tipografía existente (ver `Type.kt#scaledBy`), no una escala nueva
// desde cero. Rango acotado (1.0-1.3) para no romper layouts en cascada.
enum class FontScale(val label: String, val scale: Float) {
    NORMAL("Normal", 1.0f),
    LARGE("Grande", 1.15f),
    EXTRA_LARGE("Muy grande", 1.3f)
}

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        "docusmart_theme", Context.MODE_PRIVATE
    )

    private val _currentTheme = MutableStateFlow(loadTheme())
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    // RF-SET-07: color de acento, independiente de claro/oscuro/sistema.
    private val _accentColor = MutableStateFlow(loadAccentColor())
    val accentColor: StateFlow<AccentColor> = _accentColor.asStateFlow()

    // HU-UX-05: tamaño de letra, independiente de tema/acento.
    private val _fontScale = MutableStateFlow(loadFontScale())
    val fontScale: StateFlow<FontScale> = _fontScale.asStateFlow()

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
        prefs.edit().putString("theme", theme.name).apply()
        Timber.d("ThemeManager: tema cambiado a ${theme.label}")
    }

    fun setAccentColor(accent: AccentColor) {
        _accentColor.value = accent
        prefs.edit().putString("accent_color", accent.name).apply()
        Timber.d("ThemeManager: color de acento cambiado a ${accent.label}")
    }

    fun setFontScale(scale: FontScale) {
        _fontScale.value = scale
        prefs.edit().putString("font_scale", scale.name).apply()
        Timber.d("ThemeManager: tamaño de letra cambiado a ${scale.label}")
    }

    private fun loadTheme(): AppTheme {
        val saved = prefs.getString("theme", AppTheme.SYSTEM.name)
        return AppTheme.entries.find { it.name == saved } ?: AppTheme.SYSTEM
    }

    private fun loadAccentColor(): AccentColor {
        val saved = prefs.getString("accent_color", AccentColor.BLUE.name)
        return AccentColor.entries.find { it.name == saved } ?: AccentColor.BLUE
    }

    private fun loadFontScale(): FontScale {
        val saved = prefs.getString("font_scale", FontScale.NORMAL.name)
        return FontScale.entries.find { it.name == saved } ?: FontScale.NORMAL
    }
}