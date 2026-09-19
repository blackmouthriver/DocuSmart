package com.docsmart

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.docsmart.core.ads.AdManager
import com.docsmart.core.navegation.DocuSmartNavGraph
import com.docsmart.core.navegation.NavRoutes
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.components.DocuSmartAnimatedBackground
import com.docsmart.core.ui.components.DocuSmartBottomBar
import com.docsmart.core.ui.resolveLanguageCode
import com.docsmart.core.ui.theme.AppTheme
import com.docsmart.core.ui.theme.DocuSmartTheme
import com.docsmart.core.ui.theme.ThemeManager
import com.docsmart.core.ui.util.isRunningUnderInstrumentation
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var adManager: AdManager

    @Inject lateinit var themeManager: ThemeManager

    @Inject lateinit var languageManager: LanguageManager

    // mutableStateOf, no un `var` plano: con android:launchMode="singleTask"
    // (ver bug real de abajo), onNewIntent() puede setear un nuevo valor con
    // la Activity ya compuesta -- necesita disparar recomposición, no solo
    // quedar disponible para la próxima vez que se lea.
    private var externalFileUri by mutableStateOf<Uri?>(null)

    // HU-65: id del evento de Agenda a abrir directo al tocar su
    // notificación de recordatorio (AC3) -- mismo patrón que
    // `externalFileUri` de arriba (mutableStateOf, no un `var` plano, para
    // que onNewIntent() dispare recomposición con la Activity ya compuesta).
    private var pendingAgendaEventId by mutableStateOf<String?>(null)

    // Backlog UX #52: id de la nota a abrir directo al tocar su
    // notificación de recordatorio de repaso -- mismo patrón que
    // `pendingAgendaEventId` de arriba.
    private var pendingNoteId by mutableStateOf<String?>(null)
    private var adsInitialized = false

    // Guarda de una sola vez para el LaunchedEffect(currentRoute) de más
    // abajo que difiere requestStoragePermissions() -- ver hallazgo O2.
    private var storagePermissionRequested = false

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            permissions.forEach { (permission, granted) ->
                Timber.d("Permiso $permission: $granted")
            }
        }

    override fun attachBaseContext(newBase: Context) {
        // ── Aplicar idioma guardado antes de crear la Activity ──
        // Sin idioma guardado (instalación nueva) se usa el del dispositivo si
        // está soportado (RF-SET-06) -- antes caía siempre en "es" aquí mientras
        // LanguageManager mostraba otro en el selector.
        val prefs = newBase.getSharedPreferences("docusmart_language", Context.MODE_PRIVATE)
        val languageCode =
            resolveLanguageCode(
                saved = prefs.getString("language", null),
                deviceLanguage = newBase.resources.configuration.locales[0]?.language,
            )
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solo en un arranque real: tras rotación/cambio de idioma/proceso
        // muerto Android re-entrega el mismo Intent y se reabriría el Visor
        // (o Agenda/Nota) sobre lo que el usuario ya cerró.
        if (shouldConsumeLaunchIntent(savedInstanceState != null, intent.flags)) {
            externalFileUri = resolveExternalIntent(intent)
            pendingAgendaEventId = intent.getStringExtra(EXTRA_OPEN_AGENDA_EVENT_ID)
            pendingNoteId = intent.getStringExtra(EXTRA_OPEN_NOTE_ID)
        }
        // Hallazgo real de la auditoría general 2026-09-17 (séptima
        // ronda, Media -- O2): antes se pedía acá mismo, sincrónicamente,
        // antes de que se pintara cualquier UI propia -- lo primero que
        // veía un usuario nuevo podía ser el diálogo de permisos del
        // sistema superpuesto a la animación del splash, sin ningún
        // contexto previo (a diferencia de POST_NOTIFICATIONS, que ya se
        // pide en el momento de uso real). Se difiere con el mismo
        // mecanismo ya usado para externalFileUri/pendingAgendaEventId
        // más abajo (LaunchedEffect(currentRoute) + isStillOnSplashOrOnboarding()).
        // Bug real encontrado 2026-09-06 ("línea/franja blanca" reportada
        // por el usuario tapando botones en Convertir/Herramientas PDF):
        // enableEdgeToEdge() sin parámetros usa SystemBarStyle.auto(...) por
        // defecto, que en ciertas versiones/API de Android dibuja un scrim
        // blanco semitransparente del sistema detrás de la barra de
        // navegación para asegurar contraste. Con la barra inferior anterior
        // (más alta) ese scrim quedaba oculto debajo de su propia superficie
        // opaca; al achicar la barra (feedback de la misma sesión), el
        // scrim empezó a asomar por encima, tapando el contenido. La propia
        // barra ya resuelve su contraste con MaterialTheme.colorScheme, así
        // que no hace falta el scrim del sistema -- se fuerza transparente.
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
        )
        requestAdsConsentThenInitializeAds()

        setContent {
            val currentTheme by themeManager.currentTheme.collectAsState()
            val currentAccentColor by themeManager.accentColor.collectAsState()
            val currentFontScale by themeManager.fontScale.collectAsState()
            val animatedBackgroundEnabled by themeManager.animatedBackgroundEnabled.collectAsState()
            val currentLanguage by languageManager.currentLanguage.collectAsState()
            val isSystemDark = isSystemInDarkTheme()

            val isDarkTheme =
                when (currentTheme) {
                    AppTheme.DARK -> true
                    else -> false
                }
            val useSystemTheme = currentTheme == AppTheme.SYSTEM

            // ── Reiniciar Activity al cambiar idioma ──
            var previousLanguage by remember { mutableStateOf(currentLanguage) }
            LaunchedEffect(currentLanguage) {
                if (currentLanguage != previousLanguage) {
                    previousLanguage = currentLanguage
                    // Recrear (no lanzar una Activity nueva con CLEAR_TASK): el
                    // NavController restaura su back stack, así que el usuario
                    // sigue en Ajustes en vez de volver al splash.
                    // attachBaseContext() vuelve a leer el idioma guardado.
                    recreate()
                }
            }

            DocuSmartTheme(
                darkTheme = isDarkTheme,
                useSystemTheme = useSystemTheme,
                accentColor = currentAccentColor,
                fontScale = currentFontScale.scale,
            ) {
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStack?.destination?.route

                // Bug real (reportado 2026-08-29): al abrir un PDF/imagen desde
                // Drive o WhatsApp con la app ya corriendo, Android creaba una
                // SEGUNDA instancia de MainActivity encima de la que ya estaba
                // en Inicio (sin android:launchMode, el intent-filter VIEW usa
                // el modo "standard" por defecto) -- el visor mostraba el
                // archivo correctamente, pero al volver atrás quedaba una copia
                // de Inicio "pegada" debajo en vez de cerrar la app. Con
                // launchMode="singleTask" ahora Android reutiliza esta misma
                // instancia vía onNewIntent(), así que solo falta reaccionar
                // acá al cambio de externalFileUri en vez de esperar a llegar
                // a Home (ese caso solo aplicaba al arranque en frío).
                LaunchedEffect(externalFileUri, currentRoute) {
                    val uri = externalFileUri ?: return@LaunchedEffect
                    // Espera a que termine splash/onboarding (arranque en frío)
                    // antes de redirigir -- en caliente (singleTask +
                    // onNewIntent) currentRoute ya es Home/Library/etc. y esto
                    // navega de inmediato.
                    if (isStillOnSplashOrOnboarding(currentRoute)) return@LaunchedEffect
                    navController.navigate(NavRoutes.Viewer.createRoute(uri.toString()))
                    externalFileUri = null
                }

                // HU-65: mismo mecanismo que arriba, para el tap de una
                // notificación de recordatorio de Agenda (AC3).
                LaunchedEffect(pendingAgendaEventId, currentRoute) {
                    val eventId = pendingAgendaEventId ?: return@LaunchedEffect
                    if (isStillOnSplashOrOnboarding(currentRoute)) return@LaunchedEffect
                    navController.navigate(NavRoutes.Agenda.createRoute(openEventId = eventId))
                    pendingAgendaEventId = null
                }

                // Backlog UX #52: mismo mecanismo que arriba, para el tap de
                // una notificación de recordatorio de repaso de una nota
                // (AC2) -- aterriza en la pestaña Notas (tab=1) con esa nota
                // específica resaltada.
                LaunchedEffect(pendingNoteId, currentRoute) {
                    val noteId = pendingNoteId ?: return@LaunchedEffect
                    if (isStillOnSplashOrOnboarding(currentRoute)) return@LaunchedEffect
                    navController.navigate(NavRoutes.Study.createRoute(tab = 1, openNoteId = noteId))
                    pendingNoteId = null
                }

                // Hallazgo real de la auditoría general 2026-09-17 (séptima
                // ronda, Media -- O2): mismo mecanismo que arriba, pero para
                // el permiso de almacenamiento -- antes se pedía en onCreate()
                // antes de pintar cualquier UI propia, así que un usuario
                // nuevo podía ver el diálogo del sistema superpuesto al
                // splash sin ningún contexto. `storagePermissionRequested`
                // asegura que se pida una sola vez por creación de la
                // Activity, apenas se sale de splash/onboarding.
                LaunchedEffect(currentRoute) {
                    if (storagePermissionRequested) return@LaunchedEffect
                    if (isStillOnSplashOrOnboarding(currentRoute)) return@LaunchedEffect
                    storagePermissionRequested = true
                    requestStoragePermissions()
                }

                // Fondo animado (backlog UX 2026-09-06): capa 0 detrás de toda
                // la navegación, pintada una sola vez acá -- el Scaffold y los
                // Scaffold anidados de cada pantalla usan containerColor
                // transparente para dejarla ver (ver DocuSmartAnimatedBackground.kt
                // para la excepción del Visor, que mantiene fondo sólido fijo
                // por legibilidad de lectura).
                val backgroundColor = MaterialTheme.colorScheme.background
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    bottomBar = {
                        DocuSmartBottomBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    // El fondo comparte el mismo Box con padding que el
                    // contenido -- bug real 2026-09-06: cuando el fondo medía
                    // la pantalla completa (sin descontar la barra), una de
                    // las formas quedaba posicionada justo a la altura del
                    // borde superior de la barra y se veía como una franja de
                    // color pegada encima. Al medir el mismo alto que el
                    // contenido real (innerPadding ya descuenta la barra),
                    // las formas nunca llegan a esa zona.
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        if (animatedBackgroundEnabled) {
                            DocuSmartAnimatedBackground(modifier = Modifier.fillMaxSize())
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(backgroundColor),
                            )
                        }
                        DocuSmartNavGraph(
                            navController = navController,
                            themeManager = themeManager,
                            languageManager = languageManager,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!shouldConsumeLaunchIntent(isRestoringState = false, intentFlags = intent.flags)) return
        val uri = resolveExternalIntent(intent)
        if (uri != null) {
            Timber.d("onNewIntent: URI externa = $uri")
            externalFileUri = uri
        }
        intent.getStringExtra(EXTRA_OPEN_AGENDA_EVENT_ID)?.let { eventId ->
            Timber.d("onNewIntent: evento de Agenda $eventId")
            pendingAgendaEventId = eventId
        }
        intent.getStringExtra(EXTRA_OPEN_NOTE_ID)?.let { noteId ->
            Timber.d("onNewIntent: recordatorio de nota $noteId")
            pendingNoteId = noteId
        }
    }

    // Hallazgo real de seguridad (revisión general 2026-09-16): un Intent
    // VIEW de OTRA app instalada podía traer un `file://` (o cualquier otro
    // esquema) apuntando a `filesDir` interno de DocuSmart -- incluida
    // `secure/`, la Carpeta Segura -- y el Visor lo abría sin pedir PIN,
    // porque ViewerViewModel.resolveUri() lee cualquier `file://` con
    // File I/O directo, sin pasar por FileProvider. Solo un `content://`
    // (el único esquema que una app externa puede usar legítimamente para
    // ofrecer SU PROPIO archivo a DocuSmart) es un origen válido para un
    // Intent que viene de fuera; la navegación interna de la app (abrir un
    // documento generado por ella misma) nunca pasa por acá, usa
    // NavController.navigate() directo con el id ya resuelto.
    private fun resolveExternalIntent(intent: Intent?): Uri? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (!isAllowedExternalViewIntent(intent.action, uri.scheme)) {
            Timber.w("resolveExternalIntent: esquema no permitido desde un Intent externo -> ${uri.scheme}")
            return null
        }
        if (canPersistUriGrant(intent.flags)) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                Timber.w("No se pudo persistir permiso: ${e.javaClass.simpleName}")
            }
        }
        return uri
    }

    // Permiso de video corregido 2026-09-10 (hallazgo real al preparar la
    // declaración de permisos de fotos/video para Play Console):
    // READ_MEDIA_VIDEO se pedía acá y en LibraryScreen.kt sin que ninguna
    // función de la app use contenido de video (ConversionType no tiene
    // ningún tipo de video, no hay reproductor ni importador) -- pedir un
    // permiso que no se usa viola la política de Google de fotos/video y
    // no había forma honesta de justificarlo en el formulario.
    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_IMAGES,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // ── UMP: consentimiento de anuncios (UE/Reino Unido) ──────────────────────
    // AdManager.initialize() ya NO se dispara desde DocuSmartApplication --
    // requestConsentInfoUpdate() necesita una Activity real para poder
    // mostrar el formulario de consentimiento (por eso vive acá, no en
    // Application.onCreate()). canRequestAds() es falso hasta que se resuelve
    // esta llamada (con o sin formulario mostrado), así que MobileAds no se
    // inicializa hasta entonces -- nunca antes.
    private fun requestAdsConsentThenInitializeAds() {
        if (isRunningUnderInstrumentation()) return

        val debugSettings =
            if (BuildConfig.DEBUG) {
                ConsentDebugSettings.Builder(this)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    // Sin registrar el dispositivo como debug device, la
                    // simulación de geografía EEA no toma efecto en un
                    // dispositivo real (confirmado: sin este ID, canRequestAds()
                    // resolvía como si no aplicara GDPR, ignorando el override).
                    // Mismo ID que ya se usaba para AdMob en testDeviceIds
                    // -- UMP logueó exactamente este mismo hash al iniciar.
                    .addTestDeviceHashedId("EB3ECF44CF3E05437B137D30F852213B")
                    .build()
            } else {
                null
            }

        val paramsBuilder = ConsentRequestParameters.Builder()
        debugSettings?.let { paramsBuilder.setConsentDebugSettings(it) }
        val params = paramsBuilder.build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    if (formError != null) {
                        Timber.w("UMP: error mostrando formulario de consentimiento — ${formError.errorCode}")
                    }
                    initializeAdsIfAllowed(consentInformation)
                }
            },
            { requestConsentError ->
                Timber.w("UMP: error actualizando info de consentimiento — ${requestConsentError.errorCode}")
                initializeAdsIfAllowed(consentInformation)
            },
        )
    }

    private fun initializeAdsIfAllowed(consentInformation: ConsentInformation) {
        if (adsInitialized || !consentInformation.canRequestAds()) return
        adsInitialized = true

        val testDeviceIds =
            if (BuildConfig.DEBUG) {
                listOf("EB3ECF44CF3E05437B137D30F852213B")
            } else {
                emptyList()
            }
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build(),
        )
        // MobileAds.initialize() debe llamarse desde el hilo principal (documentado
        // por Google) -- lanzarlo en Dispatchers.IO hacía que, en el camino rápido
        // donde el SDK resuelve la inicialización desde caché, el callback de
        // finalización (que dispara InterstitialAd.load()) se ejecutara en el
        // mismo hilo IO en vez de pasar por el main looper, y esa llamada exige
        // hilo principal explícitamente -- crash real: "IllegalStateException:
        // #008 Must be called on the main UI thread." Se reprodujo de forma
        // confiable en el reinicio "en caliente" de la Activity al cambiar de
        // idioma (MainActivity se recrea con el proceso ya corriendo, callback
        // del SDK resuelto casi instantáneo) aunque el código es el mismo que
        // corre en cada arranque en frío de la app.
        lifecycleScope.launch {
            adManager.initialize()
        }
    }

    // Extraída para no duplicar esta misma condición en los 2 LaunchedEffect
    // de arranque en frío (URI externa + notificación de Agenda) -- de paso
    // baja la complejidad ciclomática de onCreate() al sacar la rama de acá.
    private fun isStillOnSplashOrOnboarding(route: String?): Boolean = isSplashOrOnboardingRoute(route)

    companion object {
        // HU-65: nombre de la extra que AgendaReminderReceiver pone en el
        // Intent de "abrir la app" de la notificación de recordatorio.
        const val EXTRA_OPEN_AGENDA_EVENT_ID = "open_agenda_event_id"

        // Backlog UX #52: mismo mecanismo, para NoteReminderReceiver.
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"
    }
}
