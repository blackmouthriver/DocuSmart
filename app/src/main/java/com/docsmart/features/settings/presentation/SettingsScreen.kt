package com.docsmart.features.settings.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.AppLanguage
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.AppTheme
import com.docsmart.core.ui.theme.PremiumGold
import com.docsmart.core.ui.theme.ThemeManager

@Composable
fun SettingsScreen(
    themeManager   : ThemeManager,
    languageManager: LanguageManager,
    onPremiumClick : () -> Unit = {},
    viewModel      : SettingsViewModel = hiltViewModel()
) {
    val context         = LocalContext.current
    val currentTheme    by themeManager.currentTheme.collectAsState()
    val currentLanguage by languageManager.currentLanguage.collectAsState()
    val isEs            = currentLanguage == AppLanguage.SPANISH

    // ── Estados de diálogos ───────────────────────────────────────────────────
    var showThemeDialog       by remember { mutableStateOf(false) }
    var showLanguageDialog    by remember { mutableStateOf(false) }
    var showStorageDialog     by remember { mutableStateOf(false) }
    var showAboutDialog       by remember { mutableStateOf(false) }
    var showPrivacyDialog     by remember { mutableStateOf(false) }
    var showHelpDialog        by remember { mutableStateOf(false) }
    var showResetDialog       by remember { mutableStateOf(false) }
    var showShareDialog       by remember { mutableStateOf(false) }

    // ── Diálogo: Idioma ───────────────────────────────────────────────────────
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(if (isEs) "Seleccionar idioma" else "Select language",
                style = MaterialTheme.typography.titleLarge) },
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
                                onClick  = {
                                    languageManager.setLanguage(language)
                                    showLanguageDialog = false
                                }
                            )
                            Column {
                                Text(language.nativeLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(language.code.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(if (isEs) "Cerrar" else "Close")
                }
            }
        )
    }

    // ── Diálogo: Tema ─────────────────────────────────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(if (isEs) "Seleccionar tema" else "Select theme",
                style = MaterialTheme.typography.titleLarge) },
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
                                onClick  = {
                                    themeManager.setTheme(theme)
                                    showThemeDialog = false
                                }
                            )
                            Text(
                                text = if (isEs) theme.label else when (theme) {
                                    AppTheme.LIGHT  -> "Light"
                                    AppTheme.DARK   -> "Dark"
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
                    Text(if (isEs) "Cerrar" else "Close")
                }
            }
        )
    }

    // ── Diálogo: Almacenamiento ───────────────────────────────────────────────
    if (showStorageDialog) {
        val convertedDir  = java.io.File(context.filesDir, "converted")
        val pdfToolsDir   = java.io.File(context.filesDir, "pdftools")
        val convertedFiles = convertedDir.listFiles()?.size ?: 0
        val pdfToolsFiles  = pdfToolsDir.listFiles()?.size ?: 0
        val totalFiles     = convertedFiles + pdfToolsFiles
        val convertedSize  = convertedDir.listFiles()?.sumOf { it.length() }?.div(1024) ?: 0
        val pdfToolsSize   = pdfToolsDir.listFiles()?.sumOf { it.length() }?.div(1024) ?: 0
        val totalSize      = convertedSize + pdfToolsSize

        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(if (isEs) "Almacenamiento" else "Storage",
                style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageRow(
                        label  = if (isEs) "Conversiones" else "Conversions",
                        files  = convertedFiles,
                        sizeKb = convertedSize
                    )
                    StorageRow(
                        label  = if (isEs) "Herramientas PDF" else "PDF Tools",
                        files  = pdfToolsFiles,
                        sizeKb = pdfToolsSize
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text  = "Total",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text  = "$totalFiles ${if (isEs) "archivos" else "files"} · $totalSize KB",
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
                    TextButton(onClick = {
                        convertedDir.listFiles()?.forEach { it.delete() }
                        pdfToolsDir.listFiles()?.forEach { it.delete() }
                        showStorageDialog = false
                    }) {
                        Text(
                            text  = if (isEs) "Limpiar caché" else "Clear cache",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }

    // ── Diálogo: Privacidad ───────────────────────────────────────────────────
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(if (isEs) "Privacidad" else "Privacy",
                style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isEs)
                            "DocuSmart respeta tu privacidad. Tus documentos nunca se suben a servidores externos. Todo el procesamiento ocurre localmente en tu dispositivo."
                        else
                            "DocuSmart respects your privacy. Your documents are never uploaded to external servers. All processing happens locally on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    // Opción: permisos del sistema
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPrivacyDialog = false
                                openAppSettings(context)
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text  = if (isEs) "Gestionar permisos del sistema" else "Manage system permissions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(if (isEs) "Entendido" else "Got it")
                }
            }
        )
    }

    // ── Diálogo: Ayuda ────────────────────────────────────────────────────────
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(if (isEs) "Ayuda" else "Help",
                style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HelpItem(
                        question = if (isEs) "¿Cómo convierto imágenes a PDF?"
                        else "How do I convert images to PDF?",
                        answer   = if (isEs) "Ve a Convertir → Imagen → selecciona tus imágenes → toca Convertir."
                        else "Go to Convert → Image → select your images → tap Convert."
                    )
                    HelpItem(
                        question = if (isEs) "¿Dónde se guardan los archivos?"
                        else "Where are files saved?",
                        answer   = if (isEs) "Los archivos generados se guardan en Descargas de tu dispositivo."
                        else "Generated files are saved to your device's Downloads folder."
                    )
                    HelpItem(
                        question = if (isEs) "¿Cómo agrego un favorito?"
                        else "How do I add a favorite?",
                        answer   = if (isEs) "Toca el ícono de corazón en cualquier documento o usa el menú ⋮."
                        else "Tap the heart icon on any document or use the ⋮ menu."
                    )
                    HelpItem(
                        question = if (isEs) "¿Mis documentos están seguros?"
                        else "Are my documents safe?",
                        answer   = if (isEs) "Sí. Todo el procesamiento es local. Ningún archivo sale de tu dispositivo."
                        else "Yes. All processing is local. No file leaves your device."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(if (isEs) "Cerrar" else "Close")
                }
            }
        )
    }

    // ── Diálogo: Restablecer ──────────────────────────────────────────────────
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(
                text  = if (isEs) "Restablecer configuración" else "Reset settings",
                style = MaterialTheme.typography.titleLarge
            )},
            text = { Text(
                text  = if (isEs)
                    "Se restablecerá el tema al modo claro, el idioma al español y se limpiará el caché. Los documentos no se eliminarán. ¿Continuar?"
                else
                    "Theme will be reset to light, language to English, and cache will be cleared. Documents will not be deleted. Continue?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )},
            confirmButton = {
                TextButton(onClick = {
                    themeManager.setTheme(AppTheme.SYSTEM)
                    languageManager.setLanguage(AppLanguage.SPANISH)
                    java.io.File(context.filesDir, "converted").listFiles()?.forEach { it.delete() }
                    java.io.File(context.filesDir, "pdftools").listFiles()?.forEach { it.delete() }
                    showResetDialog = false
                }) {
                    Text(
                        text  = if (isEs) "Restablecer" else "Reset",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(if (isEs) "Cancelar" else "Cancel")
                }
            }
        )
    }

    // ── Diálogo: Acerca de ────────────────────────────────────────────────────
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(
                text  = if (isEs) "Acerca de DocuSmart" else "About DocuSmart",
                style = MaterialTheme.typography.titleLarge
            )},
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text     = "DS",
                                style    = MaterialTheme.typography.titleMedium,
                                color    = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Column {
                            Text("DocuSmart",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text("Versión 1.0.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        text  = "© 2026 mouthblack technology",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(if (isEs) "Cerrar" else "Close")
                }
            }
        )
    }

    // ── UI Principal ──────────────────────────────────────────────────────────
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 24.dp, bottom = 100.dp,
            start = 20.dp, end = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Banner
        item {
            DocuSmartTopBanner(
                screenTitle    = if (isEs) "Ajustes" else "Settings",
                screenSubtitle = if (isEs) "Personaliza tu experiencia"
                else "Customize your experience"
            )
        }

        // AdMob
        item {
            DocuSmartBannerAd(
                adUnitId  = AdConstants.BANNER_SETTINGS_ID,
                adManager = viewModel.adManager,
                modifier  = Modifier.padding(horizontal = 0.dp)
            )
        }

        // Premium card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onPremiumClick() },
                shape    = MaterialTheme.shapes.large,
                colors   = CardDefaults.cardColors(
                    containerColor = PremiumGold.copy(alpha = 0.1f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Star, null,
                        tint     = PremiumGold,
                        modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DocuSmart Premium",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text  = if (isEs) "Desbloquea todas las funciones"
                            else "Unlock all features",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Sección: Personalización ──────────────────────────────────────────
        item {
            SettingsSectionHeader(if (isEs) "Personalización" else "Personalization")
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Language,
                title    = if (isEs) "Idioma" else "Language",
                subtitle = currentLanguage.nativeLabel,
                onClick  = { showLanguageDialog = true }
            )
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.DarkMode,
                title    = if (isEs) "Tema" else "Theme",
                subtitle = if (isEs) currentTheme.label else when (currentTheme) {
                    AppTheme.LIGHT  -> "Light"
                    AppTheme.DARK   -> "Dark"
                    AppTheme.SYSTEM -> "System"
                },
                onClick  = { showThemeDialog = true }
            )
        }

        // ── Sección: Almacenamiento ───────────────────────────────────────────
        item {
            SettingsSectionHeader(if (isEs) "Almacenamiento" else "Storage")
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Storage,
                title    = if (isEs) "Almacenamiento" else "Storage",
                subtitle = if (isEs) "Ver y limpiar archivos generados"
                else "View and clear generated files",
                onClick  = { showStorageDialog = true }
            )
        }

        // ── Sección: Privacidad y seguridad ───────────────────────────────────
        item {
            SettingsSectionHeader(if (isEs) "Privacidad y seguridad" else "Privacy & Security")
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.PrivacyTip,
                title    = if (isEs) "Privacidad" else "Privacy",
                subtitle = if (isEs) "Política de datos y permisos"
                else "Data policy and permissions",
                onClick  = { showPrivacyDialog = true }
            )
        }

        // ── Sección: Compartir ────────────────────────────────────────────────
        item {
            SettingsSectionHeader(if (isEs) "Compartir" else "Share")
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Share,
                title    = if (isEs) "Compartir DocuSmart" else "Share DocuSmart",
                subtitle = if (isEs) "Recomienda la app a tus contactos"
                else "Recommend the app to your contacts",
                onClick  = { shareApp(context) }
            )
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Star,
                title    = if (isEs) "Valorar en Play Store" else "Rate on Play Store",
                subtitle = if (isEs) "¿Te gusta DocuSmart? ¡Déjanos una reseña!"
                else "Like DocuSmart? Leave us a review!",
                onClick  = { openPlayStore(context) }
            )
        }

        // ── Sección: Soporte ──────────────────────────────────────────────────
        item {
            SettingsSectionHeader(if (isEs) "Soporte" else "Support")
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.HelpOutline,
                title    = if (isEs) "Ayuda" else "Help",
                subtitle = if (isEs) "Preguntas frecuentes" else "Frequently asked questions",
                onClick  = { showHelpDialog = true }
            )
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Email,
                title    = if (isEs) "Contactar soporte" else "Contact support",
                subtitle = "soporte@docusmart.app",
                onClick  = { sendSupportEmail(context, isEs) }
            )
        }

        // ── Sección: Sistema ──────────────────────────────────────────────────
        item {
            SettingsSectionHeader(if (isEs) "Sistema" else "System")
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.RestartAlt,
                title    = if (isEs) "Restablecer configuración" else "Reset settings",
                subtitle = if (isEs) "Volver a los valores predeterminados"
                else "Return to default values",
                onClick  = { showResetDialog = true },
                tint     = MaterialTheme.colorScheme.error
            )
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Info,
                title    = if (isEs) "Acerca de" else "About",
                subtitle = "DocuSmart v1.0.0 · mouthblack technology",
                onClick  = { showAboutDialog = true }
            )
        }
    }
}

// ── Componentes internos ──────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    icon    : androidx.compose.ui.graphics.vector.ImageVector,
    title   : String,
    subtitle: String,
    onClick : () -> Unit,
    tint    : androidx.compose.ui.graphics.Color =
        MaterialTheme.colorScheme.primary
) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StorageRow(label: String, files: Int, sizeKb: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text("$files · $sizeKb KB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HelpItem(question: String, answer: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(question,
            style      = MaterialTheme.typography.titleSmall,
            color      = MaterialTheme.colorScheme.onSurface)
        Text(answer,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(
            modifier  = Modifier.padding(top = 8.dp),
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

// ── Funciones de sistema ──────────────────────────────────────────────────────

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun shareApp(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DocuSmart")
            putExtra(
                Intent.EXTRA_TEXT,
                "¡Te recomiendo DocuSmart! El mejor visor y convertidor de documentos.\n" +
                        "https://play.google.com/store/apps/details?id=${context.packageName}"
            )
        }
        context.startActivity(Intent.createChooser(intent, "Compartir DocuSmart"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun openPlayStore(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=${context.packageName}"))
        )
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
        )
    }
}

private fun sendSupportEmail(context: Context, isEs: Boolean) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data    = Uri.parse("mailto:soporte@docusmart.app")
            putExtra(Intent.EXTRA_SUBJECT,
                if (isEs) "Soporte DocuSmart" else "DocuSmart Support")
            putExtra(Intent.EXTRA_TEXT,
                if (isEs) "Hola, necesito ayuda con DocuSmart.\n\nDispositivo: ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}\nApp: 1.0.0\n\nDescripción del problema:\n"
                else "Hello, I need help with DocuSmart.\n\nDevice: ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}\nApp: 1.0.0\n\nProblem description:\n"
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}