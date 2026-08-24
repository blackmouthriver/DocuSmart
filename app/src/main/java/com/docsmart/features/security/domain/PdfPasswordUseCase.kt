package com.docsmart.features.security.domain

import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.EncryptionConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.ReaderProperties
import com.itextpdf.kernel.pdf.WriterProperties
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
        try {
            // ── Paso 1: copiar al caché ───────────────────────────────────────
            val cacheFile = File(context.cacheDir, "temp_protect_${System.currentTimeMillis()}.pdf")
            val bytesCopied = context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext PdfPasswordResult.Error(messages.readError)

            Timber.d("PdfPasswordUseCase: caché copiado → ${cacheFile.length()} bytes (copiados=$bytesCopied)")

            if (cacheFile.length() == 0L) {
                cacheFile.delete()
                return@withContext PdfPasswordResult.Error(messages.emptyFile)
            }

            // ── Paso 2: preparar output ───────────────────────────────────────
            val outputDir  = File(context.filesDir, "pdftools").also { it.mkdirs() }
            val safeName   = fileName.replace(" ", "_").replace("/", "_")
            val outputFile = File(outputDir, "${safeName}_protegido.pdf")

            // Eliminar si existe previamente
            if (outputFile.exists()) outputFile.delete()

            val userPass  = password.toByteArray()
            val ownerPass = (password + "_owner").toByteArray()

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

            cacheFile.delete()

            Timber.d("PdfPasswordUseCase: resultado → pages=$pages size=${outputFile.length()}b")

            if (!outputFile.exists() || outputFile.length() < 100L) {
                return@withContext PdfPasswordResult.Error(
                    String.format(messages.protectGenerateError, outputFile.length())
                )
            }

            Timber.d("PdfPasswordUseCase: PDF protegido ✅ → ${outputFile.name}")
            PdfPasswordResult.Success(outputFile, messages.protectSuccess)

        } catch (e: Exception) {
            Timber.e(e, "PdfPasswordUseCase: error protegiendo PDF → ${e.javaClass.simpleName}: ${e.message}")
            PdfPasswordResult.Error(String.format(messages.protectError, e.message ?: ""))
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
        try {
            // ── Paso 1: copiar al caché ───────────────────────────────────────
            val cacheFile = File(context.cacheDir, "temp_remove_${System.currentTimeMillis()}.pdf")
            val bytesCopied = context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext PdfPasswordResult.Error(messages.readError)

            Timber.d("PdfPasswordUseCase: caché copiado → ${cacheFile.length()} bytes (copiados=$bytesCopied)")

            if (cacheFile.length() == 0L) {
                cacheFile.delete()
                return@withContext PdfPasswordResult.Error(messages.emptyFile)
            }

            // ── Paso 2: preparar output ───────────────────────────────────────
            val outputDir  = File(context.filesDir, "pdftools").also { it.mkdirs() }
            val safeName   = fileName.replace(" ", "_").replace("/", "_")
            val outputFile = File(outputDir, "${safeName}_sin_contrasena.pdf")

            if (outputFile.exists()) outputFile.delete()

            // ── Paso 3: desencriptar ──────────────────────────────────────────
            val userPass  = password.toByteArray()
            val reader = try {
                val r = PdfReader(cacheFile.absolutePath, ReaderProperties().setPassword(userPass))
                r.setUnethicalReading(true)
                r.setMemorySavingMode(true)
                r
            } catch (e: Exception) {
                cacheFile.delete()
                Timber.w("PdfPasswordUseCase: contraseña incorrecta → ${e.message}")
                return@withContext PdfPasswordResult.WrongPassword
            }

            Timber.d("PdfPasswordUseCase: desencriptando → output=${outputFile.absolutePath}")

            val writer = PdfWriter(outputFile.absolutePath)
            val doc    = PdfDocument(reader, writer)
            val pages  = doc.numberOfPages
            doc.close()

            cacheFile.delete()

            Timber.d("PdfPasswordUseCase: resultado → pages=$pages size=${outputFile.length()}b")

            if (!outputFile.exists() || outputFile.length() < 100L) {
                return@withContext PdfPasswordResult.Error(
                    String.format(messages.removeGenerateError, outputFile.length())
                )
            }

            Timber.d("PdfPasswordUseCase: contraseña eliminada ✅ → ${outputFile.name}")
            PdfPasswordResult.Success(outputFile, messages.removeSuccess)

        } catch (e: Exception) {
            Timber.e(e, "PdfPasswordUseCase: error quitando contraseña → ${e.javaClass.simpleName}: ${e.message}")
            val msg = e.message?.lowercase() ?: ""
            return@withContext if (
                msg.contains("password") ||
                msg.contains("decrypt")  ||
                msg.contains("bad user")
            ) {
                PdfPasswordResult.WrongPassword
            } else {
                PdfPasswordResult.Error(String.format(messages.removeError, e.message ?: ""))
            }
        }
    }
}
