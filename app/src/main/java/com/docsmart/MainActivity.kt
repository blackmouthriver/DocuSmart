package com.docsmart

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.docsmart.core.ads.AdManager
import com.docsmart.core.navegation.DocuSmartNavGraph
import com.docsmart.core.navegation.NavRoutes
import com.docsmart.core.ui.LanguageManager
import com.docsmart.core.ui.components.DocuSmartBottomBar
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
import androidx.appcompat.app.AppCompatActivity
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
    private var adsInitialized  = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (permission, granted) ->
            Timber.d("Permiso $permission: $granted")
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // ── Aplicar idioma guardado antes de crear la Activity ──
        val prefs = newBase.getSharedPreferences("docusmart_language", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "es") ?: "es"
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        externalFileUri = resolveExternalIntent(intent)
        requestStoragePermissions()
        enableEdgeToEdge()
        requestAdsConsentThenInitializeAds()

        setContent {
            val currentTheme by themeManager.currentTheme.collectAsState()
            val currentAccentColor by themeManager.accentColor.collectAsState()
            val currentLanguage by languageManager.currentLanguage.collectAsState()
            val isSystemDark = isSystemInDarkTheme()

            val isDarkTheme = when (currentTheme) {
                AppTheme.DARK -> true
                else -> false
            }
            val useSystemTheme = currentTheme == AppTheme.SYSTEM

            // ── Reiniciar Activity al cambiar idioma ──
            var previousLanguage by remember { mutableStateOf(currentLanguage) }
            LaunchedEffect(currentLanguage) {
                if (currentLanguage != previousLanguage) {
                    previousLanguage = currentLanguage
                    // Reiniciar para aplicar el nuevo idioma
                    val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                }
            }

            DocuSmartTheme(
                darkTheme = isDarkTheme,
                useSystemTheme = useSystemTheme,
                accentColor = currentAccentColor
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
                    val stillWaiting = currentRoute == null ||
                        currentRoute == NavRoutes.SplashMouthBlack.route ||
                        currentRoute == NavRoutes.SplashDocuSmart.route ||
                        currentRoute == NavRoutes.Onboarding.route
                    if (stillWaiting) return@LaunchedEffect
                    navController.navigate(NavRoutes.Viewer.createRoute(uri.toString()))
                    externalFileUri = null
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
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
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        DocuSmartNavGraph(
                            navController = navController,
                            themeManager = themeManager,
                            languageManager = languageManager
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = resolveExternalIntent(intent)
        if (uri != null) {
            Timber.d("onNewIntent: URI externa = $uri")
            externalFileUri = uri
        }
    }

    private fun resolveExternalIntent(intent: Intent?): Uri? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        return try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            uri
        } catch (e: Exception) {
            Timber.w("No se pudo persistir permiso: ${e.message}")
            uri
        }
    }

    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    ) != PackageManager.PERMISSION_GRANTED
                ) permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_VIDEO
                ) != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
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

        val debugSettings = if (BuildConfig.DEBUG) {
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
        } else null

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
                        Timber.w("UMP: error mostrando formulario de consentimiento — ${formError.message}")
                    }
                    initializeAdsIfAllowed(consentInformation)
                }
            },
            { requestConsentError ->
                Timber.w("UMP: error actualizando info de consentimiento — ${requestConsentError.message}")
                initializeAdsIfAllowed(consentInformation)
            }
        )
    }

    private fun initializeAdsIfAllowed(consentInformation: ConsentInformation) {
        if (adsInitialized || !consentInformation.canRequestAds()) return
        adsInitialized = true

        val testDeviceIds = if (BuildConfig.DEBUG) {
            listOf("EB3ECF44CF3E05437B137D30F852213B")
        } else {
            emptyList()
        }
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
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
}