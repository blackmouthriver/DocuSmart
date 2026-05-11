package com.docsmart.features.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.AppLanguage
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.AppTheme
import com.docsmart.core.ui.theme.PremiumGold
import com.docsmart.core.ui.theme.ThemeManager

@Composable
fun SettingsScreen(
    themeManager: ThemeManager,
    languageManager: LanguageManager,
    onPremiumClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentTheme by themeManager.currentTheme.collectAsState()
    val currentLanguage by languageManager.currentLanguage.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // ── Diálogo de idioma ─────────────────────────────
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = if (currentLanguage == AppLanguage.SPANISH)
                        "Seleccionar idioma"
                    else "Select language",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    AppLanguage.entries.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    languageManager.setLanguage(language)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLanguage == language,
                                onClick = {
                                    languageManager.setLanguage(language)
                                    showLanguageDialog = false
                                }
                            )
                            Column {
                                Text(
                                    text = language.nativeLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = language.code.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(if (currentLanguage == AppLanguage.SPANISH) "Cerrar" else "Close")
                }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    // ── Diálogo de tema ───────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = if (currentLanguage == AppLanguage.SPANISH)
                        "Seleccionar tema" else "Select theme",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeManager.setTheme(theme)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == theme,
                                onClick = {
                                    themeManager.setTheme(theme)
                                    showThemeDialog = false
                                }
                            )
                            Text(
                                text = if (currentLanguage == AppLanguage.SPANISH)
                                    theme.label
                                else when (theme) {
                                    AppTheme.LIGHT -> "Light"
                                    AppTheme.DARK -> "Dark"
                                    AppTheme.SYSTEM -> "System"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(if (currentLanguage == AppLanguage.SPANISH) "Cerrar" else "Close")
                }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    // ── Diálogo almacenamiento ────────────────────────
    if (showStorageDialog) {
        val filesDir = context.filesDir
        val convertedDir = java.io.File(filesDir, "converted")
        val pdfToolsDir = java.io.File(filesDir, "pdftools")
        val convertedFiles = convertedDir.listFiles()?.size ?: 0
        val pdfToolsFiles = pdfToolsDir.listFiles()?.size ?: 0
        val totalFiles = convertedFiles + pdfToolsFiles
        val convertedSize = convertedDir.listFiles()?.sumOf { it.length() }?.div(1024) ?: 0
        val pdfToolsSize = pdfToolsDir.listFiles()?.sumOf { it.length() }?.div(1024) ?: 0
        val totalSize = convertedSize + pdfToolsSize
        val isEs = currentLanguage == AppLanguage.SPANISH

        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = {
                Text(
                    text = if (isEs) "Almacenamiento" else "Storage",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageRow(
                        label = if (isEs) "Conversiones" else "Conversions",
                        files = convertedFiles,
                        sizeKb = convertedSize
                    )
                    StorageRow(
                        label = if (isEs) "Herramientas PDF" else "PDF Tools",
                        files = pdfToolsFiles,
                        sizeKb = pdfToolsSize
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isEs) "Total" else "Total",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$totalFiles ${if (isEs) "archivos" else "files"} · $totalSize KB",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text(if (isEs) "Cerrar" else "Close")
                }
            },
            dismissButton = {
                if (totalFiles > 0) {
                    TextButton(
                        onClick = {
                            convertedDir.listFiles()?.forEach { it.delete() }
                            pdfToolsDir.listFiles()?.forEach { it.delete() }
                            showStorageDialog = false
                        }
                    ) {
                        Text(
                            text = if (isEs) "Limpiar caché" else "Clear cache",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    // ── Diálogo acerca de ─────────────────────────────
    if (showAboutDialog) {
        val isEs = currentLanguage == AppLanguage.SPANISH
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = if (isEs) "Acerca de DocuSmart" else "About DocuSmart",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "DS",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp, vertical = 8.dp
                                )
                            )
                        }
                        Column {
                            Text(
                                text = "DocuSmart",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Versión 1.0.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        text = if (isEs)
                            "Visor y convertidor inteligente de documentos. Gestiona, convierte y organiza tus archivos de forma rápida y segura."
                        else
                            "Smart document viewer and converter. Manage, convert and organize your files quickly and securely.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "© 2026 DocuSmart.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(if (isEs) "Cerrar" else "Close")
                }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    val isEs = currentLanguage == AppLanguage.SPANISH

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 24.dp, bottom = 100.dp,
            start = 20.dp, end = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DocuSmartTopBanner(
                screenTitle = if (isEs) "Ajustes" else "Settings",
                screenSubtitle = if (isEs) "Personaliza tu experiencia"
                else "Customize your experience"
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPremiumClick() },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = PremiumGold.copy(alpha = 0.1f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = PremiumGold,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DocuSmart Premium",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEs) "Desbloquea todas las funciones"
                            else "Unlock all features",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SettingsItem(
                icon = Icons.Rounded.Language,
                title = if (isEs) "Idioma" else "Language",
                subtitle = currentLanguage.nativeLabel,
                onClick = { showLanguageDialog = true }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.DarkMode,
                title = if (isEs) "Tema" else "Theme",
                subtitle = if (isEs) currentTheme.label else when (currentTheme) {
                    AppTheme.LIGHT -> "Light"
                    AppTheme.DARK -> "Dark"
                    AppTheme.SYSTEM -> "System"
                },
                onClick = { showThemeDialog = true }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.Storage,
                title = if (isEs) "Almacenamiento" else "Storage",
                subtitle = if (isEs) "Ver archivos generados" else "View generated files",
                onClick = { showStorageDialog = true }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Rounded.Info,
                title = if (isEs) "Acerca de" else "About",
                subtitle = "DocuSmart v1.0.0",
                onClick = { showAboutDialog = true }
            )
        }
    }
}

@Composable
private fun StorageRow(label: String, files: Int, sizeKb: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$files · $sizeKb KB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}