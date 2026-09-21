package com.docsmart.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.theme.DocuSmartSpacing

/** Estado de carga centrado; el texto es opcional y sirve también a TalkBack. */
@Composable
fun DocuSmartLoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(DocuSmartSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DocuSmartSpacing.md),
    ) {
        CircularProgressIndicator()
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Estado de error: qué pasó y qué hacer (el texto lo pasa el llamador, ya
 * localizado). [retry] es un slot para no acoplar este componente a una
 * acción concreta.
 */
@Composable
fun DocuSmartErrorState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    retry: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = DocuSmartSpacing.xl, vertical = DocuSmartSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DocuSmartSpacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        retry?.invoke()
    }
}
