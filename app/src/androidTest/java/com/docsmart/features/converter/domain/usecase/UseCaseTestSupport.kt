package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

// Utilidades compartidas por las pruebas instrumentadas de casos de uso (Convertidor,
// Herramientas PDF, repositorios de Biblioteca). Nada escribe en la app real: cada prueba
// trabaja en una carpeta propia dentro de cacheDir de instrumentación y la borra al final.

/** Contexto que redirige filesDir/cacheDir a una carpeta temporal propia (el resto es el real). */
internal class IsolatedContext(
    base: Context,
    val root: File,
) : ContextWrapper(base) {
    private val isolatedFiles = File(root, "files").apply { mkdirs() }
    private val isolatedCache = File(root, "cache").apply { mkdirs() }
    private val prefsNames = mutableSetOf<String>()

    /** Si no es null, reemplaza al ContentResolver real (para simular MediaStore/SAF con cursores propios). */
    var resolver: ContentResolver? = null

    override fun getFilesDir(): File = isolatedFiles

    override fun getCacheDir(): File = isolatedCache

    override fun getContentResolver(): ContentResolver = resolver ?: super.getContentResolver()

    // Preferencias con nombre único por prueba: nunca tocan las preferencias reales de la app.
    override fun getSharedPreferences(
        name: String,
        mode: Int,
    ): SharedPreferences {
        val isolatedName = "r20_${root.name}_$name"
        prefsNames += isolatedName
        return baseContext.getSharedPreferences(isolatedName, Context.MODE_PRIVATE)
    }

    /** Carpeta donde el caso de uso deja sus resultados. */
    fun outputDir(name: String): File = File(isolatedFiles, name)

    /** Archivos de entrada preparados por la prueba (fuera de filesDir/cacheDir aislados). */
    fun inputFile(name: String): File = File(root, "in").apply { mkdirs() }.let { File(it, name) }

    fun cleanUp() {
        prefsNames.forEach { baseContext.deleteSharedPreferences(it) }
        root.deleteRecursively()
    }
}

internal fun newIsolatedContext(prefix: String): IsolatedContext {
    val target = InstrumentationRegistry.getInstrumentation().targetContext
    val root = File(target.cacheDir, "r20_${prefix}_${System.nanoTime()}").apply { mkdirs() }
    return IsolatedContext(target, root)
}

internal fun uriOf(file: File): Uri = Uri.fromFile(file)

/** Escribe una imagen sintética (fondo de color + un recuadro) y devuelve el archivo. */
internal fun writeTestImage(
    file: File,
    width: Int = 120,
    height: Int = 80,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    background: Int = Color.WHITE,
): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(background)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.RED }
        canvas.drawRect(width / 4f, height / 4f, width * 3 / 4f, height * 3 / 4f, paint)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(format, 100, it) }
    } finally {
        bitmap.recycle()
    }
    return file
}

/** PDF real de [pages] páginas creado con android.graphics.pdf.PdfDocument (renderizable con PdfRenderer). */
internal fun writeAndroidPdf(
    file: File,
    pages: Int = 1,
    width: Int = 200,
    height: Int = 300,
): File {
    val document = PdfDocument()
    try {
        val paint = Paint().apply { color = Color.BLUE }
        for (i in 1..pages) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, i).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawRect(20f, 20f, width - 20f, height / 2f, paint)
            document.finishPage(page)
        }
        file.parentFile?.mkdirs()
        file.outputStream().use { document.writeTo(it) }
    } finally {
        document.close()
    }
    return file
}

/** Cantidad de páginas de un PDF, leída con PdfRenderer (falla si el PDF no es válido). */
internal fun renderedPageCount(file: File): Int =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { it.pageCount }
    }

/** Tamaño (ancho, alto) en puntos de la página [index] de un PDF. */
internal fun renderedPageSize(
    file: File,
    index: Int = 0,
): Pair<Int, Int> =
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
            renderer.openPage(index).use { page -> page.width to page.height }
        }
    }
