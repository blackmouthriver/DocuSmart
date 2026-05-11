package com.docsmart.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.docsmart.R
import com.docsmart.core.navegation.NavRoutes

data class BottomNavItem(
    val labelRes: Int,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(R.string.nav_home,      NavRoutes.Home.route,      Icons.Rounded.Home,         Icons.Rounded.Home),
    BottomNavItem(R.string.nav_library,   NavRoutes.Library.route,   Icons.Rounded.LibraryBooks, Icons.Rounded.LibraryBooks),
    BottomNavItem(R.string.nav_converter, NavRoutes.Converter.route, Icons.Rounded.SwapHoriz,    Icons.Rounded.SwapHoriz),
    BottomNavItem(R.string.nav_pdf,       NavRoutes.PdfTools.route,  Icons.Rounded.PictureAsPdf, Icons.Rounded.PictureAsPdf),
    BottomNavItem(R.string.nav_settings,  NavRoutes.Settings.route,  Icons.Rounded.Settings,     Icons.Rounded.Settings)
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
                        imageVector = if (isSelected) item.selectedIcon
                        else item.unselectedIcon,
                        contentDescription = stringResource(item.labelRes)
                    )
                },
                label = { Text(text = stringResource(item.labelRes)) }
            )
        }
    }
}