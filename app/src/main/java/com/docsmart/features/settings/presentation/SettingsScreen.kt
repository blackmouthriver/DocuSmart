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
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.AppTheme
import com.docsmart.core.ui.theme.PremiumGold
import com.docsmart.core.ui.theme.ThemeManager

@Composable
fun SettingsScreen(
    themeManager: ThemeManager,
    onPremiumClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentTheme by themeManager.currentTheme.collectAsState()

    // ── Estados de diálogos ───────────────────────────
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }

    // ── Diálogo de tema ───────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = "Seleccionar tema",
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
                                text = theme.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cerrar")
                }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    // ── Diálogo acerca de ─────────────────────────────
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = "Acerca de DocuSmart",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ── Logo ──────────────────────────
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
                                    horizontal = 12.dp,
                                    vertical = 8.dp
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
                        text = "Visor y convertidor inteligente de documentos. Gestiona, convierte y organiza tus archivos de forma rápida y segura.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "© 2026 DocuSmart. Todos los derechos reservados.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Cerrar")
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

        val convertedSize = convertedDir.listFiles()
            ?.sumOf { it.length() }?.div(1024) ?: 0
        val pdfToolsSize = pdfToolsDir.listFiles()
            ?.sumOf { it.length() }?.div(1024) ?: 0
        val totalSize = convertedSize + pdfToolsSize

        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = {
                Text(
                    text = "Almacenamiento",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageRow(
                        label = "Conversiones",
                        files = convertedFiles,
                        sizeKb = convertedSize
                    )
                    StorageRow(
                        label = "Herramientas PDF",
                        files = pdfToolsFiles,
                        sizeKb = pdfToolsSize
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$totalFiles archivos · $totalSize KB",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Cerrar")
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
                            text = "Limpiar caché",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = 100.dp,
            start = 20.dp,
            end = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DocuSmartTopBanner(
                screenTitle = "Ajustes",
                screenSubtitle = "Personaliza tu experiencia"
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
                            text = "Desbloquea todas las funciones",
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
                title = "Idioma",
                subtitle = "Español",
                onClick = { }
            )
        }

        item {
            SettingsItem(
                icon = Icons.Rounded.DarkMode,
                title = "Tema",
                subtitle = currentTheme.label,
                onClick = { showThemeDialog = true }
            )
        }

        item {
            SettingsItem(
                icon = Icons.Rounded.Storage,
                title = "Almacenamiento",
                subtitle = "Ver archivos generados",
                onClick = { showStorageDialog = true }
            )
        }

        item {
            SettingsItem(
                icon = Icons.Rounded.Info,
                title = "Acerca de",
                subtitle = "DocuSmart v1.0.0",
                onClick = { showAboutDialog = true }
            )
        }
    }
}

@Composable
private fun StorageRow(
    label: String,
    files: Int,
    sizeKb: Long
) {
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
            text = "$files archivos · $sizeKb KB",
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