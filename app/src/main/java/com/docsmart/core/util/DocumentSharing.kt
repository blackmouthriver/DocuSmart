package com.docsmart.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.docsmart.core.ui.components.DocumentUiModel
import timber.log.Timber
import java.io.File

// Código duplicado corregido 2026-09-09: esta misma lógica (probar el id
// como content:// directo, y si falla reintentar como ruta de archivo real
// vía FileProvider) estaba repetida casi idéntica en DocumentListSection.kt,
// FavoritesSection.kt y RecentDocuments.kt. De paso corrige dos bugs
// menores reales: DocumentListSection/FavoritesSection tenían "Compartir
// ${document.name}" fijo en español en vez de stringResource (RecentDocuments
// ya lo hacía bien, pasando el label ya localizado), y el catch final de
// las tres usaba printStackTrace() en vez de Timber, silenciando cualquier
// fallo real sin dejar rastro en los logs de producción.
//
// No se unifica acá `ViewerViewModel.shareDocument()`: opera sobre un `Uri`
// ya resuelto + mimeType real (no un `DocumentUiModel`) y actualiza el
// estado de error de esa pantalla -- es una forma legítimamente distinta
// de resolver el mismo problema, no la misma lógica repetida.
fun shareDocument(context: Context, document: DocumentUiModel, chooserTitle: String) {
    try {
        val uri = Uri.parse(document.id)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, document.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: Exception) {
        try {
            val file = File(document.id)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        } catch (e2: Exception) {
            Timber.e(e2, "DocumentSharing: error compartiendo ${document.name}")
        }
    }
}
