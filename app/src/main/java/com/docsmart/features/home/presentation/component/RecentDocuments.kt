package com.docsmart.features.home.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.components.DocuSmartDocumentItem
import com.docsmart.core.ui.components.DocuSmartEmptyState
import com.docsmart.core.ui.components.DocumentUiModel

@Composable
fun RecentDocuments(
    documents: List<DocumentUiModel>,
    onDocumentClick: (DocumentUiModel) -> Unit,
    onFavoriteClick: (String) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header de sección
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recientes",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onSeeAllClick) {
                Text(
                    text = "Ver todos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Contenido
        if (documents.isEmpty()) {
            DocuSmartEmptyState(
                icon = Icons.Rounded.FolderOff,
                title = "Sin documentos recientes",
                description = "Abre un archivo para verlo aquí",
                actionLabel = "Abrir archivo",
                onAction = { }
            )
        } else {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                documents.forEachIndexed { index, document ->
                    DocuSmartDocumentItem(
                        document = document,
                        onClick = { onDocumentClick(document) },
                        onFavoriteClick = { onFavoriteClick(document.id) },
                        showDivider = index < documents.size - 1
                    )
                }
            }
        }
    }
}