package com.docsmart.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.DocuBlue
import com.docsmart.core.ui.theme.IndigoAccent
import com.docsmart.core.ui.theme.SmartBlue

@Composable
fun DocuSmartTopBanner(
    screenTitle   : String,
    screenSubtitle: String = "",
    modifier      : Modifier = Modifier,
    // RF-VIS-07: slot opcional para un ícono de acción (ej. acceso a la
    // Papelera en Biblioteca) -- por defecto null, no afecta a las 9 pantallas
    // que ya usan este banner sin este parámetro.
    actions       : (@Composable RowScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(DocuBlue, SmartBlue, IndigoAccent)
                )
            )
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

        // ── Contenido principal ───────────────────────────────────────────────
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