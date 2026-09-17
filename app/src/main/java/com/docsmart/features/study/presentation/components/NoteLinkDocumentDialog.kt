package com.docsmart.features.study.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.docsmart.R
import com.docsmart.core.ui.components.AppLibraryPickerViewModel
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.components.LinkDocumentDialog

// Backlog UX #50: elegir un documento YA indexado por la app (no "desde el
// dispositivo" -- vincular una nota exige que el documento ya exista en
// Biblioteca) -- reutiliza el mismo inventario que ya carga
// `AppLibraryPickerViewModel` para el selector de Seguridad/Herramientas
// PDF (item #15), sin la rama de "elegir del dispositivo" que ese diálogo
// sí ofrece. Wrapper delgado sobre el componente compartido (B21,
// auditoría general 2026-09-17) -- solo resuelve los 3 strings propios de
// Notas.
@Composable
fun NoteLinkDocumentDialog(
    currentDocumentId: String?,
    onDismiss        : () -> Unit,
    onSelect         : (DocumentUiModel) -> Unit,
    onUnlink         : () -> Unit,
    viewModel        : AppLibraryPickerViewModel = hiltViewModel()
) {
    LinkDocumentDialog(
        currentDocumentId = currentDocumentId,
        title             = stringResource(R.string.note_link_document_title),
        emptyMessage      = stringResource(R.string.note_link_document_empty),
        unlinkLabel       = stringResource(R.string.note_unlink_document),
        onDismiss         = onDismiss,
        onSelect          = onSelect,
        onUnlink          = onUnlink,
        viewModel         = viewModel
    )
}
