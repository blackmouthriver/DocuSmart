package com.docsmart.features.viewer.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R

// Backlog UX #47: se agregan 2 botones al indicador de página existente --
// marcar/desmarcar la página actual (izquierda) y ver la lista completa de
// marcadores (derecha) -- sin tocar el texto centrado, que sigue siendo lo
// primero que se ve al mirar la barra.
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ViewerBottomBar(
    currentPage: Int,
    totalPages: Int,
    visible: Boolean,
    isCurrentPageBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onShowBookmarks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && totalPages > 0,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(48.dp)
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = onShowBookmarks, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        imageVector        = Icons.Rounded.Bookmarks,
                        contentDescription = stringResource(R.string.viewer_bookmarks_title)
                    )
                }
                // Bug real encontrado 2026-09-14 (repaso general):
                // hardcodeado en español, saltándose el sistema de idiomas.
                Text(
                    text = String.format(
                        stringResource(R.string.viewer_bottom_bar_page_format), currentPage + 1, totalPages
                    ),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(onClick = onToggleBookmark, modifier = Modifier.align(Alignment.CenterEnd)) {
                    val bookmarkIcon =
                        if (isCurrentPageBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder
                    Icon(
                        imageVector = bookmarkIcon,
                        contentDescription = stringResource(
                            if (isCurrentPageBookmarked) R.string.viewer_bookmark_remove
                            else R.string.viewer_bookmark_add
                        ),
                        tint = if (isCurrentPageBookmarked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}