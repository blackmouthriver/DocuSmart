package com.docsmart.features.premium.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.R
import com.docsmart.core.billing.BillingManager
import com.docsmart.core.billing.PlanOffer
import com.docsmart.core.billing.PurchaseResult
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.features.premium.data.repository.PremiumRepository
import com.docsmart.features.premium.domain.model.PremiumPlan
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Ronda 18: rama del trial automático sin tarjeta de `PremiumScreen`
 * (`PremiumAutoTrialCard`). Va en un archivo aparte porque esa tarjeta tiene
 * una animación infinita (zoom "Ken Burns") y una foto de fondo grande -- si
 * resulta inestable en CI se puede excluir sin tocar `PremiumScreenTest`.
 */
class PremiumScreenTrialTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val annual =
        PremiumPlan(
            id = "annual",
            titleRes = R.string.premium_plan_annual,
            price = "USD 39.99",
            periodRes = R.string.premium_period_year,
            isPopular = true,
            productId = "com.docsmart.premium.annual",
        )

    private fun localizedContext() = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES")

    private fun buildViewModel(daysRemaining: Int): PremiumViewModel {
        val premiumManager = mockk<PremiumManager>(relaxed = true)
        // Trial automático: tiene acceso (isPremium) pero NO es cliente pagador.
        every { premiumManager.isPremium } returns MutableStateFlow(true)
        every { premiumManager.isPaidPremium } returns MutableStateFlow(false)
        every { premiumManager.trialEndsAtMillis } returns MutableStateFlow(null)
        every { premiumManager.autoTrialDaysRemaining } returns MutableStateFlow(daysRemaining)

        val repository = mockk<PremiumRepository>()
        every { repository.getAvailablePlans() } returns listOf(annual)

        val billingManager = mockk<BillingManager>(relaxed = true)
        every { billingManager.planOffers } returns MutableStateFlow(emptyMap<String, PlanOffer>())
        every { billingManager.purchaseResult } returns MutableSharedFlow<PurchaseResult>()

        return PremiumViewModel(premiumManager, repository, billingManager)
    }

    @Test
    fun trialAutomatico_muestraLosDiasRestantesYSigueOfreciendoLosPlanes() {
        val viewModel = buildViewModel(daysRemaining = 2)

        composeRule.setContent {
            val baseContext = LocalContext.current
            val localized = remember(baseContext) { forceLocale(baseContext, "es-ES") }
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources) {
                MaterialTheme { PremiumScreen(viewModel = viewModel) }
            }
        }

        val context = localizedContext()
        composeRule.onNodeWithText(context.getString(R.string.premium_auto_trial_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.premium_auto_trial_body, 2)).assertExists()
        // Todavía no es cliente pagador: la pantalla sigue ofreciendo suscribirse.
        val choosePlan = context.getString(R.string.premium_choose_plan)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(choosePlan))
        composeRule.onNodeWithText(choosePlan).assertExists()
    }
}
