package com.docsmart.features.library.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.components.DocuSmartSearchBar

@Composable
fun LibraryHeader(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    totalDocuments: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
    ) {
        // Rediseño 2026-09-20 (Fase 2): se quita el título "Biblioteca" que repetía
        // el del banner de justo arriba; queda solo el contador de documentos.
        // Hallazgo real de la auditoría general 2026-09-18 (Media, i18n):
        // concatenación manual de número + library_documents (string plano
        // fijo en plural), mismo problema que library_document_count en
        // DocumentListSection.kt -- "1 documentos" es incorrecto. Migrado al
        // mismo <plurals> library_document_count_plural.
        Text(
            text = pluralStringResource(R.plurals.library_document_count_plural, totalDocuments, totalDocuments),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        DocuSmartSearchBar(
            query = searchQuery,
            onQueryChange = onQueryChange,
            onClear = onClear,
            placeholder = stringResource(R.string.library_search),
        )
    }
}
