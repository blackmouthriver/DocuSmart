package com.docsmart.core.navegation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.theme.ThemeManager
import com.docsmart.features.converter.presentation.ConverterScreen
import com.docsmart.features.home.presentation.HomeScreen
import com.docsmart.features.library.presentation.LibraryScreen
import com.docsmart.features.pdftools.presentation.PdfToolsScreen
import com.docsmart.features.premium.presentation.PremiumScreen
import com.docsmart.features.scanner.presentation.ScanResultScreen
import com.docsmart.features.scanner.presentation.ScannerScreen
import com.docsmart.features.settings.presentation.SettingsScreen
import com.docsmart.features.splash.presentation.SplashScreen
import com.docsmart.features.viewer.presentation.ViewerScreen
import com.docsmart.features.security.presentation.SecurityScreen
import com.docsmart.features.study.presentation.StudyScreen
import timber.log.Timber

@Composable
fun DocuSmartNavGraph(
    navController: NavHostController,
    themeManager: ThemeManager,
    languageManager: LanguageManager
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Splash.route
    ) {

        composable(NavRoutes.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Home.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            HomeScreen(
                onOpenFile = { uri ->
                    try {
                        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, flags)
                    } catch (e: Exception) {
                        Timber.e("Error permiso: ${e.message}")
                    }
                    navController.navigate(NavRoutes.Viewer.createRoute(uri.toString()))
                },
                onScan = {
                    navController.navigate(NavRoutes.Scanner.route)
                },
                onConvert = {
                    navController.navigate(NavRoutes.Converter.route)
                },
                onSecurity = {
                    navController.navigate(NavRoutes.Security.route)
                             },
                onDocumentClick = { documentId ->
                    navController.navigate(NavRoutes.Viewer.createRoute(documentId))
                },
                onStudy = {
                    navController.navigate(NavRoutes.Study.route)
                },
                onSeeAll = {
                    navController.navigate(NavRoutes.Library.route)
                }
            )
        }

        composable(NavRoutes.Library.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            LibraryScreen(
                onDocumentClick = { documentId ->
                    val isUri = documentId.startsWith("content://") ||
                            documentId.startsWith("file://") ||
                            documentId.startsWith("/")

                    if (isUri && documentId.startsWith("content://")) {
                        try {
                            val uri = Uri.parse(documentId)
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                            Timber.w("No se pudo persistir permiso: ${e.message}")
                        }
                    }
                    navController.navigate(NavRoutes.Viewer.createRoute(documentId))
                }
            )
        }

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

        composable(NavRoutes.Converter.route) {
            ConverterScreen()
        }

        composable(NavRoutes.PdfTools.route) {
            PdfToolsScreen()
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                themeManager = themeManager,
                languageManager = languageManager,
                onPremiumClick = {
                    navController.navigate(NavRoutes.Premium.route)
                }
            )
        }

        composable(NavRoutes.Premium.route) {
            PremiumScreen(
                onClose = { navController.popBackStack() }
            )
        }

        // ── Escáner ───────────────────────────────────
        composable(NavRoutes.Scanner.route) {
            val scanResultEntry = remember {
                navController.getBackStackEntry(NavRoutes.Scanner.route)
            }
            ScannerScreen(
                onBack = { navController.popBackStack() },
                onScanComplete = { uris ->
                    scanResultEntry.savedStateHandle["scanned_uris"] =
                        uris.map { it.toString() }
                    scanResultEntry.savedStateHandle["is_pdf"] =
                        uris.size == 1 && uris.first().toString().endsWith(".pdf")
                    navController.navigate(NavRoutes.ScanResult.route)
                }
            )
        }

        // ── Resultado del escaneo ─────────────────────
        composable(NavRoutes.ScanResult.route) {
            val scannerEntry = remember {
                navController.getBackStackEntry(NavRoutes.Scanner.route)
            }
            val uriStrings = scannerEntry.savedStateHandle
                .get<List<String>>("scanned_uris") ?: emptyList()
            val isPdf = scannerEntry.savedStateHandle
                .get<Boolean>("is_pdf") ?: false
            val uris = uriStrings.map { Uri.parse(it) }

            ScanResultScreen(
                scannedUris = uris,
                isPdf = isPdf,
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.Scanner.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Seguridad ─────────────────────────────────────────
        composable(NavRoutes.Security.route) {
            SecurityScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── Estudio ───────────────────────────────────────────
        composable(NavRoutes.Study.route) {
            StudyScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}