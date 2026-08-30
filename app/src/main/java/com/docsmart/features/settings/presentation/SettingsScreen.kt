package com.docsmart.features.settings.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.AppLanguage
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.AccentColor
import com.docsmart.core.ui.theme.AppTheme
import com.docsmart.core.ui.theme.PremiumGold
import com.docsmart.core.ui.theme.ThemeManager
import com.docsmart.core.ui.util.findActivity
import com.docsmart.features.onboarding.presentation.resetOnboarding
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import timber.log.Timber

@Composable
fun SettingsScreen(
    themeManager   : ThemeManager,
    languageManager: LanguageManager,
    onPremiumClick   : () -> Unit = {},
    onShowOnboarding : () -> Unit = {},
    viewModel      : SettingsViewModel = hiltViewModel()
) {
    val context         = LocalContext.current
    val currentTheme       by themeManager.currentTheme.collectAsState()
    val currentAccentColor by themeManager.accentColor.collectAsState()
    val currentLanguage    by languageManager.currentLanguage.collectAsState()
    val isPremium          by viewModel.adManager.isPremium.collectAsStateWithLifecycle()

    @Composable
    fun themeLabel(theme: AppTheme): String = when (theme) {
        AppTheme.LIGHT  -> stringResource(R.string.theme_light)
        AppTheme.DARK   -> stringResource(R.string.theme_dark)
        AppTheme.SYSTEM -> stringResource(R.string.theme_system)
    }

    @Composable
    fun accentColorLabel(accent: AccentColor): String = when (accent) {
        AccentColor.BLUE   -> stringResource(R.string.accent_color_blue)
        AccentColor.PURPLE -> stringResource(R.string.accent_color_purple)
        AccentColor.GREEN  -> stringResource(R.string.accent_color_green)
        AccentColor.ORANGE -> stringResource(R.string.accent_color_orange)
        AccentColor.PINK   -> stringResource(R.string.accent_color_pink)
        AccentColor.TEAL   -> stringResource(R.string.accent_color_teal)
    }

    var showThemeDialog       by remember { mutableStateOf(false) }
    var showAccentColorDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStorageDialog  by remember { mutableStateOf(false) }
    var showAboutDialog    by remember { mutableStateOf(false) }
    var showPrivacyDialog  by remember { mutableStateOf(false) }
    var showHelpDialog     by remember { mutableStateOf(false) }
    var showResetDialog    by remember { mutableStateOf(false) }
    var showShareDialog    by remember { mutableStateOf(false) }

    // ── UMP: solo mostrar la entrada de consentimiento de anuncios si Google
    // determinó que hace falta un punto de acceso (usuarios en UE/Reino
    // Unido) -- exigido por la política de UMP, no basta con mostrar el
    // formulario una sola vez al abrir la app.
    var showAdsPrivacyOption by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showAdsPrivacyOption = UserMessagingPlatform.getConsentInformation(context)
            .privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    // ── Diálogo: Idioma ───────────────────────────────────────────────────────
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_select_language),
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
                            verticalAlignment     = Alignment.CenterVertically
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
                    Text(stringResource(R.string.settings_close))
                }
            }
        )
    }

    // ── Diálogo: Tema ─────────────────────────────────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_select_theme),
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
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == theme,
                                onClick  = {
                                    themeManager.setTheme(theme)
                                    showThemeDialog = false
                                }
                            )
                            Text(
                                text  = themeLabel(theme),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            }
        )
    }

    // ── Diálogo: Color de acento (RF-SET-07) ─────────────────────────────────
    if (showAccentColorDialog) {
        AlertDialog(
            onDismissRequest = { showAccentColorDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_select_accent_color),
                style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    AccentColor.entries.forEach { accent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeManager.setAccentColor(accent)
                                    showAccentColorDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentAccentColor == accent,
                                onClick  = {
                                    themeManager.setAccentColor(accent)
                                    showAccentColorDialog = false
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(accent.swatch, shape = CircleShape)
                            )
                            Text(
                                text  = accentColorLabel(accent),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccentColorDialog = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            }
        )
    }

    // ── Diálogo: Almacenamiento ───────────────────────────────────────────────
    if (showStorageDialog) {
        val convertedDir   = java.io.File(context.filesDir, "converted")
        val pdfToolsDir    = java.io.File(context.filesDir, "pdftools")
        val convertedFiles = convertedDir.listFiles()?.size ?: 0
        val pdfToolsFiles  = pdfToolsDir.listFiles()?.size ?: 0
        val totalFiles     = convertedFiles + pdfToolsFiles
        val convertedSize  = convertedDir.listFiles()?.sumOf { it.length() }?.div(1024) ?: 0
        val pdfToolsSize   = pdfToolsDir.listFiles()?.sumOf { it.length() }?.div(1024) ?: 0
        val totalSize      = convertedSize + pdfToolsSize

        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_storage),
                style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StorageRow(
                        label  = stringResource(R.string.settings_storage_conversions),
                        files  = convertedFiles,
                        sizeKb = convertedSize
                    )
                    StorageRow(
                        label  = stringResource(R.string.pdf_tools_title),
                        files  = pdfToolsFiles,
                        sizeKb = pdfToolsSize
                    )
                    HorizontalDivider()
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.settings_storage_total),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("$totalFiles ${stringResource(R.string.settings_storage_files_unit)} · $totalSize KB",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text(stringResource(R.string.settings_close))
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
                            text  = stringResource(R.string.settings_storage_clear_cache),
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
            title = { Text(stringResource(R.string.settings_privacy_item),
                style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text  = stringResource(R.string.settings_privacy_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
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
                        Icon(Icons.Rounded.AdminPanelSettings, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Text(
                            text  = stringResource(R.string.settings_privacy_manage_permissions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(stringResource(R.string.settings_got_it))
                }
            }
        )
    }

    // ── Diálogo: Ayuda ────────────────────────────────────────────────────────
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_help),
                style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HelpItem(
                        question = stringResource(R.string.settings_help_q1),
                        answer   = stringResource(R.string.settings_help_a1)
                    )
                    HelpItem(
                        question = stringResource(R.string.settings_help_q2),
                        answer   = stringResource(R.string.settings_help_a2)
                    )
                    HelpItem(
                        question = stringResource(R.string.settings_help_q3),
                        answer   = stringResource(R.string.settings_help_a3)
                    )
                    HelpItem(
                        question = stringResource(R.string.settings_help_q4),
                        answer   = stringResource(R.string.settings_help_a4)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(R.string.settings_close))
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
                text  = stringResource(R.string.settings_reset),
                style = MaterialTheme.typography.titleLarge
            )},
            text = { Text(
                text  = stringResource(R.string.settings_reset_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )},
            confirmButton = {
                TextButton(onClick = {
                    themeManager.setTheme(AppTheme.SYSTEM)
                    themeManager.setAccentColor(AccentColor.BLUE)
                    languageManager.setLanguage(languageManager.deviceDefaultLanguage())
                    java.io.File(context.filesDir, "converted").listFiles()?.forEach { it.delete() }
                    java.io.File(context.filesDir, "pdftools").listFiles()?.forEach { it.delete() }
                    showResetDialog = false
                }) {
                    Text(
                        text  = stringResource(R.string.settings_reset_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.general_cancel))
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
                text  = stringResource(R.string.settings_about_dialog_title),
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
                            Text(stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.settings_about_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    Text(
                        text  = stringResource(R.string.settings_about_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text  = stringResource(R.string.settings_copyright),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.settings_close))
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
        // ── AdMob — solo para usuarios free ──────────────────────────────────
        if (!isPremium) {
            item {
                DocuSmartBannerAd(
                    adUnitId  = AdConstants.BANNER_SETTINGS_ID,
                    adManager = viewModel.adManager,
                    modifier  = Modifier.padding(horizontal = 0.dp)
                )
            }
        }

        // ── Banner azul ───────────────────────────────────────────────────────
        item {
            DocuSmartTopBanner(
                screenTitle    = stringResource(R.string.settings_title),
                screenSubtitle = stringResource(R.string.settings_subtitle)
            )
        }

        // ── Premium card ──────────────────────────────────────────────────────
        item {
            Card(
                modifier  = Modifier.fillMaxWidth().clickable { onPremiumClick() },
                shape     = MaterialTheme.shapes.large,
                colors    = CardDefaults.cardColors(
                    containerColor = PremiumGold.copy(alpha = 0.1f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Star, null,
                        tint     = PremiumGold,
                        modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_premium),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text  = stringResource(R.string.settings_premium_subtitle),
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
        item { SettingsSectionHeader(stringResource(R.string.settings_section_personalization)) }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Language,
                title    = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_subtitle),
                onClick  = { showLanguageDialog = true }
            )
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Lightbulb,
                title    = stringResource(R.string.settings_tutorial),
                subtitle = stringResource(R.string.settings_tutorial_subtitle),
                onClick  = {
                    resetOnboarding(context)
                    onShowOnboarding()
                }
            )
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.DarkMode,
                title    = stringResource(R.string.settings_theme),
                subtitle = themeLabel(currentTheme),
                onClick  = { showThemeDialog = true }
            )
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Palette,
                title    = stringResource(R.string.settings_accent_color),
                subtitle = accentColorLabel(currentAccentColor),
                onClick  = { showAccentColorDialog = true }
            )
        }

        // ── Sección: Almacenamiento ───────────────────────────────────────────
        item { SettingsSectionHeader(stringResource(R.string.settings_storage)) }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Storage,
                title    = stringResource(R.string.settings_storage),
                subtitle = stringResource(R.string.settings_storage_subtitle),
                onClick  = { showStorageDialog = true }
            )
        }

        // ── Sección: Privacidad ───────────────────────────────────────────────
        item { SettingsSectionHeader(stringResource(R.string.settings_section_privacy)) }
        item {
            SettingsItem(
                icon     = Icons.Rounded.PrivacyTip,
                title    = stringResource(R.string.settings_privacy_item),
                subtitle = stringResource(R.string.settings_privacy_item_subtitle),
                onClick  = { showPrivacyDialog = true }
            )
        }
        if (showAdsPrivacyOption) {
            item {
                SettingsItem(
                    icon     = Icons.Rounded.Campaign,
                    title    = stringResource(R.string.settings_ads_privacy_options),
                    subtitle = stringResource(R.string.settings_ads_privacy_options_subtitle),
                    onClick  = {
                        context.findActivity()?.let { activity ->
                            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                                if (formError != null) {
                                    Timber.w("UMP: error mostrando opciones de privacidad — ${formError.message}")
                                }
                            }
                        }
                    }
                )
            }
        }

        // ── Sección: Compartir ────────────────────────────────────────────────
        item { SettingsSectionHeader(stringResource(R.string.general_share)) }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Share,
                title    = stringResource(R.string.settings_share_app),
                subtitle = stringResource(R.string.settings_share_app_subtitle),
                onClick  = { shareApp(context) }
            )
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Star,
                title    = stringResource(R.string.settings_rate),
                subtitle = stringResource(R.string.settings_rate_subtitle),
                onClick  = { openPlayStore(context) }
            )
        }

        // ── Sección: Soporte ──────────────────────────────────────────────────
        item { SettingsSectionHeader(stringResource(R.string.settings_section_support)) }
        item {
            SettingsItem(
                icon     = Icons.Rounded.HelpOutline,
                title    = stringResource(R.string.settings_help),
                subtitle = stringResource(R.string.settings_help_subtitle),
                onClick  = { showHelpDialog = true }
            )
        }

        // ── Sección: Sistema ──────────────────────────────────────────────────
        item { SettingsSectionHeader(stringResource(R.string.settings_section_system)) }
        item {
            SettingsItem(
                icon     = Icons.Rounded.RestartAlt,
                title    = stringResource(R.string.settings_reset),
                subtitle = stringResource(R.string.settings_reset_subtitle),
                onClick  = { showResetDialog = true },
                tint     = MaterialTheme.colorScheme.error
            )
        }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Info,
                title    = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_about_subtitle_full),
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
    tint    : androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
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
        modifier              = Modifier.fillMaxWidth(),
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
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
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
    } catch (e: Exception) { e.printStackTrace() }
}

private fun shareApp(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DocuSmart")
            putExtra(Intent.EXTRA_TEXT,
                "¡Te recomiendo DocuSmart! El mejor visor y convertidor de documentos.\n" +
                        "https://play.google.com/store/apps/details?id=${context.packageName}")
        }
        context.startActivity(Intent.createChooser(intent, "Compartir DocuSmart"))
    } catch (e: Exception) { e.printStackTrace() }
}

private fun openPlayStore(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${context.packageName}")))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
    }
}

private fun sendSupportEmail(context: Context, isEs: Boolean) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:soporte@docusmart.app")
            putExtra(Intent.EXTRA_SUBJECT,
                if (isEs) "Soporte DocuSmart" else "DocuSmart Support")
            putExtra(Intent.EXTRA_TEXT,
                if (isEs) "Hola, necesito ayuda con DocuSmart.\n\nDispositivo: ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}\nApp: 1.0.0\n\nDescripción del problema:\n"
                else "Hello, I need help with DocuSmart.\n\nDevice: ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}\nApp: 1.0.0\n\nProblem description:\n")
        }
        context.startActivity(intent)
    } catch (e: Exception) { e.printStackTrace() }
}