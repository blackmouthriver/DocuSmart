package com.docsmart.features.pdftools.domain.usecase

import android.content.Context
import android.net.Uri
import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class FormFieldInfo(
    val name        : String,
    val currentValue: String
)

class DetectFormFieldsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DetectFormFieldsUseCase"
    }

    /**
     * RF-PDF-12: lee (sin modificar) los campos de texto del `AcroForm`
     * del PDF, para que la pantalla los muestre como campos editables. Se
     * limita a campos de tipo texto (`PdfName.Tx`) — checkboxes/radio/combo
     * quedan fuera de este alcance, mismo criterio ya aplicado en
     * RF-PDF-10 (solo texto, no imágenes existentes).
     */
    suspend operator fun invoke(pdfUri: Uri): List<FormFieldInfo> = withContext(Dispatchers.IO) {
        val cacheFile = copyUriToCache(pdfUri) ?: return@withContext emptyList()
        try {
            val pdf = PdfDocument(PdfReader(cacheFile))
            val form = PdfAcroForm.getAcroForm(pdf, false)
            val fields = form?.getFormFields()
                ?.filterValues { it.getFormType() == PdfName.Tx }
                ?.map { (name, field) -> FormFieldInfo(name, field.getValueAsString() ?: "") }
                ?: emptyList()
            pdf.close()
            fields
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error detectando campos del formulario")
            emptyList()
        } finally {
            cacheFile.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "detectform_${System.currentTimeMillis()}.pdf")
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
}
