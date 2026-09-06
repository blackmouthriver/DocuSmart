package com.docsmart.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlin.math.roundToInt

/**
 * Miniatura de la primera página de un PDF para las listas de documentos
 * (Biblioteca/Favoritos/Recientes -- backlog UX #25, pedido explícito del
 * usuario 2026-09-06: "mostrar una miniatura visual del contenido" en vez
 * de solo el ícono/color por tipo). Coil 2.7.0 (ya en el proyecto, ver
 * ConverterScreen.kt/ScanResultScreen.kt) trae decodificadores para
 * imágenes normales (JPG/PNG/WebP) pero no para PDF -- Android no tiene un
 * `BitmapFactory` para ese formato, solo [PdfRenderer], que entrega un
 * `Bitmap` ya compuesto en vez de un stream de bytes de imagen. Este
 * Fetcher le enseña a Coil a usar [PdfRenderer] como si fuera un
 * decodificador más, así `document.toContentUri()` funciona igual para
 * `AsyncImage`/`SubcomposeAsyncImage` sin importar si es una imagen o un
 * PDF.
 */
class PdfThumbnailFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("No se pudo abrir el PDF para miniatura: $uri")
        return descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val scale = THUMBNAIL_WIDTH_PX.toFloat() / page.width
                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        THUMBNAIL_WIDTH_PX,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    // Las páginas de PDF suelen tener zonas transparentes donde
                    // no hay contenido -- sin este fondo blanco se verían como
                    // recortes con huecos oscuros (hereda el fondo por defecto
                    // del Bitmap, que es negro/transparente según el Config).
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                }
            }
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val mimeType = options.context.contentResolver.getType(data)
            val looksLikePdf = mimeType == "application/pdf" ||
                data.toString().endsWith(".pdf", ignoreCase = true)
            return if (looksLikePdf) PdfThumbnailFetcher(options.context, data) else null
        }
    }

    private companion object {
        // Ancho fijo de renderizado -- de sobra para los tamaños de
        // miniatura usados hoy (44dp en las filas de lista, hasta 72dp de
        // alto en las tarjetas de Favoritos), sin gastar memoria de más
        // renderizando la página a su resolución completa.
        const val THUMBNAIL_WIDTH_PX = 300
    }
}
