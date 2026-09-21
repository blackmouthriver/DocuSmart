package com.docsmart.features.scanner.presentation

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.docsmart.R
import com.docsmart.core.ui.theme.accentFilterChipColors
import com.docsmart.features.scanner.domain.ScanColorMode
import com.docsmart.features.scanner.domain.buildColorModeMatrix

// Backlog UX 2026-08-30/09-14 (HU-41): selector de modo de color de una
// página escaneada, con una miniatura de vista previa por chip que aplica
// en vivo la misma matriz de color (buildColorModeMatrix) que después se
// usa para el bake real en ScanImageEditor.applyColorMode() -- lo que se ve
// en el chip es exactamente lo que queda guardado (AC1). Reutilizado tanto
// para el modo "por defecto" del documento (ScanColorModeSection, debajo)
// como para el override por página dentro de ScanImageEditorDialog (RF2).
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScanColorModeChipRow(
    previewUri: Uri?,
    selected: ScanColorMode,
    onSelect: (ScanColorMode) -> Unit,
) {
    // Bug real encontrado 2026-09-14 (verificación en dispositivo real):
    // con 4 chips (miniatura + etiqueta larga en varios idiomas, ej.
    // "Schwarzweiß"/"Escala de grises") un Row fijo no entra en un ancho de
    // teléfono típico y el último chip queda totalmente fuera de pantalla,
    // sin scroll para alcanzarlo -- a diferencia de ScanFormatSection/
    // PercentChipRow, cuyas etiquetas son cortas y sí entran siempre.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ScanColorMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(mode.labelRes())) },
                leadingIcon =
                    previewUri?.let { uri ->
                        {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(ColorMatrix(buildColorModeMatrix(mode))),
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                            )
                        }
                    },
                colors = accentFilterChipColors(),
            )
        }
    }
}

private fun ScanColorMode.labelRes(): Int =
    when (this) {
        ScanColorMode.COLOR -> R.string.scan_color_mode_color
        ScanColorMode.BLACK_AND_WHITE -> R.string.scan_color_mode_bw
        ScanColorMode.GRAYSCALE -> R.string.scan_color_mode_grayscale
        ScanColorMode.HIGHLIGHT_TEXT -> R.string.scan_color_mode_highlight
    }

// RF1/RF2: título + fila de chips para el modo de color "por defecto" del
// documento -- ScanResultScreen aplica el modo elegido acá a todas las
// páginas que no tengan su propio override (ver ScanColorModeChipRow
// dentro de ScanImageEditorDialog para el override por página).
@Composable
fun ScanColorModeSection(
    previewUri: Uri?,
    selected: ScanColorMode,
    onSelect: (ScanColorMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.scan_color_mode_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ScanColorModeChipRow(previewUri = previewUri, selected = selected, onSelect = onSelect)
    }
}
