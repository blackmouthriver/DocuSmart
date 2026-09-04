package com.docsmart.core.navegation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.theme.ThemeManager
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.presentation.ConverterScreen
import com.docsmart.features.home.presentation.HomeScreen
import com.docsmart.features.library.presentation.LibraryScreen
import com.docsmart.features.onboarding.presentation.OnboardingScreen
import com.docsmart.features.onboarding.presentation.hasCompletedOnboarding
import com.docsmart.features.pdftools.presentation.PdfToolsScreen
import com.docsmart.features.premium.presentation.PremiumScreen
import com.docsmart.features.scanner.presentation.QrCreatorScreen
import com.docsmart.features.scanner.presentation.QrReaderScreen
import com.docsmart.features.scanner.presentation.ScanResultScreen
import com.docsmart.features.scanner.presentation.ScannerScreen
import com.docsmart.features.security.presentation.PdfPasswordScreen
import com.docsmart.features.security.presentation.SecurityScreen
import com.docsmart.features.settings.presentation.SettingsScreen
import com.docsmart.features.splash.presentation.SplashDocuSmartScreen
import com.docsmart.features.splash.presentation.SplashMouthBlackScreen
import com.docsmart.features.study.presentation.StudyScreen
import com.docsmart.features.library.presentation.TrashScreen
import com.docsmart.features.viewer.presentation.ViewerScreen
import com.docsmart.features.security.presentation.SecurityMenuScreen
import com.docsmart.features.security.presentation.PdfPasswordScreen
import com.docsmart.core.analytics.DocuSmartAnalytics
import timber.log.Timber

@Composable
fun DocuSmartNavGraph(
    navController  : NavHostController,
    themeManager   : ThemeManager,
    languageManager: LanguageManager
) {
    // Antes no había ninguna transición configurada -- NavHost usaba el
    // comportamiento por defecto de Navigation-Compose (un corte seco entre
    // pantallas, sin animación deliberada). Se agregó una transición
    // consistente tipo "shared axis" (deslizamiento horizontal + fundido) en
    // las 4 direcciones de navegación, aplicada globalmente a las ~30
    // pantallas del grafo sin tener que tocar cada `composable {}` una por
    // una. Es puramente de movimiento -- no cambia ningún color ni estilo.
    val transitionSpec = tween<Float>(280)
    val slideSpec       = tween<androidx.compose.ui.unit.IntOffset>(280)

    // logScreenView centralizado acá en vez de en cada pantalla individual
    // (~17 destinos) -- un único listener de Navigation-Compose cubre todo
    // el grafo sin tocar cada `composable {}` uno por uno.
    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            destination.route?.let { route ->
                DocuSmartAnalytics.logScreenView(screenNameForRoute(route))
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    NavHost(
        navController      = navController,
        startDestination   = NavRoutes.SplashMouthBlack.route,
        enterTransition    = {
            slideInHorizontally(slideSpec) { it / 4 } + fadeIn(transitionSpec)
        },
        exitTransition     = {
            slideOutHorizontally(slideSpec) { -it / 4 } + fadeOut(transitionSpec)
        },
        popEnterTransition = {
            slideInHorizontally(slideSpec) { -it / 4 } + fadeIn(transitionSpec)
        },
        popExitTransition  = {
            slideOutHorizontally(slideSpec) { it / 4 } + fadeOut(transitionSpec)
        }
    ) {
        splashMouthBlackComposable(navController)
        splashDocuSmartComposable(navController)
        onboardingComposable(navController)
        homeComposable(navController)
        libraryComposable(navController)
        viewerComposable(navController)

        // ── Converter ─────────────────────────────────────────────────────────
        composable(
            route     = NavRoutes.Converter.route,
            arguments = listOf(
                navArgument("initialType") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                },
                navArgument("initialFileUri") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                },
                navArgument("initialFileCategory") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            ConverterScreen(
                initialType         = backStackEntry.arguments?.getString("initialType"),
                initialFileUri      = backStackEntry.arguments?.getString("initialFileUri"),
                initialFileCategory = backStackEntry.arguments?.getString("initialFileCategory")
            )
        }

        // ── PDF Tools ─────────────────────────────────────────────────────────
        composable(NavRoutes.PdfTools.route) { PdfToolsScreen() }

        settingsComposable(navController, themeManager, languageManager)

        // ── Premium ───────────────────────────────────────────────────────────
        composable(NavRoutes.Premium.route) {
            PremiumScreen(onClose = { navController.popBackStack() })
        }

        scannerComposable(navController)
        scanResultComposable(navController)

        // ── Security Menu ─────────────────────────────────────────────────────
        composable(NavRoutes.Security.route) {
            SecurityMenuScreen(
                onBack         = { navController.popBackStack() },
                onSecureFolder = { navController.navigate(NavRoutes.SecureFolder.route) },
                onPdfPassword  = { navController.navigate(NavRoutes.PdfPassword.route) }
            )
        }

        // ── Carpeta Segura ────────────────────────────────────────────────────
        composable(NavRoutes.SecureFolder.route) {
            SecurityScreen(onBack = { navController.popBackStack() })
        }

        // ── PDF Password ──────────────────────────────────────────────────────
        composable(NavRoutes.PdfPassword.route) {
            PdfPasswordScreen(onBack = { navController.popBackStack() })
        }

        // ── Study ─────────────────────────────────────────────────────────────
        composable(
            route = NavRoutes.Study.route,
            arguments = listOf(navArgument("tab") {
                type = NavType.IntType
                defaultValue = 0
            })
        ) { backStackEntry ->
            StudyScreen(
                onBack     = { navController.popBackStack() },
                initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
            )
        }

        // ── QR Reader ─────────────────────────────────────────────────────────
        composable(NavRoutes.QrReader.route) {
            QrReaderScreen(onBack = { navController.popBackStack() })
        }

        // ── QR Creator ────────────────────────────────────────────────────────
        composable(
            route     = NavRoutes.QrCreator.route,
            arguments = listOf(
                navArgument("initialFileUri") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                },
                navArgument("initialFileType") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                },
                navArgument("initialFileName") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            QrCreatorScreen(
                onBack          = { navController.popBackStack() },
                initialFileUri  = backStackEntry.arguments?.getString("initialFileUri"),
                initialFileType = backStackEntry.arguments?.getString("initialFileType"),
                initialFileName = backStackEntry.arguments?.getString("initialFileName")
            )
        }

        // ── Papelera (RF-VIS-07) ──────────────────────────────────────────────
        composable(NavRoutes.Trash.route) {
            TrashScreen(onBack = { navController.popBackStack() })
        }
    }
}

// ── Atajos "Convertir"/"Crear QR" desde un archivo ya elegido (backlog UX
// 2026-08-30, HU-UX-01/02) -- traducen el `DocumentType` de un archivo real
// (Biblioteca/Recientes/Visor) al vocabulario que espera cada pantalla de
// destino. `null` en el Convertidor significa "este tipo no tiene ninguna
// conversión definida hoy" (Texto, ZIP) -- se navega igual pero sin
// precargar el archivo, cae al comportamiento manual de siempre.
// El `route` de un destino conserva el patrón con placeholders (p.ej.
// "converter?initialType={initialType}&...", "viewer/{documentId}"), nunca
// los valores reales -- se recorta antes del primer "?"/"/" para agrupar
// todas las variantes de una misma pantalla bajo un solo nombre en Firebase.
private val SCREEN_NAMES_BY_ROUTE = mapOf(
    "splash_mouthblack" to "SplashMouthBlack",
    "splash_docusmart"  to "SplashDocuSmart",
    "onboarding"        to "Onboarding",
    "home"              to "Home",
    "library"           to "Library",
    "viewer"            to "Viewer",
    "converter"         to "Converter",
    "pdf_tools"         to "PdfTools",
    "settings"          to "Settings",
    "premium"           to "Premium",
    "scanner"           to "Scanner",
    "scan_result"       to "ScanResult",
    "security"          to "SecurityMenu",
    "secure_folder"     to "SecureFolder",
    "pdf_password"      to "PdfPassword",
    "study"             to "Study",
    "qr_reader"         to "QrReader",
    "qr_creator"        to "QrCreator",
    "trash"             to "Trash"
)

private fun screenNameForRoute(route: String): String {
    val base = route.substringBefore("?").substringBefore("/")
    return SCREEN_NAMES_BY_ROUTE[base] ?: route
}

private fun DocumentType.toConverterCategoryOrNull(): String? = when (this) {
    DocumentType.IMAGE                 -> "Imagen"
    DocumentType.PDF, DocumentType.OCR -> "PDF" // OCR es un PDF escaneado
    DocumentType.WORD                  -> "Word"
    DocumentType.EXCEL                 -> "Excel"
    DocumentType.POWERPOINT            -> "PowerPoint"
    DocumentType.TEXT, DocumentType.ZIP -> null
}

private fun DocumentType.toQrFileType(): String =
    if (this == DocumentType.IMAGE) "image" else "document"

private fun NavHostController.navigateToConvert(document: DocumentUiModel) {
    navigate(
        NavRoutes.Converter.createRoute(
            initialFileUri      = document.id,
            initialFileCategory = document.type.toConverterCategoryOrNull()
        )
    )
}

private fun NavHostController.navigateToQrCreator(document: DocumentUiModel) {
    navigate(
        NavRoutes.QrCreator.createRoute(
            initialFileUri  = document.id,
            initialFileType = document.type.toQrFileType(),
            initialFileName = document.name
        )
    )
}

// ── Splash 1: MouthBlack ────────────────────────────────────────────────────
private fun NavGraphBuilder.splashMouthBlackComposable(navController: NavHostController) {
    composable(NavRoutes.SplashMouthBlack.route) {
        SplashMouthBlackScreen(
            onFinished = {
                navController.navigate(NavRoutes.SplashDocuSmart.route) {
                    popUpTo(NavRoutes.SplashMouthBlack.route) { inclusive = true }
                }
            }
        )
    }
}

// ── Splash 2: DocuSmart → decide si muestra onboarding ──────────────────────
private fun NavGraphBuilder.splashDocuSmartComposable(navController: NavHostController) {
    composable(NavRoutes.SplashDocuSmart.route) {
        val context = LocalContext.current
        SplashDocuSmartScreen(
            onFinished = {
                // Primera vez → Onboarding / Ya visto → Home
                val destination = if (!hasCompletedOnboarding(context))
                    NavRoutes.Onboarding.route
                else
                    NavRoutes.Home.route

                navController.navigate(destination) {
                    popUpTo(NavRoutes.SplashDocuSmart.route) { inclusive = true }
                }
            }
        )
    }
}

// ── Onboarding (primera vez) ─────────────────────────────────────────────────
private fun NavGraphBuilder.onboardingComposable(navController: NavHostController) {
    composable(NavRoutes.Onboarding.route) {
        OnboardingScreen(
            onFinished = {
                navController.navigate(NavRoutes.Home.route) {
                    popUpTo(NavRoutes.Onboarding.route) { inclusive = true }
                }
            }
        )
    }
}

// ── Home ──────────────────────────────────────────────────────────────────
private fun NavGraphBuilder.homeComposable(navController: NavHostController) {
    composable(NavRoutes.Home.route) {
        val context = LocalContext.current
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
            onConvert   = { navController.navigate(NavRoutes.Converter.createRoute()) },
            // Acceso rápido "Img→PDF": abre el Convertidor ya en Imagen→PDF
            // en vez del genérico -- antes iba al mismo lugar que el botón
            // "Convertir" grande, sin ninguna diferencia real entre los dos.
            onQuickConvertImageToPdf = {
                navController.navigate(
                    NavRoutes.Converter.createRoute(ConversionType.IMAGE_TO_PDF.name)
                )
            },
            onSecurity  = { navController.navigate(NavRoutes.Security.route) },
            onStudy     = { tab -> navController.navigate(NavRoutes.Study.createRoute(tab)) },
            onSeeAll    = { navController.navigate(NavRoutes.Library.route) },
            onQrReader  = { navController.navigate(NavRoutes.QrReader.route) },
            onQrCreator = { navController.navigate(NavRoutes.QrCreator.route) },
            onTrash     = { navController.navigate(NavRoutes.Trash.route) },
            onDocumentClick = { documentId ->
                navController.navigate(NavRoutes.Viewer.createRoute(documentId))
            },
            onConvertDocument      = { doc -> navController.navigateToConvert(doc) },
            onCreateQrFromDocument = { doc -> navController.navigateToQrCreator(doc) }
        )
    }
}

// ── Library ───────────────────────────────────────────────────────────────
private fun NavGraphBuilder.libraryComposable(navController: NavHostController) {
    composable(NavRoutes.Library.route) {
        val context = LocalContext.current
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
            },
            onTrashClick    = { navController.navigate(NavRoutes.Trash.route) },
            onConvertClick  = { doc -> navController.navigateToConvert(doc) },
            onCreateQrClick = { doc -> navController.navigateToQrCreator(doc) }
        )
    }
}

// ── Viewer ────────────────────────────────────────────────────────────────
private fun NavGraphBuilder.viewerComposable(navController: NavHostController) {
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
        val context = LocalContext.current
        ViewerScreen(
            documentId = documentId,
            onBack     = {
                // Siempre intentar finish si el previous destination también es Viewer
                val prevRoute = navController.previousBackStackEntry?.destination?.route
                Timber.d("Viewer onBack: prevRoute=$prevRoute")
                if (prevRoute == null || prevRoute.startsWith("viewer")) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    navController.popBackStack()
                }
            },
            onConvertClick  = { doc -> navController.navigateToConvert(doc) },
            onCreateQrClick = { doc -> navController.navigateToQrCreator(doc) }
        )
    }
}

// ── Settings ──────────────────────────────────────────────────────────────
private fun NavGraphBuilder.settingsComposable(
    navController  : NavHostController,
    themeManager   : ThemeManager,
    languageManager: LanguageManager
) {
    composable(NavRoutes.Settings.route) {
        SettingsScreen(
            themeManager      = themeManager,
            languageManager   = languageManager,
            onPremiumClick    = { navController.navigate(NavRoutes.Premium.route) },
            onShowOnboarding  = {
                navController.navigate(NavRoutes.Onboarding.route) {
                    popUpTo(NavRoutes.Settings.route) { inclusive = false }
                }
            }
        )
    }
}

// ── Scanner ───────────────────────────────────────────────────────────────
private fun NavGraphBuilder.scannerComposable(navController: NavHostController) {
    composable(NavRoutes.Scanner.route) { backStackEntry ->
        val scanResultEntry = remember(backStackEntry) {
            navController.getBackStackEntry(NavRoutes.Scanner.route)
        }
        ScannerScreen(
            onBack = { navController.popBackStack() },
            onScanComplete = { uris ->
                scanResultEntry.savedStateHandle["scanned_uris"] =
                    uris.map { it.toString() }
                scanResultEntry.savedStateHandle["is_pdf"] =
                    uris.size == 1 && uris.first().toString().endsWith(".pdf")
                DocuSmartAnalytics.logScanCompleted(uris.size)
                navController.navigate(NavRoutes.ScanResult.route)
            }
        )
    }
}

// ── Scan Result ───────────────────────────────────────────────────────────
private fun NavGraphBuilder.scanResultComposable(navController: NavHostController) {
    composable(NavRoutes.ScanResult.route) { backStackEntry ->
        val scannerEntry = remember(backStackEntry) {
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
}