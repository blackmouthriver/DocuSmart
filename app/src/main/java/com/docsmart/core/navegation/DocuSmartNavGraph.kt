package com.docsmart.core.navegation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.docsmart.features.converter.presentation.ConverterScreen
import com.docsmart.features.home.presentation.HomeScreen
import com.docsmart.features.library.presentation.LibraryScreen
import com.docsmart.features.pdftools.presentation.PdfToolsScreen
import com.docsmart.features.premium.presentation.PremiumScreen
import com.docsmart.features.settings.presentation.SettingsScreen
import com.docsmart.features.splash.presentation.SplashScreen
import com.docsmart.features.viewer.presentation.ViewerScreen
import timber.log.Timber

@Composable
fun DocuSmartNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Splash.route
    ) {

        // ── Splash ────────────────────────────────────
        composable(NavRoutes.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.Splash.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        // ── Home ──────────────────────────────────────
        composable(NavRoutes.Home.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            HomeScreen(
                onOpenFile = { uri ->
                    try {
                        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, flags)

                        val permisos = context.contentResolver.persistedUriPermissions
                        Timber.d("Permisos activos: ${permisos.size}")
                        permisos.forEach {
                            Timber.d("  → ${it.uri} read=${it.isReadPermission}")
                        }
                    } catch (e: Exception) {
                        Timber.e("Error permiso: ${e.message}")
                    }
                    navController.navigate(
                        NavRoutes.Viewer.createRoute(uri.toString())
                    )
                },
                onConvert = {
                    navController.navigate(NavRoutes.Converter.route)
                },
                onDocumentClick = { documentId ->
                    navController.navigate(
                        NavRoutes.Viewer.createRoute(documentId)
                    )
                },
                onSeeAll = {
                    navController.navigate(NavRoutes.Library.route)
                }
            )
        }

        // ── Biblioteca ────────────────────────────────
        composable(NavRoutes.Library.route) {
            LibraryScreen(
                onDocumentClick = { documentId ->
                    navController.navigate(
                        NavRoutes.Viewer.createRoute(documentId)
                    )
                }
            )
        }

        // ── Visor ─────────────────────────────────────
        composable(
            route = NavRoutes.Viewer.route,
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val encodedId = backStackEntry.arguments
                ?.getString("documentId") ?: return@composable

            val documentId = if (encodedId.startsWith("content%3A")) {
                Uri.decode(encodedId)
            } else {
                encodedId
            }

            Timber.d("Viewer: documentId final = $documentId")

            ViewerScreen(
                documentId = documentId,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Convertidor ───────────────────────────────
        composable(NavRoutes.Converter.route) {
            ConverterScreen()
        }

        // ── PDF Tools ─────────────────────────────────
        composable(NavRoutes.PdfTools.route) {
            PdfToolsScreen()
        }

        // ── Ajustes ───────────────────────────────────
        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onPremiumClick = {
                    navController.navigate(NavRoutes.Premium.route)
                }
            )
        }

        // ── Premium ───────────────────────────────────
        composable(NavRoutes.Premium.route) {
            PremiumScreen(
                onClose = { navController.popBackStack() }
            )
        }
    }
}