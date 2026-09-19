package com.docsmart.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.docsmart.core.ui.components.DocumentUiModel
import timber.log.Timber
import java.io.File

// Código duplicado corregido 2026-09-09: esta misma lógica (decidir entre
// usar el id como content:// directo o como ruta de archivo real vía
// FileProvider) estaba repetida casi idéntica en DocumentListSection.kt,
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
//
// Hallazgo real de esta ronda (auditoría 2026-09-18, Favoritos/Descargas):
// la versión original (heredada tal cual de los 3 sitios duplicados)
// decidía content:// vs archivo real intentando `Uri.parse(document.id)` y
// esperando que lanzara una excepción para el caso "es una ruta real" --
// pero `Uri.parse()` NUNCA lanza (es un parser laxo: si no hay esquema,
// simplemente arma una Uri con scheme=null). Para documentos generados
// localmente (Convertidor/Herramientas PDF/Escáner), `document.id` es la
// ruta absoluta real del archivo (ver TrashRepositoryTest, que usa
// `file.absolutePath` como documentId) -- el try "feliz" nunca fallaba,
// así que el intent salía con una Uri sin esquema (el receptor no puede
// abrirla) y la rama de FileProvider, pensada justo para este caso, nunca
// se ejecutaba: "Compartir" fallaba en silencio para esos documentos. Se
// decide ahora por el esquema real de la Uri, no por si algo lanza.
fun shareDocument(
    context: Context,
    document: DocumentUiModel,
    chooserTitle: String,
) {
    try {
        val uri = resolveShareUri(context, document.id)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, document.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: Exception) {
        // Hallazgo real de esta ronda: el log original incluía
        // `document.name` (nombre real del documento del usuario) en el
        // mensaje y pasaba la excepción original como Throwable --
        // CrashlyticsTree reenvía ambos a Firebase Crashlytics (log ≥WARN
        // con Throwable → recordException()). Se redacta el nombre del
        // mensaje y se loguea solo el tipo de excepción, mismo criterio que
        // SecurityManager/PdfPasswordUseCase/DownloadsAccessManager/
        // DownloadsSaver.
        Timber.e(
            RuntimeException("DocumentSharing: ${e.javaClass.simpleName}"),
            "DocumentSharing: error compartiendo documento",
        )
    }
}

/**
 * Decide qué Uri usar para compartir según el esquema real de `documentId`,
 * en vez de basarse en que `Uri.parse()` lance una excepción (nunca lo
 * hace). Extraída como función propia, testeable sin necesidad de construir
 * un `Intent` real (el proyecto no usa Robolectric para tests unitarios).
 */
internal fun resolveShareUri(
    context: Context,
    documentId: String,
): Uri {
    val parsed = Uri.parse(documentId)
    return when (parsed.scheme) {
        "content" -> parsed
        // Uri con esquema file:// (bug ya corregido en otro lugar del
        // proyecto por el mismo motivo -- exponer un file:// crudo a otra
        // app crashea en targetSdk 36 con FileUriExposedException): se
        // extrae la ruta real y se rearma vía FileProvider.
        "file" ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(parsed.path ?: documentId),
            )
        // Sin esquema (o cualquier otro): documentId es una ruta de archivo
        // real de almacenamiento interno de la app.
        else ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(documentId),
            )
    }
}
