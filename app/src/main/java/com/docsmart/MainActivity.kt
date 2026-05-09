package com.docsmart

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.docsmart.core.ui.components.DocuSmartBottomBar
import com.docsmart.core.ui.theme.AppTheme
import com.docsmart.core.ui.theme.DocuSmartTheme
import com.docsmart.core.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var adManager: AdManager
    @Inject lateinit var themeManager: ThemeManager

    // ── Launcher de permisos ──────────────────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (permission, granted) ->
            Timber.d("Permiso $permission: $granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Solicitar permisos al iniciar la app ──────
        requestStoragePermissions()

        enableEdgeToEdge()
        setContent {
            val currentTheme by themeManager.currentTheme.collectAsState()
            val isSystemDark = isSystemInDarkTheme()

            val isDarkTheme = when (currentTheme) {
                AppTheme.DARK -> true
                else -> false
            }
            val useSystemTheme = currentTheme == AppTheme.SYSTEM

            DocuSmartTheme(
                darkTheme = isDarkTheme,
                useSystemTheme = useSystemTheme
            ) {
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStack?.destination?.route

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
                            themeManager = themeManager
                        )
                    }
                }
            }
        }
    }

    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    ) != PackageManager.PERMISSION_GRANTED
                ) permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }

            // Android 13+ — permisos granulares
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.READ_MEDIA_IMAGES)

            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_VIDEO
                ) != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.READ_MEDIA_VIDEO)

        } else {
            // Android 12 y menor
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.isNotEmpty()) {
            Timber.d("Solicitando permisos: $permissions")
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            Timber.d("Todos los permisos ya concedidos")
        }
    }
}