package com.docsmart.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.theme.rememberBannerGradient

@Composable
fun DocuSmartTopBanner(
    screenTitle: String,
    screenSubtitle: String = "",
    modifier: Modifier = Modifier,
    // RF-VIS-07: slot opcional para un ícono de acción (ej. acceso a la
    // Papelera en Biblioteca) -- por defecto null, no afecta a las 9 pantallas
    // que ya usan este banner sin este parámetro.
    actions: (@Composable RowScope.() -> Unit)? = null,
    // Bug real reportado por el usuario 2026-08-30: cada sub-pantalla
    // armaba su propia flecha de "volver" suelta al lado del banner (fuera
    // de su fondo azul, sin texto), y el banner terminaba sin usar el 100%
    // del ancho porque compartía la fila con esa flecha. Con `onBack` la
    // flecha + "Volver" quedan integrados dentro del propio banner -- si es
    // `null` (pantallas de la barra inferior), el banner se ve exactamente
    // igual que antes.
    onBack: (() -> Unit)? = null,
    // Opcional: "Inicio" a la derecha de "Volver" (vistas anidadas como las de
    // Modo Estudio y Agenda, para volver a Inicio sin repetir "Volver").
    onHome: (() -> Unit)? = null,
) {
    // Bug real corregido 2026-09-04 (backlog UX §7, HU-UX-06): este
    // degradado estaba fijo en tonos de azul, ignorando el "Color de
    // acento" elegido en Ajustes -- este banner lo comparten 9 pantallas,
    // así que el fix aplica a todas de una sola vez.
    val bannerGradient = rememberBannerGradient()

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(brush = Brush.linearGradient(colors = bannerGradient))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            // ── Círculos decorativos ──────────────────────────────────────────
            Box(
                modifier =
                    Modifier
                        .size(130.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 40.dp, y = (-25).dp)
                        .background(
                            color = Color.White.copy(alpha = 0.07f),
                            shape = MaterialTheme.shapes.extraLarge,
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .size(70.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 15.dp, y = 25.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.05f),
                            shape = MaterialTheme.shapes.extraLarge,
                        ),
            )

            // ── Contenido principal ────────────────────────────────────────────
            // Reestructurado 2026-09-07 a pedido explícito del usuario: antes
            // el logo y el título/subtítulo compartían una sola fila, así que
            // el logo quedaba centrado contra las 3 líneas de texto en vez de
            // quedar "a nivel" solo con "DocuSmart" -- ahora el logo+marca son
            // una fila compacta arriba, y el título (centrado)/subtítulo
            // (justificado) van debajo, a todo el ancho del banner.
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Logo DocuSmart
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .background(
                                    color = Color.White.copy(alpha = 0.18f),
                                    shape = MaterialTheme.shapes.medium,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_docusmart_logo),
                            // H1 (auditoría de accesibilidad TalkBack
                            // 2026-09-18): el texto visible "Docu"+"Smart" de
                            // al lado ya dice "DocuSmart" -- con
                            // contentDescription acá, TalkBack anunciaba el
                            // nombre repetido (logo + 2 Text).
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .padding(1.dp),
                        )
                    }

                    // Marca "DocuSmart" -- a nivel con el logo, en la misma fila
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Docu",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Smart",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Normal,
                        )
                    }

                    actions?.invoke(this)
                }

                Spacer(Modifier.height(12.dp))

                // Título de la pantalla -- centrado, a todo el ancho
                Text(
                    text = screenTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Subtítulo opcional -- centrado, igual que el título (pedido
                // explícito del usuario 2026-09-07: primero se pidió
                // justificado, pero el efecto no se notaba con textos de una
                // sola línea -- al aclarar, prefirió centrado).
                if (screenSubtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = screenSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // ── Volver (opcional) ──────────────────────────────────────────────────
        // Pedido explícito del usuario 2026-09-06: antes vivía integrado
        // dentro del degradado del banner (texto/ícono blancos) -- ahora
        // queda debajo, fuera del área con color, usando el color de acento
        // (`primary`) en vez de blanco fijo, ya que el fondo de acá es el
        // normal de la pantalla (claro u oscuro según el tema), no el
        // degradado.
        if (onBack != null || onHome != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    BannerNavAction(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        label = stringResource(R.string.general_back),
                        onClick = onBack,
                    )
                } else {
                    Spacer(Modifier)
                }
                if (onHome != null) {
                    BannerNavAction(
                        icon = Icons.Rounded.Home,
                        label = stringResource(R.string.nav_home),
                        onClick = onHome,
                    )
                }
            }
        }
    }
}

// H17 (auditoría de accesibilidad TalkBack 2026-09-18): el objetivo táctil
// real medía ~20-24dp de alto (por debajo del mínimo de 48dp) -- se asegura
// el mínimo sin tocar el tamaño visual del ícono/texto.
@Composable
private fun BannerNavAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
