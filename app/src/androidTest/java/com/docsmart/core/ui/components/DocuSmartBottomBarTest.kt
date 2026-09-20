package com.docsmart.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.navegation.NavRoutes
import com.docsmart.core.ui.test.forceLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: barra inferior de navegación. La pestaña activa muestra el título
 * (Text) y las inactivas exponen el nombre como contentDescription; solo
 * aparece en las 5 rutas principales.
 */
class DocuSmartBottomBarTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val route = mutableStateOf<String?>(null)

    private fun label(resId: Int): String =
        forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(resId)

    private fun setBar(
        initialRoute: String?,
        onNavigate: (String) -> Unit = {},
    ) {
        route.value = initialRoute
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(LocalContext provides localizedContext) {
                MaterialTheme {
                    DocuSmartBottomBar(currentRoute = route.value, onNavigate = onNavigate)
                }
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun rutaHome_muestraTituloActivoYNombresDeLasOtrasPestanas() {
        setBar(NavRoutes.Home.route)

        composeRule.onNodeWithText(label(R.string.nav_home)).assertIsSelected()
        composeRule.onNodeWithContentDescription(label(R.string.nav_library)).assertExists()
        composeRule.onNodeWithContentDescription(label(R.string.nav_converter)).assertExists()
        composeRule.onNodeWithContentDescription(label(R.string.nav_pdf)).assertExists()
        composeRule.onNodeWithContentDescription(label(R.string.nav_settings)).assertExists()
    }

    @Test
    fun tocarPestanasInactivas_navegaALaRutaResueltaDeCadaUna() {
        val navigated = mutableListOf<String>()
        setBar(NavRoutes.Home.route, onNavigate = { navigated += it })

        composeRule.onNodeWithContentDescription(label(R.string.nav_library)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.nav_converter)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.nav_pdf)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.nav_settings)).performClick()
        composeRule.waitForIdle()

        // Convertir navega a la ruta ya resuelta (sin el placeholder literal).
        assertEquals(
            listOf(
                NavRoutes.Library.route,
                NavRoutes.Converter.createRoute(),
                NavRoutes.PdfTools.route,
                NavRoutes.Settings.route,
            ),
            navigated,
        )
    }

    @Test
    fun tocarLaPestanaActiva_noNavega() {
        var navigated = false
        setBar(NavRoutes.Home.route, onNavigate = { navigated = true })

        composeRule.onNodeWithText(label(R.string.nav_home)).performClick()
        composeRule.waitForIdle()

        assertTrue("Tocar la pestaña activa no debería navegar", !navigated)
    }

    @Test
    fun rutaNula_noDibujaLaBarra() {
        setBar(null)

        composeRule.onAllNodesWithText(label(R.string.nav_home)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(label(R.string.nav_library)).assertCountEquals(0)
    }

    @Test
    fun rutaFueraDeLasPrincipales_noDibujaLaBarra() {
        setBar(NavRoutes.Trash.route)

        composeRule.onAllNodesWithText(label(R.string.nav_home)).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(label(R.string.nav_settings)).assertCountEquals(0)
    }

    @Test
    fun cambiarDeRuta_mueveElTituloALaPestanaNueva() {
        setBar(NavRoutes.Home.route)

        // Cada ruta principal (incluida la plantilla del Convertidor) activa su pestaña.
        listOf(
            NavRoutes.Library.route to R.string.nav_library,
            NavRoutes.Converter.route to R.string.nav_converter,
            NavRoutes.PdfTools.route to R.string.nav_pdf,
            NavRoutes.Settings.route to R.string.nav_settings,
        ).forEach { (nextRoute, labelRes) ->
            composeRule.runOnIdle { route.value = nextRoute }
            waitForText(label(labelRes))
            composeRule.onNodeWithText(label(labelRes)).assertIsSelected()
        }
    }
}
