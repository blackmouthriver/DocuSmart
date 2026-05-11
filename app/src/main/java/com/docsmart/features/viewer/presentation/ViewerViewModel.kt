package com.docsmart.features.viewer.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

data class ViewerUiState(
    val document: DocumentUiModel? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val showControls: Boolean = true,
    val fileUri: Uri? = null,
    val mimeType: String? = null
)

@HiltViewModel
class ViewerViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val TAG = "ViewerViewModel"
    }

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    fun loadDocument(documentId: String, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val isRealUri = documentId.startsWith("content://") ||
                        documentId.startsWith("file://") ||
                        documentId.startsWith("content%3A") ||
                        documentId.startsWith("/")

                if (isRealUri) {
                    loadFromUri(documentId, context)
                } else {
                    loadFromMock(documentId)
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: error inesperado cargando documento")
                _uiState.update {
                    it.copy(isLoading = false, error = "Error al cargar el documento")
                }
            }
        }
    }

    // ── CORRECCIÓN BUG-02: loadFromUri recibe context para resolver nombre real ──
    private suspend fun loadFromUri(documentId: String, context: Context) {
        withContext(Dispatchers.IO) {
            val uriString = when {
                documentId.startsWith("content%3A") -> Uri.decode(documentId)
                documentId.startsWith("/")          -> "file://$documentId"
                else                                -> documentId
            }

            val uri = Uri.parse(uriString)

            val mimeType: String = context.contentResolver.getType(uri)
                ?: resolveMimeType(uriString)
                ?: "application/octet-stream"

            // Usar resolveFileName CON context para obtener nombre real
            val fileName = resolveFileName(uriString, context)

            Timber.d("$TAG: uri=$uriString mimeType=$mimeType fileName=$fileName")

            val documentType = when {
                mimeType.contains("image")                          -> DocumentType.IMAGE
                mimeType.contains("pdf")                           -> DocumentType.PDF
                mimeType.contains("word") || mimeType.contains("msword") -> DocumentType.WORD
                mimeType.contains("excel") || mimeType.contains("sheet") -> DocumentType.EXCEL
                mimeType.contains("text")                          -> DocumentType.TEXT
                else                                               -> DocumentType.PDF
            }

            val document = DocumentUiModel(
                id         = uriString,
                name       = fileName,
                type       = documentType,
                size       = "",
                date       = "",
                isFavorite = false
            )

            _uiState.update { state ->
                state.copy(
                    document  = document,
                    fileUri   = uri,
                    mimeType  = mimeType,
                    isLoading = false,
                    error     = null
                )
            }
        }
    }

    private fun loadFromMock(id: String) {
        val mockDocument = getMockDocument(id)
        _uiState.update { state ->
            state.copy(
                document    = mockDocument,
                isFavorite  = mockDocument?.isFavorite ?: false,
                isLoading   = false,
                error       = if (mockDocument == null) "Documento no encontrado" else null
            )
        }
    }

    // ── Resuelve nombre real usando ContentResolver ──
    private fun resolveFileName(uriString: String, context: Context): String {
        return try {
            val uri = Uri.parse(uriString)
            var name = "Documento"
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: "Documento"
                }
            }
            name
        } catch (e: Exception) {
            Timber.w("$TAG: no se pudo resolver nombre — ${e.message}")
            Uri.parse(uriString).lastPathSegment?.substringAfterLast("/") ?: "Documento"
        }
    }

    private fun resolveMimeType(uriString: String): String? {
        return when {
            uriString.contains("image")                          -> "image/jpeg"
            uriString.endsWith(".pdf", ignoreCase = true)        -> "application/pdf"
            uriString.endsWith(".docx", ignoreCase = true)       -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            uriString.endsWith(".doc", ignoreCase = true)        -> "application/msword"
            uriString.endsWith(".xlsx", ignoreCase = true)       -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            uriString.endsWith(".xls", ignoreCase = true)        -> "application/vnd.ms-excel"
            uriString.endsWith(".jpg", ignoreCase = true) ||
            uriString.endsWith(".jpeg", ignoreCase = true)       -> "image/jpeg"
            uriString.endsWith(".png", ignoreCase = true)        -> "image/png"
            uriString.endsWith(".txt", ignoreCase = true)        -> "text/plain"
            else                                                 -> null
        }
    }

    fun onPageChanged(page: Int, total: Int) {
        _uiState.update { it.copy(currentPage = page, totalPages = total) }
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite) }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun shareDocument(context: Context) {
        val state = _uiState.value
        val document = state.document ?: return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = state.mimeType ?: "application/pdf"
                state.fileUri?.let { uri ->
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                putExtra(Intent.EXTRA_SUBJECT, document.name)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Compartir ${document.name}")
            )
        } catch (e: Exception) {
            Timber.e(e, "$TAG: error compartiendo documento")
            _uiState.update { it.copy(error = "No se pudo compartir el archivo") }
        }
    }

    private fun getMockDocument(id: String): DocumentUiModel? {
        val mockDocs = mapOf(
            "1"  to DocumentUiModel("1",  "Contrato_Servicios_2024.pdf",  DocumentType.PDF,        "2.4 MB", "01/05/2026", true),
            "2"  to DocumentUiModel("2",  "Informe_Trimestral.docx",      DocumentType.WORD,       "1.1 MB", "30/04/2026"),
            "3"  to DocumentUiModel("3",  "Presupuesto_Q1.xlsx",          DocumentType.EXCEL,      "890 KB", "29/04/2026"),
            "4"  to DocumentUiModel("4",  "Presentacion_Clientes.pptx",   DocumentType.POWERPOINT, "5.2 MB", "28/04/2026"),
            "5"  to DocumentUiModel("5",  "Foto_Documento.jpg",           DocumentType.IMAGE,      "3.8 MB", "27/04/2026"),
            "6"  to DocumentUiModel("6",  "Manual_Usuario.pdf",           DocumentType.PDF,        "4.1 MB", "26/04/2026", true),
            "7"  to DocumentUiModel("7",  "Notas_Reunion.txt",            DocumentType.TEXT,       "12 KB",  "25/04/2026"),
            "8"  to DocumentUiModel("8",  "Backup_Documentos.zip",        DocumentType.ZIP,        "45 MB",  "24/04/2026"),
            "9"  to DocumentUiModel("9",  "Escaneo_Factura.pdf",          DocumentType.OCR,        "1.8 MB", "23/04/2026"),
            "10" to DocumentUiModel("10", "Reporte_Ventas.xlsx",          DocumentType.EXCEL,      "2.2 MB", "22/04/2026", true),
            "11" to DocumentUiModel("11", "Carta_Presentacion.docx",      DocumentType.WORD,       "340 KB", "21/04/2026"),
            "12" to DocumentUiModel("12", "Logo_Empresa.png",             DocumentType.IMAGE,      "890 KB", "20/04/2026")
        )
        return mockDocs[id]
    }
}
