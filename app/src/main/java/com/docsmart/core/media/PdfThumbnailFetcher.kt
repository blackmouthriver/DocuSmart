package com.docsmart.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import java.io.File
import kotlin.math.roundToInt

/**
 * Miniatura de la primera página de un PDF para las listas de documentos
 * (Biblioteca/Favoritos/Recientes -- backlog UX #25, pedido explícito del
 * usuario 2026-09-06: "mostrar una miniatura visual del contenido" en vez
 * de solo el ícono/color por tipo). Coil 2.7.0 (ya en el proyecto, ver
 * ConverterScreen.kt/ScanResultScreen.kt) trae decodificadores para
 * imágenes normales (JPG/PNG/WebP) pero no para PDF -- Android no tiene un
 * `BitmapFactory` para ese formato, solo [PdfRenderer], que entrega un
 * `Bitmap` ya compuesto en vez de un stream de bytes de imagen.
 *
 * Bug real corregido 2026-09-06 (encontrado al recrear un PDF de prueba y
 * ver que la miniatura seguía cayendo al ícono genérico): Coil mapea
 * internamente cualquier `Uri` de esquema `file://` a un `java.io.File`
 * (`FileUriMapper`, built-in) **antes** de resolver el `Fetcher` -- así que
 * un `Fetcher.Factory<Uri>` nunca llega a evaluarse para los documentos
 * generados por la app (`DocumentUiModel.toContentUri()` devuelve
 * `Uri.fromFile(...)` para esos). Solo los `content://` de MediaStore
 * llegan como `Uri` de verdad al Fetcher. Por eso hacen falta DOS
 * factories -- una para cada tipo de dato -- registradas ambas en
 * `DocuSmartApplication`.
 */
class PdfThumbnailFetcher(
    private val context: Context,
    private val openDescriptor: () -> ParcelFileDescriptor,
) : Fetcher {
    override suspend fun fetch(): FetchResult =
        openDescriptor().use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page ->
                    val scale = THUMBNAIL_WIDTH_PX.toFloat() / page.width
                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap =
                        Bitmap.createBitmap(
                            THUMBNAIL_WIDTH_PX,
                            height,
                            Bitmap.Config.ARGB_8888,
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
                        dataSource = DataSource.DISK,
                    )
                }
            }
        }

    /** Para `content://` reales (MediaStore, SAF) -- Coil los deja pasar como `Uri`. */
    class UriFactory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val mimeType = options.context.contentResolver.getType(data)
            if (!looksLikePdf(mimeType, data.toString())) return null
            return PdfThumbnailFetcher(options.context) {
                options.context.contentResolver.openFileDescriptor(data, "r")
                    ?: error("No se pudo abrir el PDF para miniatura: $data")
            }
        }
    }

    /** Para `file://` de la app (`converted/`/`pdftools/`) -- Coil los mapea a `File` antes de llegar acá. */
    class FileFactory : Fetcher.Factory<File> {
        override fun create(
            data: File,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            if (!looksLikePdf(mimeType = null, dataAsString = data.name)) return null
            return PdfThumbnailFetcher(options.context) {
                ParcelFileDescriptor.open(data, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
    }

    private companion object {
        // Ancho fijo de renderizado -- de sobra para los tamaños de
        // miniatura usados hoy (44dp en las filas de lista, hasta 72dp de
        // alto en las tarjetas de Favoritos), sin gastar memoria de más
        // renderizando la página a su resolución completa.
        const val THUMBNAIL_WIDTH_PX = 300

        fun looksLikePdf(
            mimeType: String?,
            dataAsString: String,
        ): Boolean = mimeType == "application/pdf" || dataAsString.endsWith(".pdf", ignoreCase = true)
    }
}
