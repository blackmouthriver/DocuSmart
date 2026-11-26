package com.docsmart.features.utilities.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.components.DocuSmartTopBanner
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import timber.log.Timber

private val OPENABLE_COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

/** Nombre y tamaño reales del archivo; si el proveedor no los da, el último segmento de la ruta y -1. */
fun queryPickedFile(
    context: Context,
    uri: Uri,
): PickedFile {
    val (name, size) = queryNameAndSize(context, uri)
    return PickedFile(uri, name ?: uri.lastPathSegment ?: "archivo", size)
}

private fun queryNameAndSize(
    context: Context,
    uri: Uri,
): Pair<String?, Long> =
    try {
        context.contentResolver.query(uri, OPENABLE_COLUMNS, null, null, null)?.use(::readNameAndSize) ?: (null to -1L)
    } catch (e: SecurityException) {
        Timber.w("UtilitiesCommon: ${e.javaClass.simpleName} al leer los datos de un archivo")
        null to -1L
    }

private fun readNameAndSize(cursor: Cursor): Pair<String?, Long> {
    if (!cursor.moveToFirst()) return null to -1L
    return cursor.getString(0) to (if (cursor.isNull(1)) -1L else cursor.getLong(1))
}

fun copyText(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("DocuSmart", text))
    Toast.makeText(context, context.getString(R.string.util_copied), Toast.LENGTH_SHORT).show()
}

fun shareText(
    context: Context,
    text: String,
    chooserTitle: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

/** Estructura común de las utilidades: banner con volver, contenido que se desplaza y la nota de privacidad. */
@Composable
internal fun UtilityScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DocuSmartTopBanner(screenTitle = title, screenSubtitle = subtitle, onBack = onBack, onHome = onHome)
        content()
        Text(
            text = stringResource(R.string.util_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tarjeta con el mismo borde y sombra de acento que el resto de las pantallas. */
@Composable
internal fun UtilityCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .accentShadow(shape = shape, elevation = 2.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun SectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}
