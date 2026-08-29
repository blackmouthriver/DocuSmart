package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.docsmart.features.pdftools.domain.model.PdfToolResult
import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class FillFormMessages(
    val emptyValuesError: String,
    val readError       : String,
    val noFieldsError    : String,
    val generateError    : String,
    val success           : String, // formato: %1$d campos rellenados
    val genericError       : String  // formato: %1$s mensaje de excepción
)

class FillFormUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FillFormUseCase"
    }

    /**
     * RF-PDF-12 (relleno de formularios): aplica los valores escritos por
     * el usuario a los campos de texto del `AcroForm` del PDF
     * (`PdfAcroForm.getFormFields()`/`PdfFormField.setValue()`) y aplana
     * el formulario al final (`form.flattenFields()`) para que los valores
     * queden fijos como texto normal en el resultado, no editables de
     * nuevo — mismo criterio que "rellenar y finalizar" de la mayoría de
     * apps de firma/relleno de formularios.
     */
    suspend operator fun invoke(
        pdfUri        : Uri,
        values        : Map<String, String>,
        outputFileName: String? = null,
        messages      : FillFormMessages
    ): PdfToolResult = withContext(Dispatchers.IO) {
        if (values.isEmpty()) {
            return@withContext PdfToolResult.Error(messages.emptyValuesError)
        }

        var cacheFile: File? = null
        try {
            cacheFile = copyUriToCache(pdfUri)
                ?: return@withContext PdfToolResult.Error(messages.readError)

            val outputFile = createOutputFile(outputFileName ?: "Formulario")
            var filledCount = 0

            PdfDocument(PdfReader(cacheFile), PdfWriter(outputFile)).use { pdf ->
                val form = PdfAcroForm.getAcroForm(pdf, false)
                    ?: return@withContext PdfToolResult.Error(messages.noFieldsError)
                val fields = form.getFormFields()
                values.forEach { (name, value) ->
                    fields[name]?.let { field ->
                        field.setValue(value)
                        filledCount++
                    }
                }
                if (filledCount == 0) {
                    return@withContext PdfToolResult.Error(messages.noFieldsError)
                }
                form.flattenFields()
            }

            if (outputFile.length() == 0L) {
                return@withContext PdfToolResult.Error(messages.generateError)
            }

            Timber.d("$TAG: relleno exitoso — $filledCount campos")

            PdfToolResult.Success(
                outputFile = outputFile,
                message = String.format(messages.success, filledCount)
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error al rellenar formulario")
            PdfToolResult.Error(String.format(messages.genericError, e.message ?: ""), e)
        } finally {
            cacheFile?.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "fillform_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    if (bytes == 0L) return null
                }
            } ?: return null
            file
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error copiando URI al cache")
            null
        }
    }

    private fun createOutputFile(name: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(context.filesDir, "pdftools").apply { mkdirs() }
        return File(dir, "DocuSmart_${name}_$timestamp.pdf")
    }
}
