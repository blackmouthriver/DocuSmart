package com.docsmart.features.premium.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.premium.domain.model.PremiumFeature
import com.docsmart.features.premium.domain.model.PremiumPlan
import com.docsmart.features.premium.presentation.components.PremiumBanner
import com.docsmart.features.premium.presentation.components.PremiumFeatureList
import com.docsmart.features.premium.presentation.components.PremiumPlanCards
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Ronda 18: componentes de la pantalla Premium (banner, lista de funciones, tarjetas de plan). */
class PremiumComponentsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val monthly =
        PremiumPlan(
            id = "monthly",
            titleRes = R.string.premium_plan_monthly,
            price = "USD 4.99",
            periodRes = R.string.premium_period_month,
            productId = "com.docsmart.premium.monthly",
        )
    private val annual =
        PremiumPlan(
            id = "annual",
            titleRes = R.string.premium_plan_annual,
            price = "USD 39.99",
            periodRes = R.string.premium_period_year,
            savingsLabelRes = R.string.premium_savings_44,
            isPopular = true,
            productId = "com.docsmart.premium.annual",
            trialDays = 7,
        )

    private fun string(
        resId: Int,
        vararg args: Any,
    ): String {
        val context = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES")
        // Sin argumentos no se formatea: algunos textos llevan un "%" literal.
        return if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            val localized = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources) {
                MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { content() } }
            }
        }
    }

    @Test
    fun banner_conUsuarioGratis_muestraElTituloComercial() {
        setContent { PremiumBanner(isPremium = false) }

        composeRule.onNodeWithText(string(R.string.settings_premium)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_premium_subtitle)).assertExists()
        composeRule.onAllNodesWithText(string(R.string.premium_you_are_premium)).assertCountEquals(0)
    }

    @Test
    fun banner_conUsuarioPremium_muestraElMensajeDeConfirmacion() {
        setContent { PremiumBanner(isPremium = true) }

        composeRule.onNodeWithText(string(R.string.premium_you_are_premium)).assertExists()
        composeRule.onNodeWithText(string(R.string.premium_enjoy_unlimited)).assertExists()
        composeRule.onAllNodesWithText(string(R.string.settings_premium)).assertCountEquals(0)
    }

    @Test
    fun listaDeFunciones_muestraTodasLasFuncionesParaGratisYPremium() {
        // Se renderiza dos veces (bloqueadas y desbloqueadas) para recorrer ambas ramas.
        setContent {
            PremiumFeatureList(features = PremiumFeature.entries, isPremium = false)
            PremiumFeatureList(features = PremiumFeature.entries, isPremium = true)
        }

        composeRule.onAllNodesWithText(string(R.string.premium_all_you_get)).assertCountEquals(2)
        PremiumFeature.entries.forEach { feature ->
            composeRule.onAllNodesWithText(string(feature.titleRes)).assertCountEquals(2)
            composeRule.onAllNodesWithText(string(feature.descRes)).assertCountEquals(2)
        }
    }

    @Test
    fun tarjetasDePlan_muestranPreciosBadgesYElPlanSeleccionado() {
        setContent {
            PremiumPlanCards(plans = listOf(monthly, annual), selectedPlan = annual, onPlanSelected = {})
        }

        composeRule.onNodeWithText(string(R.string.premium_choose_plan)).assertExists()
        composeRule.onNodeWithText(string(R.string.premium_recommended)).assertExists()
        composeRule.onNodeWithText(string(R.string.premium_savings_44)).assertExists()
        composeRule.onNodeWithText(string(R.string.premium_trial_badge, 7)).assertExists()
        composeRule.onNodeWithText(monthly.price).assertIsNotSelected()
        composeRule.onNodeWithText(annual.price).assertIsSelected()
    }

    @Test
    fun tarjetasDePlan_sinPlanSeleccionadoNingunoQuedaMarcado() {
        setContent {
            PremiumPlanCards(plans = listOf(monthly), selectedPlan = null, onPlanSelected = {})
        }

        composeRule.onNodeWithText(monthly.price).assertIsNotSelected()
        composeRule.onAllNodesWithText(string(R.string.premium_recommended)).assertCountEquals(0)
    }

    @Test
    fun tocarUnaTarjetaDePlan_informaElPlanElegido() {
        val chosen = mutableListOf<PremiumPlan>()
        setContent {
            PremiumPlanCards(plans = listOf(monthly, annual), selectedPlan = annual, onPlanSelected = { chosen += it })
        }

        composeRule.onNodeWithText(monthly.price).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(monthly), chosen)
    }
}
