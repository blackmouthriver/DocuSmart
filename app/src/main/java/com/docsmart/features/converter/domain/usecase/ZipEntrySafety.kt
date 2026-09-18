package com.docsmart.features.converter.domain.usecase

import android.content.Context
import android.net.Uri
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

// Hallazgo real de la revisión general 2026-09-16 (#36): ExcelToHtmlUseCase/
// WordToHtmlUseCase/PptToPdfUseCase/PptToTextUseCase leían cada entrada del
// ZIP interno de un .xlsx/.docx/.pptx con zip.readBytes(), sin ningún límite
// de tamaño -- un archivo malicioso con una relación de compresión extrema
// ("zip bomb": pocos KB comprimidos que decomprimen a varios GB) agota la
// memoria disponible al intentar bufferear la entrada completa. Se lee en
// bloques acotados y se aborta apenas se supera el límite, en vez de recién
// después de haber decomprimido todo.
private const val MAX_ZIP_ENTRY_BYTES = 50L * 1024 * 1024 // 50 MB

/**
 * Reemplazo seguro de [ZipInputStream.readBytes] para la entrada ZIP
 * actualmente posicionada (después de `nextEntry`) -- lee en bloques y
 * lanza [IOException] si el contenido decomprimido supera [maxBytes], en
 * vez de intentar bufferearlo entero primero.
 */
internal fun ZipInputStream.readEntrySafely(maxBytes: Long = MAX_ZIP_ENTRY_BYTES): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0L
    while (true) {
        val read = this.read(chunk)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw IOException("Entrada ZIP excede el límite de seguridad de $maxBytes bytes")
        }
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}

// Hallazgo real de la revisión de seguridad adversarial de este mismo lote
// (2026-09-16): PptToPdfUseCase.extractSlideText() bufferea el .pptx
// COMPLETO en memoria con InputStream.readBytes() (workaround deliberado de
// un bug real de SAF, ver el comentario ahí) ANTES de envolverlo en
// ZipInputStream y recién ahí aplicar el límite de readEntrySafely() por
// entrada -- ese límite no protege nada si el archivo comprimido de entrada
// ya es gigante, porque el agotamiento de memoria ocurre en este
// readBytes() mismo, antes de llegar al ZIP. Mismo criterio que
// readEntrySafely(): leer en bloques acotados y abortar apenas se supera el
// límite, en vez de bufferear todo primero.
internal fun InputStream.readBoundedBytes(maxBytes: Long = MAX_ZIP_ENTRY_BYTES): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0L
    while (true) {
        val read = this.read(chunk)
        if (read == -1) break
        total += read
        if (total > maxBytes) {
            throw IOException("Archivo excede el límite de seguridad de $maxBytes bytes")
        }
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}

// Hallazgo real de la revisión general 2026-09-16 (#38): ConversionType
// declara .xls/.ppt (formato legado OLE2, pre-Office 2007) como orígenes
// soportados, pero ExcelToHtmlUseCase/PptToPdfUseCase/PptToTextUseCase
// parsean el ZIP interno de OOXML a mano -- un .xls/.ppt real ni siquiera
// es un ZIP, así que ZipInputStream simplemente no encuentra ninguna
// entrada esperada y el use case termina con un mensaje engañoso
// ("hoja vacía"/"sin texto") en vez de avisar que el formato no está
// soportado en los hechos.
private val OLE2_SIGNATURE = byteArrayOf(
    0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
    0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
)

internal fun ByteArray.isLegacyOle2(): Boolean =
    size >= OLE2_SIGNATURE.size && copyOfRange(0, OLE2_SIGNATURE.size).contentEquals(OLE2_SIGNATURE)

/**
 * Abre [uri] una vez solo para leer los primeros bytes -- se usa ANTES de
 * la lectura real (que abre su propio stream de nuevo) en vez de espiar el
 * mismo stream con mark/reset, para no depender de que el proveedor de
 * contenido soporte esa operación.
 */
internal fun isLegacyOle2Uri(context: Context, uri: Uri): Boolean {
    val header = ByteArray(OLE2_SIGNATURE.size)
    val read = context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: return false
    return read == OLE2_SIGNATURE.size && header.isLegacyOle2()
}

// Hallazgo real de la auditoría general 2026-09-17/18 (décima ronda, Alta
// -- C1): un .docx/.xlsx/.pptx protegido con contraseña de Office (no
// confundir con el PIN de Carpeta Segura de la app, que es un candado
// distinto) se guarda como un contenedor OLE2 -- MISMA firma binaria que
// un .doc/.xls/.ppt legado real de Office 97-2003 (ver isLegacyOle2Uri()
// arriba). Antes de este fix, cualquier archivo protegido caía en esa
// misma rama: Excel→HTML/PPT→PDF/PPT→TXT mostraban "guardalo como .xlsx"
// sobre un archivo que YA es .xlsx, y las 3 conversiones de Word
// terminaban lanzando una excepción cruda de Apache POI al no encontrar
// el stream "WordDocument" esperado. La estructura estándar
// MS-OFFCRYPTO envuelve el paquete real cifrado en un stream llamado
// "EncryptedPackage" -- distinguirlo alcanza para dar el mensaje
// correcto sin necesitar la contraseña (no se intenta descifrar nada).
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun isPasswordProtectedOfficeUri(context: Context, uri: Uri): Boolean {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            POIFSFileSystem(input).use { fs ->
                fs.root.any { entry -> entry.name.equals("EncryptedPackage", ignoreCase = true) }
            }
        } ?: false
    } catch (e: Exception) {
        // No es un OLE2 válido, o algún otro problema de lectura -- lo que
        // haya llamado a esto ya tiene su propio manejo de error para esos
        // casos, acá solo interesa la pregunta puntual "¿está cifrado?".
        false
    }
}
