package com.docsmart.features.home.presentation

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

data class HomeUiState(
    val recentDocuments: List<DocumentUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val userName: String = "Usuario"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    val adManager: AdManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        _uiState.update { state ->
            state.copy(
                recentDocuments = listOf(
                    DocumentUiModel(
                        id = "1",
                        name = "Contrato_Servicios_2024.pdf",
                        type = DocumentType.PDF,
                        size = "2.4 MB",
                        date = "Hoy",
                        isFavorite = true
                    ),
                    DocumentUiModel(
                        id = "2",
                        name = "Informe_Trimestral.docx",
                        type = DocumentType.WORD,
                        size = "1.1 MB",
                        date = "Ayer"
                    ),
                    DocumentUiModel(
                        id = "3",
                        name = "Presupuesto_Q1.xlsx",
                        type = DocumentType.EXCEL,
                        size = "890 KB",
                        date = "Hace 2 días"
                    ),
                    DocumentUiModel(
                        id = "4",
                        name = "Presentacion_Clientes.pptx",
                        type = DocumentType.POWERPOINT,
                        size = "5.2 MB",
                        date = "Hace 3 días"
                    ),
                    DocumentUiModel(
                        id = "5",
                        name = "Foto_Documento.jpg",
                        type = DocumentType.IMAGE,
                        size = "3.8 MB",
                        date = "Hace 5 días"
                    )
                )
            )
        }
    }

    fun toggleFavorite(documentId: String) {
        _uiState.update { state ->
            state.copy(
                recentDocuments = state.recentDocuments.map { doc ->
                    if (doc.id == documentId)
                        doc.copy(isFavorite = !doc.isFavorite)
                    else doc
                }
            )
        }
    }
}