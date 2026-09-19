package com.docsmart.features.scanner.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * RF-SCAN-06/RF-SCAN-07: ajuste de brillo/contraste y reescalado sobre una
 * imagen ya escaneada. Google ML Kit Document Scanner no expone ninguno de
 * los dos controles (ver RNF-SCAN-01 en scanner.md) -- se resuelve como un
 * paso de edición propio DESPUÉS de recibir el resultado del escáner, sin
 * tocar la captura en sí.
 */
class ScanImageEditor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // Hallazgo real de la auditoría general 2026-09-17 (B9): cada ajuste
        // de brillo/contraste/escala escribía un archivo nuevo en
        // cacheDir/scanner_edits/ sin borrar el anterior, acumulando para
        // siempre durante la sesión. Se registran acá (no en el Composable,
        // que no tiene forma segura de mapear un content:// de vuelta a un
        // File) los URIs que ESTA instancia creó -- el llamador solo puede
        // borrar un URI si pasó por acá, así que nunca se arriesga a borrar el
        // URI original del escaneo (dueño de ML Kit) ni el de otra página.
        // `ScanImageEditor` vive tanto como `ScanImageEditorViewModel`
        // (@HiltViewModel, una instancia por visita a ScanResultScreen), así
        // que el mapa cubre exactamente la sesión de edición del usuario.
        private val ownedCacheFiles = mutableMapOf<Uri, File>()

        @Suppress("TooGenericExceptionCaught")
        suspend fun applyAdjustments(
            sourceUri: Uri,
            brightness: Int,
            contrast: Int,
            scalePercent: Int,
        ): Uri? =
            withContext(Dispatchers.IO) {
                var original: Bitmap? = null
                var scaled: Bitmap? = null
                var adjusted: Bitmap? = null
                try {
                    original = loadBitmap(sourceUri) ?: return@withContext null
                    scaled = scaleBitmap(original, scalePercent)
                    adjusted = applyColorAdjustments(scaled, brightness, contrast)

                    val outputFile = writeToCache(adjusted)

                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
                    ownedCacheFiles[uri] = outputFile
                    uri
                } catch (e: OutOfMemoryError) {
                    // H4: en Kotlin/JVM, Error no es subclase de Exception -- un OOM
                    // real decodificando/escalando el bitmap (dispositivo con poca
                    // RAM) no lo capturaba el catch genérico de abajo y tumbaba la
                    // app en vez de devolver null con gracia.
                    Timber.e(e, "Sin memoria aplicando ajustes a la imagen escaneada")
                    null
                } catch (e: Exception) {
                    Timber.e(e, "Error aplicando ajustes a la imagen escaneada")
                    null
                } finally {
                    // Revisión adversarial de seguridad (ronda 11): el catch de OOM
                    // devolvía null sin reciclar los bitmaps ya asignados hasta ese
                    // punto -- justo cuando más urge liberar memoria nativa de
                    // inmediato, antes del próximo intento del usuario. Recicla en
                    // TODOS los caminos (éxito o error), sin doble-reciclar cuando
                    // dos referencias apuntan al mismo bitmap (scalePercent=100 o
                    // brightness=contrast=0 devuelven el mismo objeto sin copiar).
                    val a = adjusted
                    val s = scaled
                    val o = original
                    if (a != null && a !== s) a.recycle()
                    if (s != null && s !== o) s.recycle()
                    o?.recycle()
                }
            }

        // B9: el llamador pasa el URI que este edit está reemplazando -- si
        // esta instancia lo reconoce como propio (lo creó ella misma vía
        // writeToCache), borra el archivo de respaldo, ya inalcanzable para
        // cualquier otra página o el URI original del escaneo. No-op seguro si
        // el URI no es reconocido.
        fun deleteCachedFile(uri: Uri) {
            ownedCacheFiles.remove(uri)?.delete()
        }

        /**
         * HU-41 (backlog UX 2026-08-30/09-14): aplica un [ScanColorMode] a una
         * página ya escaneada. `COLOR` no reprocesa nada y devuelve la misma
         * URI de entrada -- AC3, cero regresión para quien no toca esta
         * opción, sin gastar IO de más.
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun applyColorMode(
            sourceUri: Uri,
            mode: ScanColorMode,
        ): Uri? =
            withContext(Dispatchers.IO) {
                if (mode == ScanColorMode.COLOR) return@withContext sourceUri
                var original: Bitmap? = null
                var filtered: Bitmap? = null
                try {
                    original = loadBitmap(sourceUri) ?: return@withContext null
                    filtered = applyMatrix(original, buildColorModeMatrix(mode))

                    val outputFile = writeToCache(filtered)

                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
                    // Hallazgo real de la auditoría general 2026-09-17 (quinta
                    // pasada): a diferencia de applyAdjustments(), este método no
                    // registraba su archivo en ownedCacheFiles -- deleteCachedFile()
                    // nunca podía encontrarlo, así que cada cambio de modo de color
                    // quedaba huérfano para siempre en cacheDir/scanner_edits/.
                    ownedCacheFiles[uri] = outputFile
                    uri
                } catch (e: OutOfMemoryError) {
                    // H4: mismo motivo que en applyAdjustments() -- Error no es
                    // subclase de Exception, así que un OOM real acá también
                    // necesita su propio catch para no tumbar la app.
                    Timber.e(e, "Sin memoria aplicando modo de color a la imagen escaneada")
                    null
                } catch (e: Exception) {
                    Timber.e(e, "Error aplicando modo de color a la imagen escaneada")
                    null
                } finally {
                    // Revisión adversarial de seguridad (ronda 11): recicla en TODOS
                    // los caminos, no solo el de éxito -- ver applyAdjustments().
                    filtered?.recycle()
                    original?.recycle()
                }
            }

        private fun loadBitmap(uri: Uri): Bitmap? = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

        private fun scaleBitmap(
            bitmap: Bitmap,
            scalePercent: Int,
        ): Bitmap {
            if (scalePercent >= SCALE_FULL_PERCENT) return bitmap
            val (width, height) = scaledDimensions(bitmap.width, bitmap.height, scalePercent)
            return Bitmap.createScaledBitmap(bitmap, width, height, true)
        }

        private fun applyColorAdjustments(
            bitmap: Bitmap,
            brightness: Int,
            contrast: Int,
        ): Bitmap {
            if (brightness == 0 && contrast == 0) return bitmap
            return applyMatrix(bitmap, buildColorMatrix(brightness, contrast))
        }

        private fun applyMatrix(
            bitmap: Bitmap,
            matrix: FloatArray,
        ): Bitmap {
            val result =
                Bitmap.createBitmap(
                    bitmap.width,
                    bitmap.height,
                    bitmap.config ?: Bitmap.Config.ARGB_8888,
                )
            val canvas = Canvas(result)
            val paint =
                Paint().apply {
                    colorFilter = ColorMatrixColorFilter(matrix)
                }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            return result
        }

        private fun writeToCache(bitmap: Bitmap): File {
            val dir = File(context.cacheDir, "scanner_edits").apply { mkdirs() }
            val file = File(dir, "edit_${System.currentTimeMillis()}.jpg")
            try {
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            } catch (e: OutOfMemoryError) {
                // Revisión adversarial de seguridad (ronda 11): bitmap.compress()
                // es la asignación de buffers más grande del pipeline -- un OOM
                // justo acá dejaba un archivo vacío/truncado huérfano en
                // cacheDir/scanner_edits/ para siempre, ya que nunca llegaba a
                // registrarse en ownedCacheFiles.
                file.delete()
                throw e
            }
            return file
        }

        companion object {
            private const val SCALE_FULL_PERCENT = 100
            private const val JPEG_QUALITY = 92
        }
    }

/**
 * Nuevo ancho/alto tras aplicar un porcentaje de reescalado (RF-SCAN-07).
 * Función pura, sin `Bitmap` real, para poder testearla sin Robolectric.
 */
internal fun scaledDimensions(
    width: Int,
    height: Int,
    scalePercent: Int,
): Pair<Int, Int> {
    val newWidth = (width * scalePercent / 100f).toInt().coerceAtLeast(1)
    val newHeight = (height * scalePercent / 100f).toInt().coerceAtLeast(1)
    return newWidth to newHeight
}

/**
 * Matriz de color 4x5 (mismo formato que `android.graphics.ColorMatrix` y
 * `androidx.compose.ui.graphics.ColorMatrix` -- se reutiliza tal cual para
 * la vista previa en Compose y para el bake final sobre el bitmap real) que
 * aplica brillo (-100..100, desplazamiento aditivo por canal) y contraste
 * (-100..100, factor de escala anclado al gris medio) sobre una imagen.
 * Función pura -- sin ninguna clase de `android.graphics`, para poder
 * testearla como JVM unit test normal.
 */
internal fun buildColorMatrix(
    brightness: Int,
    contrast: Int,
): FloatArray {
    val contrastFactor = 1f + contrast / 100f
    val brightnessOffset = brightness * 2.55f
    val translate = (1f - contrastFactor) * 128f + brightnessOffset
    return floatArrayOf(
        contrastFactor,
        0f,
        0f,
        0f,
        translate,
        0f,
        contrastFactor,
        0f,
        0f,
        translate,
        0f,
        0f,
        contrastFactor,
        0f,
        translate,
        0f,
        0f,
        0f,
        1f,
        0f,
    )
}
