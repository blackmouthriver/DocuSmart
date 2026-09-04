package com.docsmart.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.rememberAccentGradient

@Composable
fun DocuSmartTopBanner(
    screenTitle   : String,
    screenSubtitle: String = "",
    modifier      : Modifier = Modifier,
    // RF-VIS-07: slot opcional para un ícono de acción (ej. acceso a la
    // Papelera en Biblioteca) -- por defecto null, no afecta a las 9 pantallas
    // que ya usan este banner sin este parámetro.
    actions       : (@Composable RowScope.() -> Unit)? = null,
    // Bug real reportado por el usuario 2026-08-30: cada sub-pantalla
    // armaba su propia flecha de "volver" suelta al lado del banner (fuera
    // de su fondo azul, sin texto), y el banner terminaba sin usar el 100%
    // del ancho porque compartía la fila con esa flecha. Con `onBack` la
    // flecha + "Volver" quedan integrados dentro del propio banner -- si es
    // `null` (pantallas de la barra inferior), el banner se ve exactamente
    // igual que antes.
    onBack        : (() -> Unit)? = null
) {
    // Bug real corregido 2026-09-04 (backlog UX §7, HU-UX-06): este
    // degradado estaba fijo en tonos de azul, ignorando el "Color de
    // acento" elegido en Ajustes -- este banner lo comparten 9 pantallas,
    // así que el fix aplica a todas de una sola vez.
    val bannerGradient = rememberAccentGradient()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(brush = Brush.linearGradient(colors = bannerGradient))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // ── Círculos decorativos ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(130.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-25).dp)
                .background(
                    color = Color.White.copy(alpha = 0.07f),
                    shape = MaterialTheme.shapes.extraLarge
                )
        )
        Box(
            modifier = Modifier
                .size(70.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 15.dp, y = 25.dp)
                .background(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = MaterialTheme.shapes.extraLarge
                )
        )

        Column {
            // ── Volver (opcional) ────────────────────────────────────────────
            if (onBack != null) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .clickable(role = Role.Button, onClick = onBack)
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(18.dp)
                    )
                    Text(
                        text  = stringResource(R.string.general_back),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }

            // ── Contenido principal ──────────────────────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Logo DocuSmart
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = MaterialTheme.shapes.medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter           = painterResource(R.drawable.ic_docusmart_logo),
                        contentDescription = "DocuSmart",
                        modifier          = Modifier
                            .size(34.dp)
                            .padding(2.dp)
                    )
                }

                // Textos
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Marca "DocuSmart" pequeña arriba
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text  = "Docu",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text  = "Smart",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Normal
                        )
                    }
                    // Título de la pantalla
                    Text(
                        text       = screenTitle,
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                    // Subtítulo opcional
                    if (screenSubtitle.isNotBlank()) {
                        Text(
                            text  = screenSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }

                actions?.invoke(this)
            }
        }
    }
}