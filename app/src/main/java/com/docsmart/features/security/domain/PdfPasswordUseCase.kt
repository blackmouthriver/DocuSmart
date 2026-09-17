package com.docsmart.features.security.domain

import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.EncryptionConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.ReaderProperties
import com.itextpdf.kernel.pdf.WriterProperties
import com.docsmart.core.util.sanitizeOutputFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class PdfPasswordResult {
    data class Success(val outputFile: File, val message: String) : PdfPasswordResult()
    data class Error(val message: String)                         : PdfPasswordResult()
    object WrongPassword                                          : PdfPasswordResult()
}

// Mensajes localizados, resueltos en la capa de presentación (stringResource)
// y pasados hacia abajo — el UseCase no tiene acceso a Context de recursos.
data class PdfPasswordMessages(
    val readError            : String,
    val emptyFile            : String,
    val protectSuccess       : String,
    val protectGenerateError : String, // formato: %1$d bytes
    val protectError         : String, // formato: %1$s mensaje de excepción
    val removeSuccess        : String,
    val removeGenerateError  : String, // formato: %1$d bytes
    val removeError          : String  // formato: %1$s mensaje de excepción
)

@Singleton
class PdfPasswordUseCase @Inject constructor() {

    // ── Proteger PDF con contraseña ───────────────────────────────────────────
    suspend fun protect(
        context : Context,
        uri     : Uri,
        password: String,
        fileName: String,
        messages: PdfPasswordMessages
    ): PdfPasswordResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            // ── Paso 1: copiar al caché ───────────────────────────────────────
            cacheFile = copyToCache(context, uri, "protect")
                ?: return@withContext PdfPasswordResult.Error(messages.readError)
            if (cacheFile.length() == 0L) {
                return@withContext PdfPasswordResult.Error(messages.emptyFile)
            }

            // ── Paso 2: preparar output ───────────────────────────────────────
            val outputDir  = File(context.filesDir, "pdftools").also { it.mkdirs() }
            // Hallazgo real de la auditoría general 2026-09-17 (B1): saneo ad
            // hoc (solo espacio y "/") en vez de sanitizeOutputFileName(), que
            // ya centraliza el fix de path traversal de la revisión de
            // seguridad 2026-09-16 (ej. no cubría "\" ni "..").
            val safeName   = sanitizeOutputFileName(fileName)
            val outputFile = File(outputDir, "${safeName}_protegido.pdf")

            // Eliminar si existe previamente
            if (outputFile.exists()) outputFile.delete()

            // Bug real de seguridad corregido 2026-09-08: la contraseña de
            // propietario (la que controla permisos de impresión/copia en
            // lectores externos, ver ALLOW_PRINTING/ALLOW_COPY más abajo)
            // se derivaba de forma predecible como "contraseña + _owner" --
            // cualquiera que conociera esa convención podía calcularla y
            // saltarse esos permisos sin conocer la contraseña real. Esta
            // app siempre desbloquea con la contraseña de usuario
            // (`removePassword` de abajo usa solo esa), nunca con la de
            // propietario, así que no hace falta poder reconstruirla --
            // se genera aleatoria e independiente.
            val userPass  = password.toByteArray()
            val ownerPass = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }

            val writerProps = WriterProperties().setStandardEncryption(
                userPass,
                ownerPass,
                EncryptionConstants.ALLOW_PRINTING or EncryptionConstants.ALLOW_COPY,
                EncryptionConstants.ENCRYPTION_AES_128
            )

            // ── Paso 3: encriptar ─────────────────────────────────────────────
            Timber.d("PdfPasswordUseCase: encriptando → input=${cacheFile.length()}b output=${outputFile.absolutePath}")

            val reader = PdfReader(cacheFile.absolutePath)
            reader.setMemorySavingMode(true)
            val writer = PdfWriter(outputFile.absolutePath, writerProps)
            val doc    = PdfDocument(reader, writer)
            val pages  = doc.numberOfPages
            doc.close()

            Timber.d("PdfPasswordUseCase: resultado → pages=$pages size=${outputFile.length()}b")

            if (!outputFile.exists() || outputFile.length() < 100L) {
                return@withContext PdfPasswordResult.Error(
                    String.format(messages.protectGenerateError, outputFile.length())
                )
            }

            Timber.d("PdfPasswordUseCase: PDF protegido ✅ → ${outputFile.name}")
            PdfPasswordResult.Success(outputFile, messages.protectSuccess)

        } catch (e: Exception) {
            // Hallazgo real de la revisión general 2026-09-16 (cuarta
            // pasada): IOException/FileNotFoundException reales traen la
            // ruta absoluta completa en su propio .message (ej. disco
            // lleno, permiso denegado) -- CrashlyticsTree reenvía a
            // Firebase Crashlytics tanto el mensaje de este log como el
            // Throwable pasado, subiendo esa ruta a un servidor de Google.
            // Se loguea solo el tipo de excepción, no el mensaje real.
            Timber.e(redactedForLog(e), "PdfPasswordUseCase: error protegiendo PDF → ${e.javaClass.simpleName}")
            PdfPasswordResult.Error(String.format(messages.protectError, e.message ?: ""))
        } finally {
            // Bug real encontrado 2026-09-14 (repaso general): mismo patrón
            // de fuga ya corregido antes en removePassword() -- cacheFile
            // solo se borraba en el camino feliz, una excepción al encriptar
            // lo dejaba huérfano en cacheDir para siempre.
            cacheFile?.delete()
        }
    }

    // ── Quitar contraseña de PDF ──────────────────────────────────────────────
    suspend fun removePassword(
        context : Context,
        uri     : Uri,
        password: String,
        fileName: String,
        messages: PdfPasswordMessages
    ): PdfPasswordResult = withContext(Dispatchers.IO) {
        var cacheFile: File? = null
        try {
            // ── Paso 1: copiar al caché ───────────────────────────────────────
            cacheFile = copyToCache(context, uri, "remove")
                ?: return@withContext PdfPasswordResult.Error(messages.readError)
            if (cacheFile.length() == 0L) {
                return@withContext PdfPasswordResult.Error(messages.emptyFile)
            }

            // ── Paso 2: preparar output ───────────────────────────────────────
            val outputDir  = File(context.filesDir, "pdftools").also { it.mkdirs() }
            // Hallazgo real de la auditoría general 2026-09-17 (B1): mismo
            // saneo ad hoc que protect(), ver el comentario ahí.
            val safeName   = sanitizeOutputFileName(fileName)
            val outputFile = File(outputDir, "${safeName}_sin_contrasena.pdf")

            if (outputFile.exists()) outputFile.delete()

            // ── Paso 3: desencriptar ──────────────────────────────────────────
            // Bug real de seguridad encontrado 2026-09-14 (repaso general):
            // antes esto se envolvía en su propio try/catch (openReaderOrNull)
            // que atrapaba CUALQUIER excepción -- PDF corrupto, un archivo
            // que ni siquiera es un PDF -- y la reportaba siempre como
            // "contraseña incorrecta". Un usuario con la contraseña correcta
            // pero un archivo dañado reintentaba la misma contraseña
            // indefinidamente sin ver nunca el error real. Ahora la
            // excepción se deja propagar hasta el catch de abajo, que sí
            // distingue (classifyRemoveError, ya existía pero nunca se
            // alcanzaba desde acá) entre "contraseña incorrecta" real y
            // cualquier otro error.
            val reader = PdfReader(
                cacheFile.absolutePath, ReaderProperties().setPassword(password.toByteArray())
            ).apply {
                setUnethicalReading(true)
                setMemorySavingMode(true)
            }

            Timber.d("PdfPasswordUseCase: desencriptando → output=${outputFile.absolutePath}")

            val writer = PdfWriter(outputFile.absolutePath)
            val doc    = PdfDocument(reader, writer)
            val pages  = doc.numberOfPages
            doc.close()

            Timber.d("PdfPasswordUseCase: resultado → pages=$pages size=${outputFile.length()}b")

            if (!outputFile.exists() || outputFile.length() < 100L) {
                return@withContext PdfPasswordResult.Error(
                    String.format(messages.removeGenerateError, outputFile.length())
                )
            }

            Timber.d("PdfPasswordUseCase: contraseña eliminada ✅ → ${outputFile.name}")
            PdfPasswordResult.Success(outputFile, messages.removeSuccess)

        } catch (e: Exception) {
            // Ver el comentario equivalente en protectPdf() más arriba.
            Timber.e(redactedForLog(e), "PdfPasswordUseCase: error quitando contraseña → ${e.javaClass.simpleName}")
            classifyRemoveError(e, messages)
        } finally {
            // Bug real encontrado 2026-09-14: cacheFile solo se borraba en
            // las ramas explícitas de éxito/error temprano -- una excepción
            // real al leer/desencriptar (justo el caso más común de este
            // flujo) lo dejaba huérfano en cacheDir para siempre.
            cacheFile?.delete()
        }
    }

    private fun copyToCache(context: Context, uri: Uri, prefix: String): File? {
        val cacheFile = File(context.cacheDir, "temp_${prefix}_${System.currentTimeMillis()}.pdf")
        val bytesCopied = context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        Timber.d("PdfPasswordUseCase: caché copiado → ${cacheFile.length()} bytes (copiados=$bytesCopied)")
        return cacheFile
    }

    // Ver el comentario de redactedForLog() en el catch de protectPdf() --
    // evita que CrashlyticsTree reenvíe a la nube el .message real de una
    // IOException/FileNotFoundException, que trae la ruta absoluta completa
    // del PDF.
    private fun redactedForLog(e: Exception) = RuntimeException("PdfPasswordUseCase: ${e.javaClass.simpleName}")

    private fun classifyRemoveError(e: Exception, messages: PdfPasswordMessages): PdfPasswordResult {
        val msg = e.message?.lowercase() ?: ""
        return if (msg.contains("password") || msg.contains("decrypt") || msg.contains("bad user")) {
            PdfPasswordResult.WrongPassword
        } else {
            PdfPasswordResult.Error(String.format(messages.removeError, e.message ?: ""))
        }
    }
}
