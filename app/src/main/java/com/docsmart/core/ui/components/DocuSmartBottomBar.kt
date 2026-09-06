package com.docsmart.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docsmart.R
import com.docsmart.core.navegation.NavRoutes
import com.docsmart.core.ui.theme.rememberAccentGradient

data class BottomNavItem(
    @StringRes val labelRes: Int,
    val route: String,
    val icon: ImageVector,
    // Ruta real a la que navegar al tocar la pestaña -- por defecto igual a
    // `route`, pero para Convertir difiere: `route` es la plantilla con
    // parámetro opcional ("converter?initialType={initialType}", usada para
    // comparar contra el destino actual) mientras que acá hace falta la
    // ruta ya resuelta (`NavRoutes.Converter.createRoute()`, sin el
    // placeholder literal) para que `navController.navigate(...)` funcione.
    val navigateRoute: String = route
)

// Los labels se resuelven con stringResource() dentro del Composable (ver
// abajo) — bottomNavItems es una lista de nivel de módulo, sin contexto de
// composición, así que no puede resolver el string aquí directamente.
private val bottomNavItems = listOf(
    BottomNavItem(R.string.nav_home, NavRoutes.Home.route, Icons.Rounded.Home),
    BottomNavItem(R.string.nav_library, NavRoutes.Library.route, Icons.Rounded.LibraryBooks),
    BottomNavItem(
        R.string.nav_converter, NavRoutes.Converter.route, Icons.Rounded.SwapHoriz,
        navigateRoute = NavRoutes.Converter.createRoute()
    ),
    BottomNavItem(R.string.nav_pdf, NavRoutes.PdfTools.route, Icons.Rounded.PictureAsPdf),
    BottomNavItem(R.string.nav_settings, NavRoutes.Settings.route, Icons.Rounded.Settings)
)

// ── Solo mostrar en rutas principales ────────────────
private val routesWithBottomBar = setOf(
    NavRoutes.Home.route,
    NavRoutes.Library.route,
    NavRoutes.Converter.route,
    NavRoutes.PdfTools.route,
    NavRoutes.Settings.route
)

// Medidas del bar con elevación móvil: el destino seleccionado sobresale
// apenas del bar con un spring suave (feedback 2026-09-05: el diseño
// anterior lo elevaba muy por encima del bar y el hueco extra reservado
// para eso se veía como una franja plana que tapaba el contenido/fondo
// real detrás -- ahora el fondo del bar se ajusta al alto real del
// contenido (Row) en vez de un alto fijo, así que el círculo activo
// sobresale por overflow natural, sin reservar espacio extra ni pintar
// un color de relleno detrás). Los colores NO viven acá -- salen de
// MaterialTheme.colorScheme (acento + tema claro/oscuro) para no
// repetir el bug real corregido 2026-09-04 en
// HomeBanner/DocuSmartTopBanner/etc. (gradientes con azul fijo que
// ignoraban el "Color de acento" elegido).
private object BottomBarSizes {
    val BarCorner = 22.dp
    val BarVerticalPadding = 6.dp    // feedback 2026-09-06 (2da vuelta): más delgada aún
    val ItemBox = 52.dp
    val ItemCorner = ItemBox / 2     // siempre circular, activo e inactivo
    // Sobresale la mitad del propio círculo (26dp = ItemBox/2), no la mitad
    // del valor anterior -- feedback 2026-09-06 (2da vuelta): "el botón no
    // sale de la barra, la idea es que salga la mitad".
    val LiftOffset = -(ItemBox / 2)
    val ActiveIconSize = 22.dp
    val InactiveIconSize = 18.dp
}

@Composable
fun DocuSmartBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    if (currentRoute == null || currentRoute !in routesWithBottomBar) return

    val barShape = RoundedCornerShape(
        topStart = BottomBarSizes.BarCorner,
        topEnd = BottomBarSizes.BarCorner
    )
    // Feedback 2026-09-06 (3ra vuelta): con la superficie neutra (surface/
    // surfaceVariant) la barra "se perdía" contra el fondo animado al hacer
    // scroll -- se le da un tinte suave del propio Color de acento (mismo
    // que usan HomeBanner/DocuSmartTopBanner/la pastilla activa) para que
    // combine con el resto de la app sin importar qué acento se elija,
    // manteniéndolo bastante más tenue que la pastilla (que usa el acento a
    // toda intensidad) para que esta siga contrastando y destacando encima.
    val accent = MaterialTheme.colorScheme.primary
    val barTopColor = lerp(MaterialTheme.colorScheme.surface, accent, 0.14f)
    val barBottomColor = lerp(MaterialTheme.colorScheme.surfaceVariant, accent, 0.24f)
    val surfaceGradient = listOf(barTopColor, barBottomColor)
    // Borde superior un poco más oscuro que el acento, para separar
    // visualmente la barra del contenido que se ve detrás (mismo pedido).
    val barBorderColor = lerp(accent, Color.Black, 0.3f).copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Bug real encontrado 2026-09-06 ("arcos negros" reportados por
            // el usuario en las esquinas de la barra): el hijo de abajo se
            // recorta a `barShape` (redondeado solo arriba), así que las dos
            // esquinitas triangulares FUERA de esa curva -- dentro del
            // rectángulo de este Box exterior pero fuera de la forma
            // redondeada -- quedaban sin pintar. Con el Scaffold en
            // `containerColor = Color.Transparent` (para dejar ver el fondo
            // animado), esas esquinas dejaban ver el fondo de la ventana de
            // la Activity (negro) en vez del tono de la barra. Se pinta acá,
            // en el Box exterior SIN recortar, con el mismo tono que la
            // parada superior del degradado de abajo, para que esas
            // esquinas combinen en vez de quedar transparentes o desentonar.
            .background(barTopColor),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Superficie del bar -- se ajusta al alto real del Row (el único
        // hijo sin matchParentSize), en vez de un alto fijo adivinado que
        // podía chocar con el navigationBarsPadding real del dispositivo y
        // recortar el título (bug real encontrado 2026-09-06: con un alto
        // fijo, en 3-botones el padding del sistema dejaba muy poco alto
        // disponible y el label quedaba sin espacio). El círculo activo
        // sigue sobresaliendo por overflow natural (Box no recorta a sus
        // hijos por defecto), sin reservarle a Scaffold espacio extra.
        //
        // Sin `.shadow()`: con esquinas redondeadas solo arriba y una
        // elevación alta (18dp) heredada del diseño de referencia, el
        // renderer dibujaba una sombra que se perdía contra el fondo
        // transparente. En su lugar, un borde superior (ver barBorderColor
        // arriba) separa visualmente la barra del contenido -- más simple y
        // predecible que una sombra sobre una forma con esquinas mixtas.
        Box(
            Modifier
                .matchParentSize()
                .clip(barShape)
                .background(Brush.verticalGradient(surfaceGradient))
                .border(width = 1.5.dp, color = barBorderColor, shape = barShape)
        )

        // Items
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = BottomBarSizes.BarVerticalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                val label = stringResource(item.labelRes)
                BottomNavAnimatedItem(
                    icon = item.icon,
                    label = label,
                    active = isSelected,
                    onClick = { if (!isSelected) onNavigate(item.navigateRoute) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavAnimatedItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Springs suaves y sin rebote (feedback 2026-09-05: la versión anterior
    // con DampingRatioMediumBouncy se sentía brusca/con rebote elástico) --
    // DampingRatioLowBouncy asienta con una transición suave, casi sin
    // overshoot. Duraciones subidas de nuevo (feedback 2026-09-06: "no pasa
    // tan rápido") para que la transición se note, no sea instantánea.
    val liftSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = 130f
    )
    val colorTween = tween<Color>(durationMillis = 480, easing = FastOutSlowInEasing)
    val sizeTween = tween<Dp>(durationMillis = 480, easing = FastOutSlowInEasing)

    val lift by animateDpAsState(
        targetValue = if (active) BottomBarSizes.LiftOffset else 0.dp,
        animationSpec = liftSpec,
        label = "lift"
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 130f),
        label = "scale"
    )
    val iconSize by animateDpAsState(
        targetValue = if (active) BottomBarSizes.ActiveIconSize else BottomBarSizes.InactiveIconSize,
        animationSpec = sizeTween,
        label = "iconSize"
    )
    val iconColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = colorTween,
        label = "iconColor"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "pillAlpha"
    )

    val pillGradient = rememberAccentGradient()
    val pillShadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.selectable(
            selected = active,
            onClick = onClick,
            role = Role.Tab,
            interactionSource = interaction,
            indication = null            // el propio lift es el feedback
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val circleShape = RoundedCornerShape(BottomBarSizes.ItemCorner)
        Box(
            Modifier
                .offset(y = lift)
                .scale(scale)
                .size(BottomBarSizes.ItemBox)
                .shadow(
                    elevation = (14 * pillAlpha).dp,
                    shape = circleShape,
                    ambientColor = pillShadowColor,
                    spotColor = pillShadowColor
                )
                .clip(circleShape)
                .background(
                    Brush.linearGradient(pillGradient.map { it.copy(alpha = pillAlpha) })
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
        }
        // El título solo aparece para la pestaña activa (feedback
        // 2026-09-06: "ya no tiene los títulos... agrégalos cuando se haga
        // clic"), con una pequeña demora tras el ícono para que la
        // revelación se sienta en dos tiempos, no todo instantáneo a la vez
        // (feedback del mismo mensaje: "que tenga una demora y no pase tan
        // rápido").
        AnimatedVisibility(
            visible = active,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 260,
                    delayMillis = 160,
                    easing = FastOutSlowInEasing
                )
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = 260,
                    delayMillis = 160,
                    easing = FastOutSlowInEasing
                ),
                initialOffsetY = { it / 2 }
            ),
            exit = fadeOut(animationSpec = tween(durationMillis = 160)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 160),
                    targetOffsetY = { it / 2 }
                )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
