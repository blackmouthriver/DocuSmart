package com.docsmart.core.pdf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Tamaño de una página en puntos PDF (ya refleja la rotación, igual que [PdfPageBitmap]). */
data class PdfPageSize(
    val widthPts: Float,
    val heightPts: Float,
)

/**
 * PDF abierto UNA vez con páginas renderizadas **bajo demanda**.
 *
 * Reemplaza al render eager de [renderPdfPagesToBitmaps] en el Visor: aquel rasterizaba TODAS las
 * páginas antes de mostrar la primera y las mantenía en memoria. Medido con un libro de 2 332 páginas
 * (252x331 pt): 21,7 s y 2 968 MB de bitmaps en un teléfono de 7,6 GB; 74,6 s y los mismos 2 968 MB en
 * uno de 3,9 GB. Aquí se abre el renderer, se lee solo el tamaño de la primera página y cada página se
 * renderiza cuando entra en pantalla, con una caché acotada por bytes ([LruByteCache]).
 *
 * `PdfRenderer` no admite dos páginas abiertas a la vez ni acceso concurrente: todo acceso se
 * serializa con un [Mutex]. Leer el tamaño de todas las páginas por adelantado costaba 7,6 s (Edge)
 * y 25 s (E22) para ese libro, por eso solo se lee la primera y el resto se corrige al renderizar.
 */
class PdfPageSource private constructor(
    private val sourceFile: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageCount: Int,
    /** Estimación para las páginas aún no renderizadas (la mayoría de los PDF tienen un solo formato). */
    val firstPageSize: PdfPageSize,
    cacheBudgetBytes: Long,
) : Closeable {
    private val mutex = Mutex()
    private val cache = LruByteCache<Long, PdfPageBitmap>(cacheBudgetBytes) { it.bitmap.allocationByteCount.toLong() }

    @Volatile
    private var closed = false

    /** Bytes de bitmaps que mantiene ahora la caché (para pruebas y diagnóstico). */
    val cachedBytes: Long get() = cache.sizeBytes

    /**
     * Página [index] renderizada a [targetWidthPx] de ancho (alto proporcional), o `null` si la fuente
     * ya se cerró o el render falló. Si estaba en caché no toca el renderer.
     */
    suspend fun render(
        index: Int,
        targetWidthPx: Int,
    ): PdfPageBitmap? {
        if (index !in 0 until pageCount || targetWidthPx <= 0) return null
        val key = cacheKey(index, targetWidthPx)
        return cache.get(key) ?: mutex.withLock {
            // Otro render de la misma página pudo terminar mientras se esperaba el turno.
            val cached = cache.get(key)
            when {
                cached != null -> cached
                closed -> null
                else -> withContext(Dispatchers.IO) { renderLocked(index, targetWidthPx, key) }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun renderLocked(
        index: Int,
        targetWidthPx: Int,
        key: Long,
    ): PdfPageBitmap? =
        try {
            renderer.openPage(index).use { page ->
                val size = lazyPageBitmapSize(page.width, page.height, targetWidthPx)
                val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                PdfPageBitmap(bitmap, page.width.toFloat(), page.height.toFloat()).also {
                    // Si esta página ya estaba a otro ancho (rotación de pantalla), el bitmap viejo sobra.
                    cache.removeWhere { other -> pageOf(other) == index && other != key }
                    cache.put(key, it)
                }
            }
        } catch (e: Exception) {
            // Solo el tipo: el mensaje de PdfRenderer puede incluir rutas (mismo criterio que el resto del módulo).
            Timber.e("PdfPageSource: no se pudo renderizar la página ${index + 1} (${e.javaClass.simpleName})")
            null
        }

    /** Libera el renderer, el descriptor, la copia en caché y los bitmaps. Idempotente. */
    override fun close() {
        if (closed) return
        closed = true
        // No se bloquea el hilo que llama (suele ser el principal): la liberación espera su turno en el mutex.
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
                sourceFile.delete()
                cache.clear()
            }
        }
    }

    companion object {
        private const val CACHE_PREFIX = "viewer_src"
        private const val STALE_AFTER_MS = 60 * 60 * 1000L

        /**
         * Abre [uri] (copia el PDF al caché de la app: `PdfRenderer` exige un descriptor de archivo real).
         * Lanza si el PDF no se puede leer o no tiene páginas, igual que antes el render eager.
         */
        @Suppress("TooGenericExceptionCaught") // se vuelve a lanzar tras liberar lo abierto
        suspend fun open(
            uri: Uri,
            context: Context,
        ): PdfPageSource =
            withContext(Dispatchers.IO) {
                purgeStaleCopies(context)
                val copy = File(context.cacheDir, "${CACHE_PREFIX}_${System.nanoTime()}.pdf")
                check(copyPdfUriToCache(uri, context, copy)) { "no se pudo copiar el PDF al caché" }
                var descriptor: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    descriptor = ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(descriptor)
                    check(renderer.pageCount > 0) { "el PDF no tiene páginas" }
                    val first = renderer.openPage(0).use { PdfPageSize(it.width.toFloat(), it.height.toFloat()) }
                    PdfPageSource(copy, descriptor, renderer, renderer.pageCount, first, cacheBudgetBytes(context))
                } catch (e: Throwable) {
                    runCatching { renderer?.close() }
                    runCatching { descriptor?.close() }
                    copy.delete()
                    throw e
                }
            }

        /** ~1/3 de la clase de memoria de la app, entre 48 y 128 MB. */
        internal fun cacheBudgetBytes(context: Context): Long {
            val memoryClassMb = context.getSystemService(ActivityManager::class.java)?.memoryClass ?: 192
            return (memoryClassMb / 3).toLong().coerceIn(MIN_CACHE_MB, MAX_CACHE_MB) * 1024L * 1024L
        }

        // Copias de una sesión anterior que murió sin cerrar (proceso matado) no deben acumularse en cacheDir.
        private fun purgeStaleCopies(context: Context) {
            val limit = System.currentTimeMillis() - STALE_AFTER_MS
            context.cacheDir
                .listFiles { f -> f.name.startsWith(CACHE_PREFIX) && f.lastModified() < limit }
                ?.forEach { it.delete() }
        }

        private const val MIN_CACHE_MB = 48L
        private const val MAX_CACHE_MB = 128L

        private fun cacheKey(
            index: Int,
            widthPx: Int,
        ): Long = (index.toLong() shl 32) or widthPx.toLong()

        private fun pageOf(key: Long): Int = (key shr 32).toInt()
    }
}

/**
 * Tamaño del bitmap de una página de [pageWidth] x [pageHeight] puntos para mostrarla a [targetWidthPx] de
 * ancho, con alto proporcional. Se limita a [VIEWER_PAGE_MAX_PIXELS] (mismo tope que el render eager: Android
 * aborta al dibujar un bitmap > 100 MB, ver `viewerPageBitmapSize`). Función pura, testeable en JVM.
 */
internal fun lazyPageBitmapSize(
    pageWidth: Int,
    pageHeight: Int,
    targetWidthPx: Int,
): PageBitmapSize {
    val w = pageWidth.coerceAtLeast(1)
    val h = pageHeight.coerceAtLeast(1)
    var width = targetWidthPx.coerceAtLeast(1).toLong()
    var height = (width * h.toDouble() / w).roundToInt().coerceAtLeast(1).toLong()
    val pixels = width * height
    if (pixels > VIEWER_PAGE_MAX_PIXELS) {
        val factor = sqrt(VIEWER_PAGE_MAX_PIXELS.toDouble() / pixels)
        width = (width * factor).toLong().coerceAtLeast(1)
        height = (height * factor).toLong().coerceAtLeast(1)
    }
    return PageBitmapSize(width.toInt(), height.toInt())
}
