package com.docsmart.features.agenda.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docsmart.R
import com.docsmart.core.ui.components.AppLibraryPickerViewModel
import com.docsmart.core.ui.components.DocumentUiModel

// HU-65: mismo mecanismo que NoteLinkDocumentDialog (backlog UX #50) --
// reutiliza AppLibraryPickerViewModel para elegir un documento YA indexado
// por la app, sin la rama "elegir del dispositivo".
@Composable
fun AgendaLinkDocumentDialog(
    currentDocumentId: String?,
    onDismiss        : () -> Unit,
    onSelect         : (DocumentUiModel) -> Unit,
    onUnlink         : () -> Unit,
    viewModel        : AppLibraryPickerViewModel = hiltViewModel()
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val isLoading  by viewModel.isLoading.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape            = MaterialTheme.shapes.large,
        title            = { Text(stringResource(R.string.agenda_link_document_title)) },
        text = {
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
            } else if (documents.isEmpty()) {
                Text(
                    text  = stringResource(R.string.agenda_link_document_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(documents, key = { it.id }) { doc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(doc) }
                                .padding(vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = if (doc.id == currentDocumentId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text     = doc.name,
                                style    = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_cancel))
            }
        },
        dismissButton = {
            if (currentDocumentId != null) {
                TextButton(onClick = onUnlink) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.agenda_unlink_document),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
