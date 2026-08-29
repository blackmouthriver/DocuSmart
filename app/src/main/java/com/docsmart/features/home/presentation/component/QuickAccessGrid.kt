package com.docsmart.features.home.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.components.cards.DocuSmartQuickAccessCard
import com.docsmart.core.ui.theme.*

// Ancho fijo por tarjeta -- con 9 accesos no caben todos en una sola fila de
// ancho igual (como antes, con 4). Se pasó a una fila con scroll horizontal
// en vez de reducir el tamaño de cada tarjeta hasta hacerla ilegible; el
// ancho se eligió para que ~4.3 tarjetas queden visibles en un teléfono
// típico, con la última parcialmente cortada como pista de que hay más.
private val QUICK_ACCESS_CARD_WIDTH = 82.dp

data class QuickAccessItem(
    val icon   : ImageVector,
    val label  : String,
    val color  : Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessGrid(
    onScanClick      : () -> Unit,
    onImageToPdfClick: () -> Unit,
    onSafeBoxClick   : () -> Unit,
    onStudyModeClick : () -> Unit,
    onQrClick        : () -> Unit = {},
    onQrReaderClick  : () -> Unit = {},  // ← leer QR
    onQrCreatorClick : () -> Unit = {},  // ← crear QR
    onNotesClick     : () -> Unit = {},  // ← NUEVO: notas de Estudio
    onPomodoroClick  : () -> Unit = {},  // ← NUEVO: pomodoro de Estudio
    onTrashClick     : () -> Unit = {},  // ← NUEVO: papelera de Biblioteca
    modifier         : Modifier = Modifier
) {
    var showScannerSheet by remember { mutableStateOf(false) }
    val sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showScannerSheet) {
        ScannerBottomSheetContent(
            sheetState        = sheetState,
            onDismiss         = { showScannerSheet = false },
            onScanClick       = onScanClick,
            onQrReaderClick   = onQrReaderClick,
            onQrCreatorClick  = onQrCreatorClick
        )
    }

    // RF: se agregaron accesos directos a Lectura/Notas/Pomodoro (antes solo
    // "Estudio" genérico, siempre abría en Lectura) y a Leer/Crear QR y
    // Papelera (antes Leer/Crear QR solo estaban dentro del hoja de Escanear,
    // y Papelera solo dentro de Biblioteca) -- pedido explícito del usuario.
    val items = listOf(
        QuickAccessItem(
            icon    = Icons.Rounded.DocumentScanner,
            label   = stringResource(R.string.home_scan),
            color   = InfoCyan,
            onClick = { showScannerSheet = true }
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.Image,
            label   = stringResource(R.string.home_img_pdf),
            color   = ColorPdf,
            onClick = onImageToPdfClick
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.Lock,
            label   = stringResource(R.string.home_security),
            color   = PremiumGold,
            onClick = onSafeBoxClick
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.MenuBook,
            label   = stringResource(R.string.study_tab_reading),
            color   = SuccessGreen,
            onClick = onStudyModeClick
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.EditNote,
            label   = stringResource(R.string.study_tab_notes),
            color   = IndigoAccent,
            onClick = onNotesClick
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.Timer,
            label   = stringResource(R.string.study_tab_pomodoro),
            color   = WarningAmber,
            onClick = onPomodoroClick
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.QrCodeScanner,
            label   = stringResource(R.string.home_qr_read),
            color   = DocuBlue,
            onClick = onQrReaderClick
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.QrCode,
            label   = stringResource(R.string.home_qr_create),
            color   = SmartBlue,
            onClick = onQrCreatorClick
        ),
        QuickAccessItem(
            icon    = Icons.Rounded.DeleteOutline,
            label   = stringResource(R.string.library_trash),
            color   = MaterialTheme.colorScheme.error,
            onClick = onTrashClick
        )
    )

    Column(modifier = modifier) {
        Text(
            text  = stringResource(R.string.home_quick_access),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                DocuSmartQuickAccessCard(
                    icon     = item.icon,
                    label    = item.label,
                    onClick  = item.onClick,
                    iconTint = item.color,
                    modifier = Modifier.width(QUICK_ACCESS_CARD_WIDTH)
                )
            }
        }
    }
}

// Extraído de QuickAccessGrid (LongMethod de detekt) -- contenido del sheet
// que se abre al tocar "Escanear". De paso corrige textos hardcodeados en
// español ("Scanner", "Elige una opción", etc.) que no pasaban por
// stringResource(), mismo tipo de bug ya corregido en Recientes/Ver todos.
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScannerBottomSheetContent(
    sheetState      : SheetState,
    onDismiss       : () -> Unit,
    onScanClick     : () -> Unit,
    onQrReaderClick : () -> Unit,
    onQrCreatorClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
        tonalElevation   = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Cabecera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(InfoCyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.DocumentScanner,
                        contentDescription = null,
                        tint               = InfoCyan,
                        modifier           = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text       = stringResource(R.string.home_scanner_sheet_title),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text  = stringResource(R.string.home_scanner_sheet_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 20.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(Modifier.height(4.dp))

            // Opción 1 — Escanear documento
            ScannerOption(
                icon     = Icons.Rounded.DocumentScanner,
                title    = stringResource(R.string.home_scanner_option_scan_title),
                subtitle = stringResource(R.string.home_scanner_option_scan_subtitle),
                color    = InfoCyan,
                onClick  = {
                    onDismiss()
                    onScanClick()
                }
            )

            // Opción 2 — Leer QR
            ScannerOption(
                icon     = Icons.Rounded.QrCodeScanner,
                title    = stringResource(R.string.qr_reader_title),
                subtitle = stringResource(R.string.home_scanner_option_qr_read_subtitle),
                color    = MaterialTheme.colorScheme.primary,
                onClick  = {
                    onDismiss()
                    onQrReaderClick()
                }
            )

            // Opción 3 — Crear QR
            ScannerOption(
                icon     = Icons.Rounded.QrCode,
                title    = stringResource(R.string.qr_creator_title),
                subtitle = stringResource(R.string.qr_creator_subtitle),
                color    = SuccessGreen,
                onClick  = {
                    onDismiss()
                    onQrCreatorClick()
                }
            )
        }
    }
}

@Composable
private fun ScannerOption(
    icon    : ImageVector,
    title   : String,
    subtitle: String,
    color   : Color,
    onClick : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = color,
                modifier           = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector        = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(18.dp)
        )
    }
}