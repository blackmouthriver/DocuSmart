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
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import androidx.appcompat.app.AppCompatActivity
@AndroidEntryPoint

class MainActivity : AppCompatActivity() {

    @Inject lateinit var adManager: AdManager
    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var languageManager: LanguageManager

    private var externalFileUri: Uri? = null

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

        setContent {
            val currentTheme by themeManager.currentTheme.collectAsState()
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
                useSystemTheme = useSystemTheme
            ) {
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStack?.destination?.route

                LaunchedEffect(Unit) {
                    externalFileUri?.let { uri ->
                        navController.addOnDestinationChangedListener { _, destination, _ ->
                            if (destination.route == NavRoutes.Home.route) {
                                navController.navigate(
                                    NavRoutes.Viewer.createRoute(uri.toString())
                                )
                                externalFileUri = null
                            }
                        }
                    }
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
}