package com.docsmart.features.viewer.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R

// Backlog UX #47: lista de páginas marcadas del documento actual -- tocar
// una fila salta ahí (ViewerViewModel.navigateToBookmark), el ícono de
// borrar la desmarca sin necesidad de estar en esa página primero (mismo
// criterio que DocumentContextMenu para el resto de los menús del Visor:
// ModalBottomSheet, no un AlertDialog, porque es una lista, no una
// confirmación puntual).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerBookmarksSheet(
    bookmarkedPages: List<Int>, // 0-based, ya ordenadas
    onNavigate: (Int) -> Unit,
    onRemove  : (Int) -> Unit,
    onDismiss : () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        tonalElevation   = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                text     = stringResource(R.string.viewer_bookmarks_title),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant
            )
            if (bookmarkedPages.isEmpty()) {
                Text(
                    text     = stringResource(R.string.viewer_bookmarks_empty),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(bookmarkedPages, key = { it }) { page ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(page) }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    stringResource(R.string.viewer_bookmarks_page_format, page + 1),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            IconButton(onClick = { onRemove(page) }) {
                                Icon(
                                    imageVector        = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.general_delete)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
