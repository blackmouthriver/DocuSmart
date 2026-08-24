package com.docsmart.features.home.presentation.component

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
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.DocuBlue
import com.docsmart.core.ui.theme.IndigoAccent
import com.docsmart.core.ui.theme.SmartBlue

@Composable
fun HomeBanner(
    onOpenFileClick: () -> Unit,
    onConvertClick : () -> Unit,
    modifier       : Modifier = Modifier
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
                        contentDescription = "DocuSmart",
                        modifier           = Modifier
                            .size(30.dp)
                            .padding(2.dp)
                    )
                }

                // Nombre de marca
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
                    Text(
                        text  = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Título principal
            Text(
                text  = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Botones de acción
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick   = onOpenFileClick,
                    modifier  = Modifier.weight(1f).height(44.dp),
                    shape     = MaterialTheme.shapes.medium,
                    colors    = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor   = DocuBlue
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
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape    = MaterialTheme.shapes.medium,
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(0.6f), Color.White.copy(0.6f))
                        )
                    )
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
}