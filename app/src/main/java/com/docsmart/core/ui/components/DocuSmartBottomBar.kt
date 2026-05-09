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
    BottomNavItem(
        label = "Inicio",
        route = NavRoutes.Home.route,
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Rounded.Home
    ),
    BottomNavItem(
        label = "Biblioteca",
        route = NavRoutes.Library.route,
        selectedIcon = Icons.Rounded.LibraryBooks,
        unselectedIcon = Icons.Rounded.LibraryBooks
    ),
    BottomNavItem(
        label = "Convertir",
        route = NavRoutes.Converter.route,
        selectedIcon = Icons.Rounded.SwapHoriz,
        unselectedIcon = Icons.Rounded.SwapHoriz
    ),
    BottomNavItem(
        label = "PDF",
        route = NavRoutes.PdfTools.route,
        selectedIcon = Icons.Rounded.PictureAsPdf,
        unselectedIcon = Icons.Rounded.PictureAsPdf
    ),
    BottomNavItem(
        label = "Ajustes",
        route = NavRoutes.Settings.route,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Rounded.Settings
    )
)

@Composable
fun DocuSmartBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            val isSelected = currentRoute == item.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(text = item.label) }
            )
        }
    }
}