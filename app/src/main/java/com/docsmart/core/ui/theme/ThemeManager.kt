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

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        "docusmart_theme", Context.MODE_PRIVATE
    )

    private val _currentTheme = MutableStateFlow(loadTheme())
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
        prefs.edit().putString("theme", theme.name).apply()
        Timber.d("ThemeManager: tema cambiado a ${theme.label}")
    }

    private fun loadTheme(): AppTheme {
        val saved = prefs.getString("theme", AppTheme.SYSTEM.name)
        return AppTheme.entries.find { it.name == saved } ?: AppTheme.SYSTEM
    }
}