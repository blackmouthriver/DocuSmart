package com.docsmart.features.pdftools.presentation.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.R

// Duplicación de UI corregida 2026-09-09 (hallazgo del audit de
// arquitectura): la "caja de selección de PDF" y el "footer de progreso/
// botón" estaban repetidos casi idénticos en las 14 pantallas de
// Herramientas PDF (Xxx*Screen.kt en este mismo paquete), cada una con su
// propia copia local. Se consolidan acá, parametrizados por color de
// acento/texto/ícono -- el contenido intermedio específico de cada
// herramienta (rango de páginas, preview de rotación, editor de redacción,
// captura de firma, etc.) queda exactamente igual que antes, sin tocar.
//
// Merge es la única de las 14 que NO usa PdfSelectZone: no es "elegir 1
// archivo" sino "agregar más" (multi-select, lista de archivos removibles,
// sin nombre de archivo mostrado en la caja) -- su propio
// MergePdfSelectZone privado queda como estaba, es una forma legítimamente
// distinta de resolver el mismo problema, no la misma UI repetida.
//
// Normalización menor de paso: Split/Merge eran las únicas 2 sin acento
// propio (usan `MaterialTheme.colorScheme.primary` por defecto) y por eso
// no tenían `disabledContainerColor`/`trackColor` explícitos como las
// otras 12 -- ahora los 14 comparten exactamente la misma fórmula
// (`accentColor.copy(alpha = 0.2f)` / `MaterialTheme.colorScheme.surfaceVariant`),
// visualmente casi idéntico a lo que ya tenían.

/**
 * Caja de "elegir un PDF" reutilizada por 13 de las 14 herramientas PDF
 * (todas menos Merge). `Comparar PDF` la usa dos veces (una por documento)
 * pasando [label].
 */
@Composable
fun PdfSelectZone(
    selectedPdf: Uri?,
    onSelectPdf: () -> Unit,
    readyText: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    label: String? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(MaterialTheme.shapes.large)
                .border(
                    width = if (selectedPdf != null) 1.dp else 1.5.dp,
                    color = if (selectedPdf != null)
                        accentColor
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.large
                )
                .background(
                    if (selectedPdf != null)
                        accentColor.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                )
                .clickable { onSelectPdf() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (selectedPdf != null)
                        Icons.Rounded.CheckCircle
                    else
                        Icons.Rounded.FileOpen,
                    contentDescription = null,
                    tint = if (selectedPdf != null) accentColor else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                // Hallazgo real de la revisión general 2026-09-16 (#24):
                // sin maxLines/overflow, un nombre de archivo largo
                // envolvía a una segunda línea dentro de esta caja de
                // altura fija (72.dp) -- desbordaba visualmente el
                // contenedor de bordes redondeados. MergePdfScreen (la
                // única de las 14 que no usa este componente) ya lo hacía
                // bien; se replica el mismo criterio acá.
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = if (selectedPdf != null) readyText
                               else stringResource(R.string.pdf_tools_select_pdf_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedPdf != null) accentColor else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (selectedPdf != null) {
                        Text(
                            text = selectedPdf.lastPathSegment
                                ?.substringAfterLast("/") ?: stringResource(R.string.pdf_tools_default_filename),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Footer de "progreso o botón de ejecutar" repetido en las 14 herramientas
 * PDF -- barra de progreso mientras [isProcessing], botón de ejecutar en
 * caso contrario.
 */
@Composable
fun PdfProcessingFooter(
    isProcessing: Boolean,
    enabled: Boolean,
    progressText: String,
    buttonLabel: String,
    buttonIcon: ImageVector,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    if (isProcessing) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.2f)
            )
            Text(
                text = progressText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Button(
            onClick = onExecute,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Icon(
                imageVector = buttonIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
