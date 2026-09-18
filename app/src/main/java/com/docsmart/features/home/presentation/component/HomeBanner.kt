package com.docsmart.features.home.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.rememberAccentGradient

@Composable
fun HomeBanner(
    onOpenFileClick: () -> Unit,
    onConvertClick : () -> Unit,
    modifier       : Modifier = Modifier
) {
    // Bug real corregido 2026-09-04 (backlog UX §7, HU-UX-06): este
    // degradado estaba fijo en tonos de azul (DocuBlue/SmartBlue/
    // IndigoAccent) sin importar el "Color de acento" elegido en Ajustes
    // -- era el único elemento de Home que no respetaba esa elección.
    val bannerGradient = rememberAccentGradient()
    val primary = MaterialTheme.colorScheme.primary

    Column(modifier = modifier) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(brush = Brush.linearGradient(colors = bannerGradient))
            .padding(20.dp)
    ) {
        // ── Círculos decorativos ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.TopEnd)
                .offset(x = 45.dp, y = (-25).dp)
                .background(
                    color = Color.White.copy(alpha = 0.07f),
                    shape = MaterialTheme.shapes.extraLarge
                )
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 15.dp, y = 25.dp)
                .background(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = MaterialTheme.shapes.extraLarge
                )
        )

        // ── Contenido ─────────────────────────────────────────────────────────
        Column {
            // Fila logo + marca
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.padding(bottom = 12.dp)
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = MaterialTheme.shapes.medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter            = painterResource(R.drawable.ic_docusmart_logo),
                        // H1 (auditoría de accesibilidad TalkBack 2026-09-18):
                        // el texto visible "Docu"+"Smart" de al lado ya dice
                        // "DocuSmart" -- con contentDescription acá, TalkBack
                        // anunciaba el nombre 3 veces seguidas (logo + 2 Text).
                        contentDescription = null,
                        modifier           = Modifier
                            .size(30.dp)
                            .padding(2.dp)
                    )
                }

                // Nombre de marca -- a nivel con el logo, en la misma fila.
                // Bug real corregido 2026-09-07 (seguimiento): debajo de esta
                // fila había un segundo texto con `R.string.app_name`
                // ("DocuSmart") que repetía lo que ya dicen "Docu"+"Smart" acá
                // mismo -- se elimina, ya no hace falta el Column contenedor.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "Docu",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text       = "Smart",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Título principal -- centrado, a todo el ancho
            Text(
                text       = stringResource(R.string.home_title),
                style      = MaterialTheme.typography.headlineSmall,
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Subtítulo -- centrado, igual que el título (pedido explícito
            // del usuario 2026-09-07: primero se pidió justificado, pero el
            // efecto no se notaba porque el texto tenía saltos de línea
            // manuales -- al aclarar, el usuario prefirió centrado en vez de
            // quitar los saltos solo para lograr el justificado).
            Text(
                text      = stringResource(R.string.home_subtitle),
                style     = MaterialTheme.typography.bodySmall,
                color     = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Pedido explícito del usuario 2026-09-07: sacar los botones de
    // acción de dentro del recuadro degradado, dejarlos debajo (mismo
    // patrón ya usado con la flecha "Volver" de DocuSmartTopBanner) --
    // por eso ahora usan el color de acento en vez de blanco/blanco.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Altura subida de 44dp a 48dp (auditoría de testers 2026-09-12, "botones pequeños").
        Button(
            onClick   = onOpenFileClick,
            modifier  = Modifier.weight(1f).height(48.dp),
            shape     = MaterialTheme.shapes.medium,
            colors    = ButtonDefaults.buttonColors(
                containerColor = primary,
                contentColor   = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text  = stringResource(R.string.home_open),
                style = MaterialTheme.typography.labelLarge
            )
        }

        OutlinedButton(
            onClick  = onConvertClick,
            modifier = Modifier.weight(1f).height(48.dp),
            shape    = MaterialTheme.shapes.medium,
            colors   = ButtonDefaults.outlinedButtonColors(
                contentColor = primary
            ),
            border = BorderStroke(1.dp, primary)
        ) {
            Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text  = stringResource(R.string.home_convert),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
    }
}