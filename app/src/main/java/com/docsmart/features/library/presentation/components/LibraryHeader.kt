package com.docsmart.features.library.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.components.DocuSmartSearchBar

@Composable
fun LibraryHeader(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    totalDocuments: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Biblioteca",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$totalDocuments archivos guardados",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        DocuSmartSearchBar(
            query = searchQuery,
            onQueryChange = onQueryChange,
            onClear = onClear,
            placeholder = "Buscar en biblioteca..."
        )
    }
}