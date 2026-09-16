package com.docsmart.features.settings.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import kotlinx.coroutines.delay
import com.docsmart.core.ads.AdConstants
import com.docsmart.core.ads.DocuSmartBannerAd
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.AppLanguage
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.AccentColor
import com.docsmart.core.ui.theme.AppTheme
import com.docsmart.core.ui.theme.FontScale
import com.docsmart.core.ui.theme.PremiumGold
import com.docsmart.core.ui.theme.ThemeManager
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
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
    val currentFontScale   by themeManager.fontScale.collectAsState()
    val animatedBackgroundEnabled by themeManager.animatedBackgroundEnabled.collectAsState()
    val soundEffectsEnabled by viewModel.soundEffectPlayer.enabled.collectAsState()
    val currentLanguage    by languageManager.currentLanguage.collectAsState()
    val isPremium          by viewModel.adManager.isPremium.collectAsStateWithLifecycle()
    // HU-54, AC1: revisando Ajustes, sin entrar a PremiumScreen, ya se ve
    // que Premium está activo y cuándo empezaría a cobrarse.
    val trialEndsAtMillis  by viewModel.adManager.trialEndsAtMillis.collectAsStateWithLifecycle()
    // Trial automático sin tarjeta: distinto del de arriba -- acá no hay
    // ninguna suscripción, solo el trial que se da a todo el que instala.
    val autoTrialDaysRemaining by viewModel.adManager.autoTrialDaysRemaining.collectAsStateWithLifecycle()

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
        AccentColor.INDIGO -> stringResource(R.string.accent_color_indigo)
        AccentColor.RED    -> stringResource(R.string.accent_color_red)
        AccentColor.AMBER  -> stringResource(R.string.accent_color_amber)
        AccentColor.CYAN   -> stringResource(R.string.accent_color_cyan)
    }

    @Composable
    fun fontScaleLabel(scale: FontScale): String = when (scale) {
        FontScale.NORMAL      -> stringResource(R.string.font_scale_normal)
        FontScale.LARGE       -> stringResource(R.string.font_scale_large)
        FontScale.EXTRA_LARGE -> stringResource(R.string.font_scale_extra_large)
    }

    // Compartida entre el diálogo de Almacenamiento ("Limpiar caché") y
    // "Restablecer configuración" -- antes cada uno tenía su propia copia
    // (Almacenamiento) o directamente no lo hacía (Restablecer, bug real
    // encontrado 2026-09-14: el texto del diálogo prometía "se limpiará el
    // caché" pero el handler nunca tocaba ningún archivo).
    fun clearGeneratedFilesCache() {
        val convertedDir = java.io.File(context.filesDir, "converted")
        val pdfToolsDir  = java.io.File(context.filesDir, "pdftools")
        // HU-46: hallazgo real de la revisión de seguridad -- las copias
        // aplanadas de "Compartir con anotaciones" quedaban fuera de "Limpiar
        // caché", acumulándose para siempre sin que el usuario pudiera verlas
        // ni borrarlas.
        val viewerShareDir = java.io.File(context.filesDir, "viewer_share")
        // Hallazgo real de la revisión general 2026-09-16: mismo bug que
        // viewer_share en HU-46, nunca extendido a las notas/resúmenes
        // exportados desde Modo Estudio.
        val studyExportsDir = java.io.File(context.filesDir, "study_exports")
        val allFiles = convertedDir.listFiles()?.toList().orEmpty() +
            pdfToolsDir.listFiles()?.toList().orEmpty() +
            viewerShareDir.listFiles()?.toList().orEmpty() +
            studyExportsDir.listFiles()?.toList().orEmpty()
        if (allFiles.isNotEmpty()) {
            viewModel.moveConvertedFilesToTrash(allFiles.map { it.absolutePath })
        }
    }

    // Rediseño de Ajustes 2026-09-14: Tema/Color de acento/Tamaño de letra
    // pasan de diálogo a controles en línea dentro de la tarjeta de
    // Apariencia (ver AppearanceCard más abajo) -- ya no necesitan su
    // propio diálogo, solo Idioma lo conserva (lista larga, no cabe inline).
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStorageDialog  by remember { mutableStateOf(false) }
    var showAboutDialog    by remember { mutableStateOf(false) }
    var showPrivacyDialog  by remember { mutableStateOf(false) }
    var showHelpDialog     by remember { mutableStateOf(false) }
    var showResetDialog    by remember { mutableStateOf(false) }
    var showShareDialog    by remember { mutableStateOf(false) }
    var showUnlinkDownloadsDialog by remember { mutableStateOf(false) }

    // Fila 22 del backlog UX: estado de la carpeta de Descargas vinculada por
    // SAF (ver DownloadsAccessManager / LibraryScreen).
    val linkedDownloadsFolderUri by viewModel.linkedDownloadsFolderUri.collectAsStateWithLifecycle()
    val linkDownloadsFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.onDownloadsFolderPicked(it) } }

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
                // Bug real corregido 2026-09-04: sin scroll, agregar los 5
                // idiomas nuevos (10 en total) hizo que la lista excediera el
                // alto del diálogo -- la última entrada (Francés) quedaba con
                // altura cero, invisible pese a que el RadioButton sí se
                // dibujaba. Con más idiomas a futuro el problema solo crece.
                // Pedido explícito del usuario 2026-09-14: nada de check/radio
                // para elegir -- toda la fila es la acción, con la bandera del
                // idioma a la izquierda; el único indicador de selección es un
                // check al final, solo informativo (no es lo que se toca para
                // elegir).
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AppLanguage.entries.forEach { language ->
                        val selected = currentLanguage == language
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    languageManager.setLanguage(language)
                                    showLanguageDialog = false
                                }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(language.flagEmoji, style = MaterialTheme.typography.headlineSmall)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(language.nativeLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(language.code.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) {
                                Icon(Icons.Rounded.Check, null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp))
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

    // ── Diálogo: Almacenamiento ───────────────────────────────────────────────
    if (showStorageDialog) {
        val convertedDir    = java.io.File(context.filesDir, "converted")
        val pdfToolsDir     = java.io.File(context.filesDir, "pdftools")
        val viewerShareDir  = java.io.File(context.filesDir, "viewer_share")
        // Hallazgo real de la revisión general 2026-09-16: mismo bug que
        // viewer_share en HU-46, nunca extendido a study_exports/.
        val studyExportsDir  = java.io.File(context.filesDir, "study_exports")
        val convertedFiles  = convertedDir.listFiles()?.size ?: 0
        val pdfToolsFiles   = pdfToolsDir.listFiles()?.size ?: 0
        val viewerShareFiles = viewerShareDir.listFiles()?.size ?: 0
        val studyExportsFiles = studyExportsDir.listFiles()?.size ?: 0
        val totalFiles      = convertedFiles + pdfToolsFiles + viewerShareFiles + studyExportsFiles
        val convertedSize   = convertedDir.listFiles()?.sumOf { it.length() }?.div(1024) ?: 0
        val pdfToolsSize    = pdfToolsDir.listFiles()?.sumOf { it.length() }?.div(1024) ?: 0
        val viewerShareSize = viewerShareDir.listFiles()?.sumOf { it.length() }?.div(1024) ?: 0
        val studyExportsSize = studyExportsDir.listFiles()?.sumOf { it.length() }?.div(1024) ?: 0
        val totalSize       = convertedSize + pdfToolsSize + viewerShareSize + studyExportsSize

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
                    // Hallazgo #55 (revisión general 2026-09-16): viewer_share/
                    // y study_exports/ ya se sumaban al Total, pero sin fila
                    // propia -- un usuario que solo compartió "con
                    // anotaciones" o exportó notas de Estudio veía un Total
                    // mayor a cero sin ninguna fila que lo explicara.
                    if (viewerShareFiles > 0) {
                        StorageRow(
                            label  = stringResource(R.string.settings_storage_viewer_share),
                            files  = viewerShareFiles,
                            sizeKb = viewerShareSize
                        )
                    }
                    if (studyExportsFiles > 0) {
                        StorageRow(
                            label  = stringResource(R.string.settings_storage_study_exports),
                            files  = studyExportsFiles,
                            sizeKb = studyExportsSize
                        )
                    }
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
                        clearGeneratedFilesCache()
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

    // ── Diálogo: Desvincular carpeta de Descargas (fila 22 backlog UX) ─────────
    if (showUnlinkDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkDownloadsDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.settings_unlink_downloads_title),
                style = MaterialTheme.typography.titleLarge) },
            text  = { Text(stringResource(R.string.settings_unlink_downloads_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unlinkDownloadsFolder()
                    showUnlinkDownloadsDialog = false
                }) {
                    Text(
                        text  = stringResource(R.string.settings_unlink_downloads_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDownloadsDialog = false }) {
                    Text(stringResource(R.string.general_cancel))
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
                    // Bug real encontrado 2026-09-14: este handler no
                    // reseteaba fondo animado/sonido (quedaban como el
                    // usuario los hubiera dejado, sin avisar) ni limpiaba el
                    // caché, pese a que el texto del diálogo promete ambas
                    // cosas -- ver settings_reset_body.
                    themeManager.setTheme(AppTheme.SYSTEM)
                    themeManager.setAccentColor(AccentColor.BLUE)
                    themeManager.setFontScale(FontScale.NORMAL)
                    themeManager.setAnimatedBackgroundEnabled(true)
                    viewModel.soundEffectPlayer.setEnabled(true)
                    languageManager.setLanguage(languageManager.deviceDefaultLanguage())
                    clearGeneratedFilesCache()
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

    // Rediseño de Ajustes 2026-09-14 (pedido explícito del usuario, con
    // referencia visual propia): Tema/Tamaño de letra como segmented buttons,
    // Color de acento como carrusel horizontal (con más colores de los que
    // caben en pantalla, para dejar claro que se puede deslizar), Fondo
    // animado/Sonidos como switch, e Idioma como fila que abre el selector --
    // todo dentro de una sola tarjeta. Cada bloque es su propia función local
    // (LongMethod de detekt) pero todas leen el estado de SettingsScreen
    // directamente por clausura, sin repetir parámetros.
    @Composable
    fun AppearanceThemeRow() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppTheme.entries.forEachIndexed { index, theme ->
                    SegmentedButton(
                        selected = currentTheme == theme,
                        onClick  = { themeManager.setTheme(theme) },
                        shape    = SegmentedButtonDefaults.itemShape(index, AppTheme.entries.size),
                        label    = { Text(themeLabel(theme)) }
                    )
                }
            }
        }
    }

    @Composable
    fun AppearanceAccentColorRow() {
        val accentCarouselState = rememberLazyListState()
        // Nudge de una sola vez (no un loop continuo, para no distraer ni
        // gastar batería): desliza un poco y vuelve, así el usuario nota que
        // hay más colores de los que se ven de entrada.
        LaunchedEffect(Unit) {
            delay(600)
            accentCarouselState.animateScrollToItem(
                (AccentColor.entries.size - 1).coerceAtMost(4)
            )
            delay(450)
            accentCarouselState.animateScrollToItem(0)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.settings_accent_color),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(accentColorLabel(currentAccentColor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyRow(
                state                 = accentCarouselState,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(AccentColor.entries.toList()) { accent ->
                    val selected = currentAccentColor == accent
                    val label = accentColorLabel(accent)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(accent.swatch, shape = CircleShape)
                            // Antes no tenía ninguna etiqueta accesible (ni
                            // texto ni contentDescription) -- ni TalkBack ni
                            // un test de Compose podían identificar qué color
                            // era cada círculo, solo la posición.
                            .semantics { contentDescription = label }
                            .clickable { themeManager.setAccentColor(accent) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, null,
                                tint     = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AppearanceFontScaleRow() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_font_scale),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                FontScale.entries.forEachIndexed { index, scale ->
                    SegmentedButton(
                        selected = currentFontScale == scale,
                        onClick  = { themeManager.setFontScale(scale) },
                        shape    = SegmentedButtonDefaults.itemShape(index, FontScale.entries.size),
                        label    = { Text(fontScaleLabel(scale)) }
                    )
                }
            }
        }
    }

    @Composable
    fun AppearanceToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    @Composable
    fun AppearanceLanguageRow(onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Language, null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.settings_language),
                style    = MaterialTheme.typography.titleSmall,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Text(currentLanguage.flagEmoji,
                style = MaterialTheme.typography.titleMedium)
            Text(currentLanguage.nativeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Rounded.ChevronRight, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }

    @Composable
    fun AppearanceCard(onLanguageRowClick: () -> Unit) {
        val shape = MaterialTheme.shapes.large
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .accentShadow(shape = shape, elevation = 2.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                AppearanceThemeRow()
                AppearanceAccentColorRow()
                AppearanceFontScaleRow()
                HorizontalDivider()
                AppearanceToggleRow(
                    title    = stringResource(R.string.settings_animated_background),
                    subtitle = stringResource(R.string.settings_animated_background_subtitle),
                    checked  = animatedBackgroundEnabled,
                    onCheckedChange = { themeManager.setAnimatedBackgroundEnabled(it) }
                )
                AppearanceToggleRow(
                    title    = stringResource(R.string.settings_sound_effects),
                    subtitle = stringResource(R.string.settings_sound_effects_subtitle),
                    checked  = soundEffectsEnabled,
                    onCheckedChange = { viewModel.soundEffectPlayer.setEnabled(it) }
                )
                HorizontalDivider()
                AppearanceLanguageRow(onClick = onLanguageRowClick)
            }
        }
    }

    // ── UI Principal ──────────────────────────────────────────────────────────
    LazyColumn(
        // Rediseño de Ajustes 2026-09-14: el carrusel de colores de acento
        // agregó un segundo LazyRow con scroll dentro de esta lista -- un
        // testTag propio evita la ambigüedad de hasScrollAction() en los
        // tests de Compose (SettingsScreenTest ya la encontró real).
        modifier       = Modifier.fillMaxSize().testTag("settings_list"),
        contentPadding = PaddingValues(
            bottom = 100.dp,
            start = 16.dp, end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pedido explícito del usuario 2026-09-07: mismo margen/espaciado de
        // banner que el resto de las pantallas -- acá el margen horizontal
        // ya lo da el `contentPadding` de arriba (todos los ítems de esta
        // pantalla lo comparten, no solo el banner), así que en vez de
        // DocuSmartScreenHeader se arma el mismo espacio de 8dp a mano, en
        // un solo ítem (para que `spacedBy` de la lista no sume su propio
        // espacio entre el anuncio y el banner).
        item {
            // Pedido explícito del usuario 2026-09-07 (seguimiento): 12dp de
            // espacio arriba, igual que DocuSmartScreenHeader, para que no
            // quede pegado al borde/barra de estado.
            Column(modifier = Modifier.padding(top = 12.dp)) {
                if (!isPremium) {
                    DocuSmartBannerAd(
                        adUnitId  = AdConstants.BANNER_SETTINGS_ID,
                        adManager = viewModel.adManager
                    )
                    Spacer(Modifier.height(8.dp))
                }
                DocuSmartTopBanner(
                    screenTitle    = stringResource(R.string.settings_title),
                    screenSubtitle = stringResource(R.string.settings_subtitle)
                )
            }
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
                        val isInTrial = trialEndsAtMillis != null &&
                            trialEndsAtMillis!! > System.currentTimeMillis()
                        val subtitle = when {
                            isInTrial -> {
                                // Lint real (NonObservableLocale): ver
                                // comentario equivalente en
                                // PremiumScreen.PremiumActiveCard.
                                val locale = LocalConfiguration.current.locales[0]
                                val formattedDate = java.text.SimpleDateFormat("dd/MM/yyyy", locale)
                                    .format(java.util.Date(trialEndsAtMillis!!))
                                stringResource(R.string.settings_premium_trial_subtitle, formattedDate)
                            }
                            // Trial automático sin tarjeta: no hay
                            // suscripción real, solo el trial que se da a
                            // todo el que instala -- se distingue del de
                            // arriba porque no hay fecha de cobro que avisar.
                            autoTrialDaysRemaining != null ->
                                stringResource(
                                    R.string.settings_premium_auto_trial_subtitle,
                                    autoTrialDaysRemaining!!
                                )
                            else -> stringResource(R.string.settings_premium_subtitle)
                        }
                        Text(
                            text  = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Sección: Apariencia (rediseño 2026-09-14) ─────────────────────────
        // Una sola tarjeta con controles en línea (Tema/Tamaño de letra en
        // segmented buttons, Color de acento en carrusel, Fondo animado y
        // Sonidos en switch, Idioma como fila que abre el selector) en vez de
        // 6 filas sueltas que cada una abría su propio diálogo -- mismo orden
        // pedido por el usuario, con referencia visual propia.
        item { SettingsSectionHeader(stringResource(R.string.settings_section_appearance)) }
        item { AppearanceCard(onLanguageRowClick = { showLanguageDialog = true }) }

        // ── Sección: Archivos y privacidad (rediseño 2026-09-14) ──────────────
        // Fusiona las antiguas secciones "Almacenamiento" y "Privacidad y
        // seguridad" en una sola, mismo contenido de cada una.
        item { SettingsSectionHeader(stringResource(R.string.settings_section_files_privacy)) }
        item {
            SettingsItem(
                icon     = Icons.Rounded.Storage,
                title    = stringResource(R.string.settings_storage),
                subtitle = stringResource(R.string.settings_storage_subtitle),
                onClick  = { showStorageDialog = true }
            )
        }
        // Fila 22 del backlog UX: gestionar (vincular/desvincular) la carpeta
        // de Descargas para ver PDF/Word/Excel/PowerPoint/Texto reales en la
        // pestaña Dispositivo de Biblioteca (ver LibraryScreen).
        item {
            SettingsItem(
                icon     = Icons.Rounded.FolderOpen,
                title    = stringResource(R.string.settings_linked_downloads_title),
                subtitle = if (linkedDownloadsFolderUri != null)
                    stringResource(R.string.settings_linked_downloads_linked)
                else
                    stringResource(R.string.settings_linked_downloads_not_linked),
                onClick  = {
                    if (linkedDownloadsFolderUri != null) {
                        showUnlinkDownloadsDialog = true
                    } else {
                        linkDownloadsFolderLauncher.launch(viewModel.downloadsFolderPickerInitialUri())
                    }
                }
            )
        }
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

        // ── Sección: Ayuda y comunidad (rediseño 2026-09-14) ──────────────────
        // Fusiona Tutorial (antes en Personalización), Ayuda (antes "Soporte")
        // y Compartir/Valorar (antes sin encabezado propio) en una sección,
        // mismo orden que el diseño de referencia.
        item { SettingsSectionHeader(stringResource(R.string.settings_section_help_community)) }
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
                icon     = Icons.Rounded.HelpOutline,
                title    = stringResource(R.string.settings_help),
                subtitle = stringResource(R.string.settings_help_subtitle),
                onClick  = { showHelpDialog = true }
            )
        }
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
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .accentShadow(shape = shape, elevation = 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .accentBorder(shape = shape)
            .clickable { onClick() }
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