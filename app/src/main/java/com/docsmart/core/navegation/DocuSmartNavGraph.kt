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
import com.docsmart.features.scanner.presentation.QrCreatorScreen
import com.docsmart.features.scanner.presentation.QrReaderScreen
import com.docsmart.features.scanner.presentation.ScanResultScreen
import com.docsmart.features.scanner.presentation.ScannerScreen
import com.docsmart.features.security.presentation.SecurityScreen
import com.docsmart.features.settings.presentation.SettingsScreen
import com.docsmart.features.splash.presentation.SplashDocuSmartScreen
import com.docsmart.features.splash.presentation.SplashMouthBlackScreen
import com.docsmart.features.study.presentation.StudyScreen
import com.docsmart.features.viewer.presentation.ViewerScreen
import timber.log.Timber

@Composable
fun DocuSmartNavGraph(
    navController  : NavHostController,
    themeManager   : ThemeManager,
    languageManager: LanguageManager
) {
    NavHost(
        navController    = navController,
        startDestination = NavRoutes.SplashMouthBlack.route
    ) {

        // ── Splash 1: MouthBlack ──────────────────────────────────────────────
        composable(NavRoutes.SplashMouthBlack.route) {
            SplashMouthBlackScreen(
                onFinished = {
                    navController.navigate(NavRoutes.SplashDocuSmart.route) {
                        popUpTo(NavRoutes.SplashMouthBlack.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Splash 2: DocuSmart ───────────────────────────────────────────────
        composable(NavRoutes.SplashDocuSmart.route) {
            SplashDocuSmartScreen(
                onFinished = {
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.SplashDocuSmart.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Home ──────────────────────────────────────────────────────────────
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
                onScan      = { navController.navigate(NavRoutes.Scanner.route) },
                onConvert   = { navController.navigate(NavRoutes.Converter.route) },
                onSecurity  = { navController.navigate(NavRoutes.Security.route) },
                onStudy     = { navController.navigate(NavRoutes.Study.route) },
                onSeeAll    = { navController.navigate(NavRoutes.Library.route) },
                onQrReader  = { navController.navigate(NavRoutes.QrReader.route) },  // ← NUEVO
                onQrCreator = { navController.navigate(NavRoutes.QrCreator.route) }, // ← NUEVO
                onDocumentClick = { documentId ->
                    navController.navigate(NavRoutes.Viewer.createRoute(documentId))
                }
            )
        }

        // ── Library ───────────────────────────────────────────────────────────
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

        // ── Viewer ────────────────────────────────────────────────────────────
        composable(
            route     = NavRoutes.Viewer.route,
            arguments = listOf(
                navArgument("documentId") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val encodedId  = backStackEntry.arguments
                ?.getString("documentId") ?: return@composable
            val documentId = if (encodedId.startsWith("content%3A"))
                Uri.decode(encodedId) else encodedId
            Timber.d("Viewer: documentId final = $documentId")
            ViewerScreen(
                documentId = documentId,
                onBack     = { navController.popBackStack() }
            )
        }

        // ── Converter ─────────────────────────────────────────────────────────
        composable(NavRoutes.Converter.route) { ConverterScreen() }

        // ── PDF Tools ─────────────────────────────────────────────────────────
        composable(NavRoutes.PdfTools.route) { PdfToolsScreen() }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                themeManager    = themeManager,
                languageManager = languageManager,
                onPremiumClick  = { navController.navigate(NavRoutes.Premium.route) }
            )
        }

        // ── Premium ───────────────────────────────────────────────────────────
        composable(NavRoutes.Premium.route) {
            PremiumScreen(onClose = { navController.popBackStack() })
        }

        // ── Scanner ───────────────────────────────────────────────────────────
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

        // ── Scan Result ───────────────────────────────────────────────────────
        composable(NavRoutes.ScanResult.route) {
            val scannerEntry = remember {
                navController.getBackStackEntry(NavRoutes.Scanner.route)
            }
            val uriStrings = scannerEntry.savedStateHandle
                .get<List<String>>("scanned_uris") ?: emptyList()
            val isPdf = scannerEntry.savedStateHandle.get<Boolean>("is_pdf") ?: false
            val uris  = uriStrings.map { Uri.parse(it) }
            ScanResultScreen(
                scannedUris = uris,
                isPdf       = isPdf,
                onBack      = { navController.popBackStack() },
                onDone      = {
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.Scanner.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Security ──────────────────────────────────────────────────────────
        composable(NavRoutes.Security.route) {
            SecurityScreen(onBack = { navController.popBackStack() })
        }

        // ── Study ─────────────────────────────────────────────────────────────
        composable(NavRoutes.Study.route) {
            StudyScreen(onBack = { navController.popBackStack() })
        }

        // ── QR Reader ─────────────────────────────────────────────────────────
        composable(NavRoutes.QrReader.route) {
            QrReaderScreen(onBack = { navController.popBackStack() })
        }

        // ── QR Creator ────────────────────────────────────────────────────────
        composable(NavRoutes.QrCreator.route) {
            QrCreatorScreen(onBack = { navController.popBackStack() })
        }
    }
}