package com.docsmart.features.library.presentation

import androidx.lifecycle.ViewModel
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class LibraryUiState(
    val allDocuments: List<DocumentUiModel> = emptyList(),
    val filteredDocuments: List<DocumentUiModel> = emptyList(),
    val favorites: List<DocumentUiModel> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: DocumentType? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    val adManager: AdManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val mockDocuments = listOf(
        DocumentUiModel("1",  "Contrato_Servicios_2024.pdf",   DocumentType.PDF,        "2.4 MB", "01/05/2026", true),
        DocumentUiModel("2",  "Informe_Trimestral.docx",       DocumentType.WORD,       "1.1 MB", "30/04/2026"),
        DocumentUiModel("3",  "Presupuesto_Q1.xlsx",           DocumentType.EXCEL,      "890 KB", "29/04/2026"),
        DocumentUiModel("4",  "Presentacion_Clientes.pptx",    DocumentType.POWERPOINT, "5.2 MB", "28/04/2026"),
        DocumentUiModel("5",  "Foto_Documento.jpg",            DocumentType.IMAGE,      "3.8 MB", "27/04/2026"),
        DocumentUiModel("6",  "Manual_Usuario.pdf",            DocumentType.PDF,        "4.1 MB", "26/04/2026", true),
        DocumentUiModel("7",  "Notas_Reunion.txt",             DocumentType.TEXT,       "12 KB",  "25/04/2026"),
        DocumentUiModel("8",  "Backup_Documentos.zip",         DocumentType.ZIP,        "45 MB",  "24/04/2026"),
        DocumentUiModel("9",  "Escaneo_Factura.pdf",           DocumentType.OCR,        "1.8 MB", "23/04/2026"),
        DocumentUiModel("10", "Reporte_Ventas.xlsx",           DocumentType.EXCEL,      "2.2 MB", "22/04/2026", true),
        DocumentUiModel("11", "Carta_Presentacion.docx",       DocumentType.WORD,       "340 KB", "21/04/2026"),
        DocumentUiModel("12", "Logo_Empresa.png",              DocumentType.IMAGE,      "890 KB", "20/04/2026")
    )

    init {
        loadDocuments()
    }

    private fun loadDocuments() {
        _uiState.update { state ->
            state.copy(
                allDocuments = mockDocuments,
                filteredDocuments = mockDocuments,
                favorites = mockDocuments.filter { it.isFavorite }
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state -> state.copy(searchQuery = query) }
        applyFilters()
    }

    fun onCategorySelected(type: DocumentType?) {
        _uiState.update { state ->
            val newCategory = if (state.selectedCategory == type) null else type
            state.copy(selectedCategory = newCategory)
        }
        applyFilters()
    }

    fun toggleFavorite(documentId: String) {
        val updated = _uiState.value.allDocuments.map { doc ->
            if (doc.id == documentId) doc.copy(isFavorite = !doc.isFavorite) else doc
        }
        _uiState.update { state ->
            state.copy(
                allDocuments = updated,
                favorites = updated.filter { it.isFavorite }
            )
        }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val result = state.allDocuments.filter { doc ->
            val matchesQuery = state.searchQuery.isBlank() ||
                    doc.name.contains(state.searchQuery, ignoreCase = true)
            val matchesCategory = state.selectedCategory == null ||
                    doc.type == state.selectedCategory
            matchesQuery && matchesCategory
        }
        _uiState.update { it.copy(filteredDocuments = result) }
    }

    fun clearSearch() {
        onSearchQueryChange("")
    }
}