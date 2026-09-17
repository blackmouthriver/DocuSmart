package com.docsmart.features.agenda.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.docsmart.R
import com.docsmart.core.ui.components.AppLibraryPickerViewModel
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.components.LinkDocumentDialog

// HU-65: mismo mecanismo que NoteLinkDocumentDialog (backlog UX #50) --
// reutiliza AppLibraryPickerViewModel para elegir un documento YA indexado
// por la app, sin la rama "elegir del dispositivo". Wrapper delgado sobre
// el componente compartido (B21, auditoría general 2026-09-17) -- solo
// resuelve los 3 strings propios de Agenda.
@Composable
fun AgendaLinkDocumentDialog(
    currentDocumentId: String?,
    onDismiss        : () -> Unit,
    onSelect         : (DocumentUiModel) -> Unit,
    onUnlink         : () -> Unit,
    viewModel        : AppLibraryPickerViewModel = hiltViewModel()
) {
    LinkDocumentDialog(
        currentDocumentId = currentDocumentId,
        title             = stringResource(R.string.agenda_link_document_title),
        emptyMessage      = stringResource(R.string.agenda_link_document_empty),
        unlinkLabel       = stringResource(R.string.agenda_unlink_document),
        onDismiss         = onDismiss,
        onSelect          = onSelect,
        onUnlink          = onUnlink,
        viewModel         = viewModel
    )
}
