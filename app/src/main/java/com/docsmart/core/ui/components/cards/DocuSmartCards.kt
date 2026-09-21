package com.docsmart.core.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.theme.accentBorder
import com.docsmart.core.ui.theme.accentShadow
import com.docsmart.core.ui.theme.ensureIconContrast

// ── Card base reutilizable ────────────────────────────
// Úsala como contenedor para cualquier contenido
@Composable
fun DocuSmartCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large // 20dp
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .accentShadow(shape = shape, elevation = 2.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape)
                .then(
                    // H23 (auditoría de accesibilidad TalkBack 2026-09-18): sin
                    // role, TalkBack no anunciaba esta tarjeta como accionable.
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

// ── Card de acceso rápido (grid home) ────────────────
// Uso: íconos de acceso rápido en el Home
@Composable
fun DocuSmartQuickAccessCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
) {
    val shape = MaterialTheme.shapes.large
    val baseTint = iconTint.takeOrElse { MaterialTheme.colorScheme.primary }
    // Se mide contra el fondo real del icono (superficie + su propio tinte al
    // 12-14%), no solo contra la superficie.
    val iconBackground = lerp(MaterialTheme.colorScheme.surface, baseTint, 0.14f)
    val tint = ensureIconContrast(baseTint, iconBackground)
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .accentShadow(shape = shape)
                .clip(shape)
                .background(backgroundColor)
                .accentBorder(shape = shape)
                // H2 (auditoría de accesibilidad TalkBack 2026-09-18): sin role,
                // TalkBack no anunciaba las 9 tarjetas de acceso rápido de Inicio
                // como accionables.
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            // ← reducido de 12dp a 8dp
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    // Hallazgo real de la auditoría general 2026-09-17
                    // (octava ronda, Media -- G7): el mismo texto ya es
                    // visible en el Text de abajo, dentro del mismo
                    // Box.clickable (que fusiona la semántica de sus
                    // hijos en un solo nodo) -- TalkBack anunciaba el
                    // nombre 2 veces por cada uno de los 9 accesos
                    // rápidos. Mismo criterio que DocuSmartToolCard más
                    // abajo, que ya usa null para este mismo patrón.
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                // ← labelMedium → labelSmall
                style = MaterialTheme.typography.labelSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ── Tile compacto de herramienta (rejilla de 2 columnas) ──
// Rediseño 2026-09-21: las 15 herramientas PDF eran tarjetas anchas de una
// columna (~96dp cada una); el tile vertical de dos columnas las agrupa por tarea
// y reduce el desplazamiento sin quitar el título ni la descripción.
@Composable
fun DocuSmartToolTile(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
) {
    val shape = MaterialTheme.shapes.large
    val baseTint = iconTint.takeOrElse { MaterialTheme.colorScheme.primary }
    val iconBackground = lerp(MaterialTheme.colorScheme.surface, baseTint, 0.14f)
    val tint = ensureIconContrast(baseTint, iconBackground)
    Box(
        modifier =
            modifier
                .accentShadow(shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape)
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Card de herramienta PDF ───────────────────────────
// Uso: pantalla PDF Tools
@Composable
fun DocuSmartToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
) {
    val shape = MaterialTheme.shapes.large
    val baseTint = iconTint.takeOrElse { MaterialTheme.colorScheme.primary }
    // Se mide contra el fondo real del icono (superficie + su propio tinte al
    // 12-14%), no solo contra la superficie.
    val iconBackground = lerp(MaterialTheme.colorScheme.surface, baseTint, 0.14f)
    val tint = ensureIconContrast(baseTint, iconBackground)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .accentShadow(shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .accentBorder(shape = shape)
                // H23 (auditoría de accesibilidad TalkBack 2026-09-18): sin
                // role, TalkBack no anunciaba esta tarjeta como accionable.
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
