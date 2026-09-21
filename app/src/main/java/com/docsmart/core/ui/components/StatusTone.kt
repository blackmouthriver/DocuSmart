package com.docsmart.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Significado del chip de estado; define su par de colores contenedor/texto. */
enum class StatusTone { NEUTRAL, ACCENT, SUCCESS, WARNING, ERROR }

private data class ToneColors(
    val container: Color,
    val content: Color,
)

// Pares elegidos para superar 4.5:1 (WCAG AA) en claro y oscuro. Antes el
// estado "Vencido" de Agenda era ámbar sobre ámbar translúcido (~2.3:1).
private val successLight = ToneColors(Color(0xFFDCFCE7), Color(0xFF14532D))
private val successDark = ToneColors(Color(0xFF12351F), Color(0xFF86EFAC))
private val warningLight = ToneColors(Color(0xFFFEF3C7), Color(0xFF78350F))
private val warningDark = ToneColors(Color(0xFF3B2A08), Color(0xFFFCD34D))
private val errorLight = ToneColors(Color(0xFFFEE2E2), Color(0xFF7F1D1D))
private val errorDark = ToneColors(Color(0xFF3B1414), Color(0xFFFCA5A5))

@Composable
private fun toneColors(tone: StatusTone): ToneColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return when (tone) {
        StatusTone.NEUTRAL -> ToneColors(scheme.surfaceVariant, scheme.onSurfaceVariant)
        StatusTone.ACCENT -> ToneColors(scheme.primaryContainer, scheme.onPrimaryContainer)
        StatusTone.SUCCESS -> if (dark) successDark else successLight
        StatusTone.WARNING -> if (dark) warningDark else warningLight
        StatusTone.ERROR -> if (dark) errorDark else errorLight
    }
}

/**
 * Chip informativo de estado (Vencido, Hoy, Próximo, Premium...). Es solo
 * texto: no es clicable ni expone acción a TalkBack.
 */
@Composable
fun DocuSmartStatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = toneColors(tone)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colors.container,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
