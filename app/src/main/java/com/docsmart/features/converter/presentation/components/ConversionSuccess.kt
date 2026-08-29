package com.docsmart.features.converter.presentation.components

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.docsmart.R
import com.docsmart.core.ui.theme.ColorExcel
import com.docsmart.core.ui.theme.ColorImage
import com.docsmart.core.ui.theme.ColorOcr
import com.docsmart.core.ui.theme.ColorPdf
import com.docsmart.core.ui.theme.ColorPowerPoint
import com.docsmart.core.ui.theme.ColorText
import com.docsmart.core.ui.theme.ColorWord
import com.docsmart.features.converter.domain.model.ConversionResult
import timber.log.Timber
import java.io.File

@Composable
fun ConversionSuccess(
    result: ConversionResult.Success,
    savedToDownloads: Boolean,
    onConvertAnother: () -> Unit,
    onSaveToDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shareLabel = stringResource(R.string.converter_share)
    val (fileIcon, fileColor) = formatIconForExtension(result.outputFile.extension)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Ícono de éxito ────────────────────────
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraLarge
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // ── Título ────────────────────────────────
            Text(
                text = stringResource(R.string.converter_success_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ── Detalles del archivo ──────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = fileIcon,
                            contentDescription = null,
                            tint = fileColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = result.outputFile.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.converter_success_page_count_size,
                            result.pageCount, result.fileSizeKb
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Confirmación de guardado ──────────────
            if (savedToDownloads) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.converter_saved_to_downloads),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── Botones ───────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!savedToDownloads) {
                    Button(
                        onClick = onSaveToDownloads,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.converter_save),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                OutlinedButton(
                    onClick = { shareFile(context, result.outputFile, shareLabel) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = shareLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                TextButton(
                    onClick = onConvertAnother,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.converter_convert_another),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

// ── Ícono/color por extensión real del archivo de salida ──────────────────
// ConversionSuccess se reutiliza para las 17 conversiones (no solo
// PDF/imagen) -- antes el ícono, el MIME type al compartir y el texto de
// los botones asumían PDF/imagen a propósito fijo, mostrando el ícono y
// el MIME equivocados para el resto (Excel→CSV, Word→HTML, PPT→Texto...).
internal fun formatIconForExtension(extension: String): Pair<ImageVector, Color> =
    when (extension.lowercase()) {
        "pdf"                         -> Icons.Rounded.PictureAsPdf   to ColorPdf
        "jpg", "jpeg", "png",
        "webp", "bmp"                 -> Icons.Rounded.Image          to ColorImage
        "doc", "docx"                 -> Icons.Rounded.Description    to ColorWord
        "xls", "xlsx", "csv"          -> Icons.Rounded.TableChart     to ColorExcel
        "ppt", "pptx"                 -> Icons.Rounded.Slideshow      to ColorPowerPoint
        "txt"                         -> Icons.Rounded.TextSnippet    to ColorText
        "html"                        -> Icons.Rounded.Code           to ColorOcr
        else                          -> Icons.Rounded.InsertDriveFile to ColorText
    }

// ── Fix Sentinel: manejo de errores en FileProvider ───
// Antes: si el archivo no existía o FileProvider fallaba
// la app crasheaba sin mensaje al usuario
// Ahora: captura la excepción y loguea con Timber
internal fun shareFile(context: Context, file: File, shareLabel: String) {
    try {
        // ── Verificar que el archivo existe antes de compartir
        if (!file.exists()) {
            Timber.e("shareFile: archivo no encontrado — ${file.absolutePath}")
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // ── Permisos explícitos para el receptor ──
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, shareLabel))
        Timber.d("shareFile: compartiendo ${file.name}")

    } catch (e: IllegalArgumentException) {
        // FileProvider no encontró el archivo en las rutas configuradas
        Timber.e(e, "shareFile: archivo fuera de rutas FileProvider — ${file.absolutePath}")
    } catch (e: Exception) {
        Timber.e(e, "shareFile: error inesperado — ${e.message}")
    }
}