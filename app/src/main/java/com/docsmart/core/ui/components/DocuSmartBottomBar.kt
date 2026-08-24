package com.docsmart.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.docsmart.core.navegation.NavRoutes

data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem("Inicio",     NavRoutes.Home.route,      Icons.Rounded.Home,         Icons.Rounded.Home),
    BottomNavItem("Biblioteca", NavRoutes.Library.route,   Icons.Rounded.LibraryBooks, Icons.Rounded.LibraryBooks),
    BottomNavItem("Convertir",  NavRoutes.Converter.route, Icons.Rounded.SwapHoriz,    Icons.Rounded.SwapHoriz),
    BottomNavItem("PDF",        NavRoutes.PdfTools.route,  Icons.Rounded.PictureAsPdf, Icons.Rounded.PictureAsPdf),
    BottomNavItem("Ajustes",    NavRoutes.Settings.route,  Icons.Rounded.Settings,     Icons.Rounded.Settings)
)

// ── Solo mostrar en rutas principales ────────────────
private val routesWithBottomBar = setOf(
    NavRoutes.Home.route,
    NavRoutes.Library.route,
    NavRoutes.Converter.route,
    NavRoutes.PdfTools.route,
    NavRoutes.Settings.route
)

@Composable
fun DocuSmartBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    if (currentRoute == null || currentRoute !in routesWithBottomBar) return

    NavigationBar {
        bottomNavItems.forEach { item ->
            val isSelected = currentRoute == item.route
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) onNavigate(item.route)
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon
                        else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(text = item.label) }
            )
        }
    }
}
