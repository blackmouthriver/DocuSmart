package com.docsmart.core.navegation

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.docsmart.R
import com.docsmart.core.analytics.DocuSmartAnalytics
import com.docsmart.core.data.canonicalMediaUri
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.core.ui.components.toContentUri
import com.docsmart.core.ui.theme.ThemeManager
import com.docsmart.features.agenda.presentation.AgendaScreen
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.presentation.ConverterScreen
import com.docsmart.features.home.presentation.HomeScreen
import com.docsmart.features.library.presentation.LibraryScreen
import com.docsmart.features.library.presentation.TrashScreen
import com.docsmart.features.onboarding.presentation.OnboardingScreen
import com.docsmart.features.onboarding.presentation.hasCompletedOnboarding
import com.docsmart.features.pdftools.presentation.PdfToolsScreen
import com.docsmart.features.premium.presentation.PremiumScreen
import com.docsmart.features.scanner.presentation.QrCreatorScreen
import com.docsmart.features.scanner.presentation.QrHistoryScreen
import com.docsmart.features.scanner.presentation.QrReaderScreen
import com.docsmart.features.scanner.presentation.ScanResultDocumentActions
import com.docsmart.features.scanner.presentation.ScanResultScreen
import com.docsmart.features.scanner.presentation.ScannerScreen
import com.docsmart.features.security.presentation.PdfPasswordScreen
import com.docsmart.features.security.presentation.SecurityMenuScreen
import com.docsmart.features.security.presentation.SecurityScreen
import com.docsmart.features.settings.presentation.SettingsScreen
import com.docsmart.features.splash.presentation.SplashDocuSmartScreen
import com.docsmart.features.splash.presentation.SplashMouthBlackScreen
import com.docsmart.features.study.presentation.StudyScreen
import com.docsmart.features.viewer.presentation.ViewerScreen
import timber.log.Timber
import java.io.File

@Composable
fun DocuSmartNavGraph(
    navController: NavHostController,
    themeManager: ThemeManager,
    languageManager: LanguageManager,
) {
    // Antes no había ninguna transición configurada -- NavHost usaba el
    // comportamiento por defecto de Navigation-Compose (un corte seco entre
    // pantallas, sin animación deliberada). Se agregó una transición
    // consistente tipo "shared axis" (deslizamiento horizontal + fundido) en
    // las 4 direcciones de navegación, aplicada globalmente a las ~30
    // pantallas del grafo sin tener que tocar cada `composable {}` una por
    // una. Es puramente de movimiento -- no cambia ningún color ni estilo.
    val transitionSpec = tween<Float>(280)
    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(280)

    // logScreenView centralizado acá en vez de en cada pantalla individual
    // (~17 destinos) -- un único listener de Navigation-Compose cubre todo
    // el grafo sin tocar cada `composable {}` uno por uno.
    DisposableEffect(navController) {
        val listener =
            androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                destination.route?.let { route ->
                    DocuSmartAnalytics.logScreenView(screenNameForRoute(route))
                }
            }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.SplashMouthBlack.route,
        enterTransition = {
            slideInHorizontally(slideSpec) { it / 4 } + fadeIn(transitionSpec)
        },
        exitTransition = {
            slideOutHorizontally(slideSpec) { -it / 4 } + fadeOut(transitionSpec)
        },
        popEnterTransition = {
            slideInHorizontally(slideSpec) { -it / 4 } + fadeIn(transitionSpec)
        },
        popExitTransition = {
            slideOutHorizontally(slideSpec) { it / 4 } + fadeOut(transitionSpec)
        },
    ) {
        splashMouthBlackComposable(navController)
        splashDocuSmartComposable(navController)
        onboardingComposable(navController)
        homeComposable(navController)
        libraryComposable(navController)
        viewerComposable(navController)

        // ── Converter ─────────────────────────────────────────────────────────
        composable(
            route = NavRoutes.Converter.route,
            arguments =
                listOf(
                    navArgument("initialType") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("initialFileUri") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("initialFileCategory") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            ConverterScreen(
                initialType = backStackEntry.arguments?.getString("initialType"),
                initialFileUri = backStackEntry.arguments?.getString("initialFileUri"),
                initialFileCategory = backStackEntry.arguments?.getString("initialFileCategory"),
                onOpenDocument = { path -> navController.navigate(NavRoutes.Viewer.createRoute(path)) },
            )
        }

        // ── PDF Tools ─────────────────────────────────────────────────────────
        composable(
            route = NavRoutes.PdfTools.route,
            arguments =
                listOf(
                    navArgument("initialTool") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("initialFileUri") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            PdfToolsScreen(
                initialTool = backStackEntry.arguments?.getString("initialTool"),
                initialFileUri = backStackEntry.arguments?.getString("initialFileUri"),
            )
        }

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
                onBack = { navController.popBackStack() },
                onSecureFolder = { navController.navigate(NavRoutes.SecureFolder.route) },
                onPdfPassword = { navController.navigate(NavRoutes.PdfPassword.route) },
            )
        }

        // ── Carpeta Segura ────────────────────────────────────────────────────
        composable(
            route = NavRoutes.SecureFolder.route,
            arguments =
                listOf(
                    navArgument("pendingFileUri") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            SecurityScreen(
                onBack = { navController.popBackStack() },
                pendingFileUri = backStackEntry.arguments?.getString("pendingFileUri"),
                // Hallazgo #53 (revisión general 2026-09-16): navega al Visor
                // con la copia efímera de vista previa (ruta local en
                // cacheDir), sin restaurar el archivo de Carpeta Segura.
                onPreviewFile = { path -> navController.navigate(NavRoutes.Viewer.createRoute(path)) },
            )
        }

        // ── PDF Password ──────────────────────────────────────────────────────
        composable(NavRoutes.PdfPassword.route) {
            PdfPasswordScreen(onBack = { navController.popBackStack() })
        }

        // ── Study ─────────────────────────────────────────────────────────────
        composable(
            route = NavRoutes.Study.route,
            arguments =
                listOf(
                    navArgument("tab") {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument("openNoteId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            StudyScreen(
                onBack = { navController.popBackStack() },
                initialTab = backStackEntry.arguments?.getInt("tab") ?: 0,
                openNoteId = backStackEntry.arguments?.getString("openNoteId"),
                onOpenAgenda = { navController.navigate(NavRoutes.Agenda.createRoute()) },
            )
        }

        // ── Agenda (HU-65) ────────────────────────────────────────────────────
        composable(
            route = NavRoutes.Agenda.route,
            arguments =
                listOf(
                    navArgument("openEventId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            AgendaScreen(
                onBack = { navController.popBackStack() },
                openEventId = backStackEntry.arguments?.getString("openEventId"),
            )
        }

        // ── QR Reader ─────────────────────────────────────────────────────────
        composable(NavRoutes.QrReader.route) {
            QrReaderScreen(
                onBack = { navController.popBackStack() },
                onHistoryClick = { navController.navigate(NavRoutes.QrHistory.route) },
            )
        }

        // ── QR Creator ────────────────────────────────────────────────────────
        composable(
            route = NavRoutes.QrCreator.route,
            arguments =
                listOf(
                    navArgument("initialFileUri") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("initialFileType") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("initialFileName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { backStackEntry ->
            QrCreatorScreen(
                onBack = { navController.popBackStack() },
                initialFileUri = backStackEntry.arguments?.getString("initialFileUri"),
                initialFileType = backStackEntry.arguments?.getString("initialFileType"),
                initialFileName = backStackEntry.arguments?.getString("initialFileName"),
                onHistoryClick = { navController.navigate(NavRoutes.QrHistory.route) },
            )
        }

        // ── Historial de QR (HU-44) ──────────────────────────────────────────
        composable(NavRoutes.QrHistory.route) {
            QrHistoryScreen(onBack = { navController.popBackStack() })
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
private val SCREEN_NAMES_BY_ROUTE =
    mapOf(
        "splash_mouthblack" to "SplashMouthBlack",
        "splash_docusmart" to "SplashDocuSmart",
        "onboarding" to "Onboarding",
        "home" to "Home",
        "library" to "Library",
        "viewer" to "Viewer",
        "converter" to "Converter",
        "pdf_tools" to "PdfTools",
        "settings" to "Settings",
        "premium" to "Premium",
        "scanner" to "Scanner",
        "scan_result" to "ScanResult",
        "security" to "SecurityMenu",
        "secure_folder" to "SecureFolder",
        "pdf_password" to "PdfPassword",
        "study" to "Study",
        "qr_reader" to "QrReader",
        "qr_creator" to "QrCreator",
        "qr_history" to "QrHistory",
        "trash" to "Trash",
    )

private fun screenNameForRoute(route: String): String {
    val base = route.substringBefore("?").substringBefore("/")
    return SCREEN_NAMES_BY_ROUTE[base] ?: route
}

private fun DocumentType.toConverterCategoryOrNull(): String? =
    when (this) {
        DocumentType.IMAGE -> "Imagen"
        DocumentType.PDF, DocumentType.OCR -> "PDF" // OCR es un PDF escaneado
        DocumentType.WORD -> "Word"
        DocumentType.EXCEL -> "Excel"
        DocumentType.POWERPOINT -> "PowerPoint"
        DocumentType.TEXT, DocumentType.ZIP -> null
    }

private fun DocumentType.toQrFileType(): String = if (this == DocumentType.IMAGE) "image" else "document"

private fun NavHostController.navigateToConvert(document: DocumentUiModel) {
    // Hallazgo real de la revisión adversarial de la octava ronda (Alta):
    // el fix de G6 le agregó `launchSingleTop=true` a esta función, pero
    // sus argumentos VARÍAN por llamada (un `document` distinto cada vez,
    // desde Biblioteca/Visor/Home) -- con la versión de Navigation-Compose
    // de este proyecto (2.8.4), cuando `launchSingleTop` encuentra el
    // mismo destino ya en el tope del back stack, REUTILIZA la entrada
    // existente con sus argumentos ORIGINALES, no los nuevos. Escenario
    // real: Convertir sobre el documento A, volver, Convertir sobre el
    // documento B sin que Convertidor salga del tope -- mostraría el
    // documento A otra vez. Se revierte a `navigate()` simple; el guard
    // de doble-toque de G6 queda solo en los destinos de Home cuyos
    // argumentos NO varían (ver homeComposable() más abajo).
    navigate(
        NavRoutes.Converter.createRoute(
            // Bug real encontrado 2026-09-14 (revisión pre-fusión HU-42):
            // `document.id` puede ser una ruta absoluta sin esquema (ver
            // comentario de `toContentUri()`), que `Uri.parse()` no
            // reconstruye como URI válido -- hay que normalizar siempre.
            initialFileUri = document.toContentUri().toString(),
            initialFileCategory = document.type.toConverterCategoryOrNull(),
        ),
    )
}

// Hallazgo real de la revisión general 2026-09-16 (cuarta pasada): a
// diferencia de Convertir/OCR/Firmar/Mover a Carpeta Segura (que solo leen
// el archivo dentro del propio proceso vía ContentResolver, donde un
// `file://` crudo funciona sin problema), "Crear QR" incrusta la Uri TAL
// CUAL como el contenido de texto del código -- al escanearlo y tocar
// "Abrir documento", se lanza un Intent.ACTION_VIEW externo con ese
// `file://`, que en un dispositivo con targetSdk 36 dispara
// FileUriExposedException (atrapada en silencio, sin aviso). Para un
// documento propio de la app (id = ruta absoluta) hay que envolverlo con
// FileProvider ANTES de generar el QR, igual que ya hace
// ViewerViewModel.shareableUri() para "Compartir".
private fun safeShareableUriOrNull(
    context: Context,
    document: DocumentUiModel,
): Uri? {
    if (document.id.startsWith("content://")) return Uri.parse(document.id)
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(document.id))
    } catch (e: IllegalArgumentException) {
        // Ruta fuera de las carpetas declaradas en file_provider_paths.xml
        // (ej. un archivo de Carpeta Segura) -- no hay forma segura de
        // compartirlo por QR.
        Timber.w(e, "safeShareableUriOrNull: sin Uri compartible para ${document.name}")
        null
    }
}

private fun NavHostController.navigateToQrCreator(
    context: Context,
    document: DocumentUiModel,
) {
    val safeUri = safeShareableUriOrNull(context, document)
    if (safeUri == null) {
        Toast.makeText(
            context,
            context.getString(R.string.qr_document_not_shareable),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    // Ver nota de navigateToConvert() más arriba -- mismo motivo, se
    // revierte launchSingleTop acá también (argumentos varían por
    // documento).
    navigate(
        NavRoutes.QrCreator.createRoute(
            initialFileUri = safeUri.toString(),
            initialFileType = document.type.toQrFileType(),
            initialFileName = document.name,
        ),
    )
}

// ── Accesos directos a OCR/Firmar/Carpeta Segura desde un archivo ya
// elegido (backlog UX 2026-08-30/09-10, HU-42) -- mismo mecanismo de
// "atajo de navegación con parámetros opcionales" que Convertir/Crear QR
// (HU-UX-01/02) de arriba, extendido a Biblioteca/Recientes/Visor/lista de
// sesión del Escáner (el mismo `DocumentContextMenu` compartido por los 4).
// Ver nota de navigateToConvert() más arriba -- mismo motivo en las 3
// funciones de abajo, se revierte launchSingleTop (argumentos varían por
// documento).
private fun NavHostController.navigateToOcr(document: DocumentUiModel) {
    navigate(NavRoutes.PdfTools.createRoute(initialTool = "OCR", initialFileUri = document.toContentUri().toString()))
}

private fun NavHostController.navigateToSign(document: DocumentUiModel) {
    navigate(NavRoutes.PdfTools.createRoute(initialTool = "SIGN", initialFileUri = document.toContentUri().toString()))
}

private fun NavHostController.navigateToSecureFolder(document: DocumentUiModel) {
    navigate(NavRoutes.SecureFolder.createRoute(pendingFileUri = document.toContentUri().toString()))
}

// ── Splash 1: MouthBlack ────────────────────────────────────────────────────
private fun NavGraphBuilder.splashMouthBlackComposable(navController: NavHostController) {
    composable(NavRoutes.SplashMouthBlack.route) {
        SplashMouthBlackScreen(
            onFinished = {
                navController.navigate(NavRoutes.SplashDocuSmart.route) {
                    popUpTo(NavRoutes.SplashMouthBlack.route) { inclusive = true }
                }
            },
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
                val destination =
                    if (!hasCompletedOnboarding(context)) {
                        NavRoutes.Onboarding.route
                    } else {
                        NavRoutes.Home.route
                    }

                navController.navigate(destination) {
                    popUpTo(NavRoutes.SplashDocuSmart.route) { inclusive = true }
                }
            },
        )
    }
}

// ── Onboarding (primera vez) ─────────────────────────────────────────────────
private fun NavGraphBuilder.onboardingComposable(navController: NavHostController) {
    composable(NavRoutes.Onboarding.route) {
        OnboardingScreen(
            onFinished = {
                // Hallazgo real de la auditoría general 2026-09-17
                // (séptima ronda, Media -- O1): reingresar acá desde
                // Ajustes ("Ver tutorial") deja el back stack
                // [Home, Settings, Onboarding] -- popUpTo(Onboarding)
                // solo saca Onboarding, empujando un Home NUEVO encima
                // ([Home, Settings, Home]), así que había que presionar
                // Atrás 3 veces para salir en vez de 1. Apuntar el
                // popUpTo a Home (con launchSingleTop) limpia Settings+
                // Onboarding y reutiliza el Home ya existente cuando se
                // reingresa desde Ajustes, sin cambiar nada en el
                // arranque en frío (Home aún no existe, se agrega igual).
                navController.navigate(NavRoutes.Home.route) {
                    popUpTo(NavRoutes.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
        )
    }
}

// ── Home ──────────────────────────────────────────────────────────────────
private fun NavGraphBuilder.homeComposable(navController: NavHostController) {
    composable(NavRoutes.Home.route) {
        val context = LocalContext.current
        HomeScreen(
            onOpenFile = { pickedUri ->
                try {
                    // Persistir también escritura (ver comentario en
                    // HomeScreen.kt openFileLauncher) -- sin esto, borrar
                    // este documento más tarde desde Biblioteca/Recientes
                    // fallaba en silencio.
                    val flags =
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(pickedUri, flags)
                } catch (e: Exception) {
                    Timber.e("Error permiso: ${e.message}")
                }
                // Bug real reportado 2026-09-11: un documento abierto con
                // "Abrir" (picker de documentos, autoridad
                // com.android.providers.media.documents) queda registrado en
                // el historial con SU PROPIO Uri de documento SAF -- pero ese
                // mismo archivo físico, si ya está indexado por MediaStore
                // (caso típico: cualquier cosa en Descargas), también aparece
                // en Biblioteca → Dispositivo con un Uri de MediaStore
                // DISTINTO (content://media/...). Al "Eliminar" desde una
                // lista solo se marca ese id puntual en la papelera -- el
                // otro id, del mismo archivo, nunca se filtra y sigue
                // apareciendo, dando la falsa impresión de que "Eliminar" no
                // hizo nada. `MediaStore.getMediaUri()` (API 29+) resuelve el
                // Uri de documento SAF a su Uri real de MediaStore cuando
                // corresponde -- pero ese resultado puede venir en la
                // colección genérica `content://media/external/file/<id>`,
                // distinta de la colección específica
                // (`.../downloads/<id>`, `.../images/media/<id>`) que usan
                // loadDocumentsFromDownloads()/loadImagesFromMediaStore()
                // para el MISMO archivo -- mismo `_id`, string distinto.
                // canonicalMediaUri() normaliza ambos casos a la misma forma.
                val resolved =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            MediaStore.getMediaUri(context, pickedUri) ?: pickedUri
                        } catch (e: Exception) {
                            Timber.w("No se pudo resolver a Uri de MediaStore: ${e.message}")
                            pickedUri
                        }
                    } else {
                        pickedUri
                    }
                val uri = canonicalMediaUri(resolved)
                navController.navigate(NavRoutes.Viewer.createRoute(uri.toString()))
            },
            // Hallazgo real de la auditoría general 2026-09-17 (octava
            // ronda, Media -- G6): ninguna de estas lambdas usaba
            // `launchSingleTop`, ni había guard/debounce en
            // QuickAccessGrid.kt/DocuSmartDocumentItem -- un doble-toque
            // rápido apilaba el mismo destino 2 veces, y "Atrás" reaparecía
            // en una segunda instancia de la misma pantalla en vez de
            // volver a Home. Ya se usaba `launchSingleTop` en otros puntos
            // de este mismo archivo (ver onboardingComposable); se extiende
            // acá SOLO a los destinos cuyos argumentos NUNCA varían entre
            // llamadas -- la revisión adversarial de esta misma ronda
            // encontró que Navigation-Compose 2.8.4 reutiliza los
            // argumentos ORIGINALES (no los nuevos) cuando launchSingleTop
            // encuentra el mismo destino ya en el tope, así que se dejó
            // sin este guard a onStudy/onDocumentClick (tab/documentId
            // varían por toque) y a los atajos que navegan con un
            // documento (onConvertDocument y el resto, más abajo).
            onScan = { navController.navigate(NavRoutes.Scanner.route) { launchSingleTop = true } },
            onConvert = { navController.navigate(NavRoutes.Converter.createRoute()) { launchSingleTop = true } },
            // Acceso rápido "Img→PDF": abre el Convertidor ya en Imagen→PDF
            // en vez del genérico -- antes iba al mismo lugar que el botón
            // "Convertir" grande, sin ninguna diferencia real entre los dos.
            onQuickConvertImageToPdf = {
                navController.navigate(
                    NavRoutes.Converter.createRoute(ConversionType.IMAGE_TO_PDF.name),
                ) { launchSingleTop = true }
            },
            onSecurity = { navController.navigate(NavRoutes.Security.route) { launchSingleTop = true } },
            // Sin launchSingleTop: `tab` varía según qué acceso rápido se
            // toque (Estudio/Notas/Pomodoro comparten esta misma lambda
            // con tabs distintos).
            onStudy = { tab -> navController.navigate(NavRoutes.Study.createRoute(tab)) },
            onSeeAll = { navController.navigate(NavRoutes.Library.route) { launchSingleTop = true } },
            onQrReader = { navController.navigate(NavRoutes.QrReader.route) { launchSingleTop = true } },
            // Bug real corregido 2026-09-08: navegaba con `NavRoutes.QrCreator.route`,
            // la plantilla cruda de la ruta ("...&initialFileName={initialFileName}")
            // -- al no pasar por `createRoute()`, esos marcadores de posición
            // literales quedaban como el valor real del argumento (se veía el
            // texto "{initialFileName}" en la pantalla en vez de un campo vacío).
            onQrCreator = { navController.navigate(NavRoutes.QrCreator.createRoute()) { launchSingleTop = true } },
            onTrash = { navController.navigate(NavRoutes.Trash.route) { launchSingleTop = true } },
            // Sin launchSingleTop: `documentId` varía según qué tarjeta de
            // Recientes se toque.
            onDocumentClick = { documentId ->
                navController.navigate(NavRoutes.Viewer.createRoute(documentId))
            },
            onConvertDocument = { doc -> navController.navigateToConvert(doc) },
            onCreateQrFromDocument = { doc -> navController.navigateToQrCreator(context, doc) },
            onMakeSearchableDocument = { doc -> navController.navigateToOcr(doc) },
            onSignDocument = { doc -> navController.navigateToSign(doc) },
            onMoveToSecureFolderDocument = { doc -> navController.navigateToSecureFolder(doc) },
        )
    }
}

// ── Library ───────────────────────────────────────────────────────────────
private fun NavGraphBuilder.libraryComposable(navController: NavHostController) {
    composable(NavRoutes.Library.route) {
        val context = LocalContext.current
        LibraryScreen(
            onDocumentClick = { documentId ->
                val isUri =
                    documentId.startsWith("content://") ||
                        documentId.startsWith("file://") ||
                        documentId.startsWith("/")
                if (isUri && documentId.startsWith("content://")) {
                    try {
                        val uri = Uri.parse(documentId)
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (e: Exception) {
                        Timber.w("No se pudo persistir permiso: ${e.message}")
                    }
                }
                navController.navigate(NavRoutes.Viewer.createRoute(documentId))
            },
            onTrashClick = { navController.navigate(NavRoutes.Trash.route) },
            onConvertClick = { doc -> navController.navigateToConvert(doc) },
            onCreateQrClick = { doc -> navController.navigateToQrCreator(context, doc) },
            onMakeSearchableClick = { doc -> navController.navigateToOcr(doc) },
            onSignClick = { doc -> navController.navigateToSign(doc) },
            onMoveToSecureFolderClick = { doc -> navController.navigateToSecureFolder(doc) },
        )
    }
}

// ── Viewer ────────────────────────────────────────────────────────────────
private fun NavGraphBuilder.viewerComposable(navController: NavHostController) {
    composable(
        route = NavRoutes.Viewer.route,
        arguments =
            listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val encodedId =
            backStackEntry.arguments
                ?.getString("documentId") ?: return@composable
        val documentId =
            if (encodedId.startsWith("content%3A")) {
                Uri.decode(encodedId)
            } else {
                encodedId
            }
        Timber.d("Viewer: documentId final = $documentId")
        val context = LocalContext.current
        ViewerScreen(
            documentId = documentId,
            onBack = {
                // Siempre intentar finish si el previous destination también es Viewer
                val prevRoute = navController.previousBackStackEntry?.destination?.route
                Timber.d("Viewer onBack: prevRoute=$prevRoute")
                if (prevRoute == null || prevRoute.startsWith("viewer")) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    navController.popBackStack()
                }
            },
            onConvertClick = { doc -> navController.navigateToConvert(doc) },
            onCreateQrClick = { doc -> navController.navigateToQrCreator(context, doc) },
            onMakeSearchableClick = { doc -> navController.navigateToOcr(doc) },
            onSignClick = { doc -> navController.navigateToSign(doc) },
            onMoveToSecureFolderClick = { doc -> navController.navigateToSecureFolder(doc) },
        )
    }
}

// ── Settings ──────────────────────────────────────────────────────────────
private fun NavGraphBuilder.settingsComposable(
    navController: NavHostController,
    themeManager: ThemeManager,
    languageManager: LanguageManager,
) {
    composable(NavRoutes.Settings.route) {
        SettingsScreen(
            themeManager = themeManager,
            languageManager = languageManager,
            onPremiumClick = { navController.navigate(NavRoutes.Premium.route) },
            onShowOnboarding = {
                navController.navigate(NavRoutes.Onboarding.route) {
                    popUpTo(NavRoutes.Settings.route) { inclusive = false }
                }
            },
        )
    }
}

// ── Scanner ───────────────────────────────────────────────────────────────
private fun NavGraphBuilder.scannerComposable(navController: NavHostController) {
    composable(NavRoutes.Scanner.route) { backStackEntry ->
        val scanResultEntry =
            remember(backStackEntry) {
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
            },
        )
    }
}

// ── Scan Result ───────────────────────────────────────────────────────────
private fun NavGraphBuilder.scanResultComposable(navController: NavHostController) {
    composable(NavRoutes.ScanResult.route) { backStackEntry ->
        val scannerEntry =
            remember(backStackEntry) {
                navController.getBackStackEntry(NavRoutes.Scanner.route)
            }
        val uriStrings =
            scannerEntry.savedStateHandle
                .get<List<String>>("scanned_uris") ?: emptyList()
        val isPdf = scannerEntry.savedStateHandle.get<Boolean>("is_pdf") ?: false
        val uris = uriStrings.map { Uri.parse(it) }
        val context = LocalContext.current
        ScanResultScreen(
            scannedUris = uris,
            isPdf = isPdf,
            onBack = { navController.popBackStack() },
            onDone = {
                navController.navigate(NavRoutes.Home.route) {
                    popUpTo(NavRoutes.Scanner.route) { inclusive = true }
                }
            },
            onPremiumClick = { navController.navigate(NavRoutes.Premium.route) },
            // Backlog UX §35 (feedback del usuario tras probar la primera
            // versión de la lista de sesión): mismas acciones que ya tiene
            // el menú "⋮" de Biblioteca/Recientes -- los archivos de la
            // sesión son siempre rutas absolutas (generados por la app),
            // nunca `content://`, así que no hace falta el permiso
            // persistente que sí necesita Library para MediaStore.
            onOpenDocument = { documentId -> navController.navigate(NavRoutes.Viewer.createRoute(documentId)) },
            onConvertDocument = { doc -> navController.navigateToConvert(doc) },
            onCreateQrFromDocument = { doc -> navController.navigateToQrCreator(context, doc) },
            documentActions =
                ScanResultDocumentActions(
                    onMakeSearchable = { doc -> navController.navigateToOcr(doc) },
                    onSign = { doc -> navController.navigateToSign(doc) },
                    onMoveToSecureFolder = { doc -> navController.navigateToSecureFolder(doc) },
                ),
        )
    }
}
