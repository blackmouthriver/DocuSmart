package com.docsmart.features.scanner.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.WarningAmber
import com.docsmart.core.ui.theme.accentBorder

// HU-45 (backlog UX 2026-08-30/09-14): paleta fija de colores para los
// módulos del QR (RF1) -- mismo criterio visual que el selector de "Color
// de acento" de Ajustes (círculos en fila), pero con su propia paleta
// porque acá el significado es distinto (color de impresión sobre fondo
// blanco fijo, no theming de la UI). Incluye a propósito un color de bajo
// contraste (Amarillo) para que la advertencia de AC1 sea alcanzable real
// en la práctica, no solo lógica muerta -- los otros 7 ya están
// verificados por unit test (`QrColorValidationTest`) para pasar el umbral.
// Bug real encontrado durante la implementación: `Color(Int)` de Compose y
// `Bitmap.setPixel()` esperan el canal alfa incluido (ARGB empaquetado) --
// un literal de 24 bits sin el prefijo `0xFF` de alfa se interpreta como
// completamente transparente (módulos invisibles). Todos los presets
// llevan `0xFF______.toInt()` (el `.toInt()` es necesario porque el
// literal excede el rango de Int con signo de 32 bits).

// Bug real encontrado en la revisión pre-fusión: el color por defecto
// dependía implícitamente de ser el primer elemento de `QR_COLOR_PRESETS`
// -- un futuro reordenamiento de la paleta (por estética) lo habría
// cambiado en silencio, sin que ningún test lo detectara. Constante
// explícita, usada tanto acá como en el estado inicial de
// `QrCreatorScreen` y el default param de `generateQrBitmap`.
internal const val QR_DEFAULT_MODULE_COLOR = 0xFF000000.toInt() // Negro

internal val QR_COLOR_PRESETS = listOf(
    QR_DEFAULT_MODULE_COLOR, // Negro (default)
    0xFF1976D2.toInt(), // Azul
    0xFF7B1FA2.toInt(), // Púrpura
    0xFF2E7D32.toInt(), // Verde
    0xFFC62828.toInt(), // Rojo
    0xFFE65100.toInt(), // Naranja
    0xFF00695C.toInt(), // Teal
    0xFFFBC02D.toInt()  // Amarillo -- contraste insuficiente a propósito, ver arriba
)

@Composable
fun QrDesignSection(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    hasSufficientContrast: Boolean,
    logoBitmap: Bitmap?,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.qr_design_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            QR_COLOR_PRESETS.forEach { colorInt ->
                val isSelected = colorInt == selectedColor
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(colorInt))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(colorInt) }
                )
            }
        }
        if (!hasSufficientContrast) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.qr_design_contrast_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber
                )
            }
        }

        if (logoBitmap != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = logoBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )
                Text(
                    text = stringResource(R.string.qr_design_logo_added),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemoveLogo) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.qr_design_remove_logo)
                    )
                }
            }
        } else {
            OutlinedButton(
                onClick = onPickLogo,
                modifier = Modifier.accentBorder(MaterialTheme.shapes.medium)
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.qr_design_add_logo))
            }
        }
    }
}
