package com.docsmart.core.ui.components.buttons

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Botón flotante de la acción principal de una pantalla. Relleno con el color
 * de acento pleno (antes usaba el `primaryContainer` pálido por defecto de
 * Material, que casi no destacaba sobre el fondo) y texto/icono con el par
 * `onPrimary`, ya con contraste garantizado por el tema.
 */
@Composable
fun DocuSmartFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
