package com.docsmart.features.premium.presentation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Ronda 18: `PremiumScreen` con un `PremiumViewModel` real armado a mano con
 * `PremiumManager`/`BillingManager`/`PremiumRepository` mockeados (sin Hilt).
 *
 * ATENCIÓN (docs/requirements/compose-ui-testing.md, fila 16): esta pantalla
 * estuvo "bloqueada" por una condición de carrera de sincronización de
 * Compose Testing (`waitUntil` sin resolver). Estas pruebas no se pudieron
 * ejecutar al escribirlas -- si vuelven a colgarse en CI, este archivo (y
 * `PremiumScreenTrialTest`) se puede excluir sin afectar al resto.
 *
 * Es una `LazyColumn`: lo que queda fuera del viewport no se compone, así
 * que se hace scroll hasta cada texto antes de aserciones/clics.
 */
class PremiumScreenTest {
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
            isPopular = true,
            productId = "com.docsmart.premium.annual",
        )

    private lateinit var billingManager: BillingManager

    private fun string(
        resId: Int,
        vararg args: Any,
    ): String {
        val context = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES")
        // Sin argumentos no se formatea: algunos textos llevan un "%" literal.
        return if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
    }

    private fun buildViewModel(
        isPaid: Boolean = false,
        trialEndsAtMillis: Long? = null,
        purchaseResults: MutableSharedFlow<PurchaseResult> = MutableSharedFlow(replay = 1),
    ): PremiumViewModel {
        val premiumManager = mockk<PremiumManager>(relaxed = true)
        every { premiumManager.isPremium } returns MutableStateFlow(isPaid)
        every { premiumManager.isPaidPremium } returns MutableStateFlow(isPaid)
        every { premiumManager.trialEndsAtMillis } returns MutableStateFlow(trialEndsAtMillis)
        every { premiumManager.autoTrialDaysRemaining } returns MutableStateFlow(null)

        val repository = mockk<PremiumRepository>()
        every { repository.getAvailablePlans() } returns listOf(monthly, annual)

        billingManager = mockk(relaxed = true)
        every { billingManager.planOffers } returns MutableStateFlow(emptyMap<String, PlanOffer>())
        every { billingManager.purchaseResult } returns purchaseResults
        every { billingManager.launchPurchase(any(), any()) } returns true
        coEvery { billingManager.restorePurchases() } returns PurchaseResult.NoPurchasesToRestore

        return PremiumViewModel(premiumManager, repository, billingManager)
    }

    private fun setScreen(
        viewModel: PremiumViewModel,
        localized: Boolean = true,
        onClose: () -> Unit = {},
    ) {
        composeRule.setContent {
            val baseContext = LocalContext.current
            // findActivity() no atraviesa el contexto de forceLocale(): las pruebas que
            // necesitan el Activity real (compra) usan el contexto original.
            val context =
                remember(baseContext) { if (localized) forceLocale(baseContext, "es-ES") else baseContext }
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources) {
                MaterialTheme { PremiumScreen(onClose = onClose, viewModel = viewModel) }
            }
        }
    }

    private fun scrollToText(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun usuarioGratis_muestraPlanesYElBotonDelPlanPopular() {
        setScreen(buildViewModel())

        scrollToText(string(R.string.premium_choose_plan))
        composeRule.onNodeWithText(string(R.string.premium_choose_plan)).assertExists()
        scrollToText(string(R.string.premium_get_plan, string(annual.titleRes), annual.price))
        composeRule.onNodeWithText(string(R.string.premium_get_plan, string(annual.titleRes), annual.price))
            .assertExists()
        scrollToText(string(R.string.premium_terms))
        composeRule.onNodeWithText(string(R.string.premium_terms)).assertExists()
    }

    @Test
    fun elegirOtroPlan_actualizaElTextoDelBotonDeCompra() {
        setScreen(buildViewModel())

        scrollToText(monthly.price)
        composeRule.onNodeWithText(monthly.price).performClick()
        val monthlyCta = string(R.string.premium_get_plan, string(monthly.titleRes), monthly.price)
        scrollToText(monthlyCta)

        composeRule.onNodeWithText(monthlyCta).assertExists()
    }

    @Test
    fun restaurarCompras_consultaAPlayBillingYAvisaQueNoHayCompras() {
        setScreen(buildViewModel())

        scrollToText(string(R.string.premium_restore_purchases))
        composeRule.onNodeWithText(string(R.string.premium_restore_purchases)).performClick()
        composeRule.waitForIdle()

        coVerify(exactly = 1) { billingManager.restorePurchases() }
        waitForText(string(R.string.premium_no_purchases_found))
    }

    @Test
    fun comprar_lanzaElFlujoDeCompraConElProductoSeleccionadoYMuestraElProgreso() {
        // Sin forceLocale: la compra necesita el Activity real (findActivity()).
        setScreen(buildViewModel(), localized = false)
        val activity = composeRule.activity
        val annualTitle = activity.getString(annual.titleRes)
        val cta = activity.getString(R.string.premium_get_plan, annualTitle, annual.price)

        scrollToText(cta)
        composeRule.onNodeWithText(cta).performClick()
        composeRule.waitForIdle()

        verify(exactly = 1) { billingManager.launchPurchase(any(), annual.productId) }
        val processing = activity.getString(R.string.premium_processing_purchase)
        scrollToText(processing)
        composeRule.onNodeWithText(processing).assertExists()
    }

    @Test
    fun continuarConLaVersionGratuita_yElIconoCerrar_invocanOnClose() {
        var closes = 0
        setScreen(buildViewModel(), onClose = { closes++ })

        composeRule.onNodeWithContentDescription(string(R.string.settings_close)).performClick()
        scrollToText(string(R.string.premium_continue_free))
        composeRule.onNodeWithText(string(R.string.premium_continue_free)).performClick()
        composeRule.waitForIdle()

        assertEquals(2, closes)
    }

    @Test
    fun clientePagador_muestraLaTarjetaActivaSinPlanes() {
        var closes = 0
        setScreen(buildViewModel(isPaid = true), onClose = { closes++ })

        composeRule.onNodeWithText(string(R.string.premium_you_are_premium)).assertExists()
        composeRule.onNodeWithText(string(R.string.premium_active_title)).assertExists()
        composeRule.onNodeWithText(string(R.string.premium_active_body)).assertExists()
        composeRule.onAllNodesWithText(string(R.string.premium_choose_plan)).assertCountEquals(0)

        scrollToText(string(R.string.premium_continue))
        composeRule.onNodeWithText(string(R.string.premium_continue)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, closes)
    }

    @Test
    fun clienteEnPruebaDeSuscripcion_muestraLaFechaDeCobro() {
        val trialEnds = System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000
        setScreen(buildViewModel(isPaid = true, trialEndsAtMillis = trialEnds))

        val locale = composeRule.activity.resources.configuration.locales[0]
        val formattedDate = SimpleDateFormat("dd/MM/yyyy", locale).format(Date(trialEnds))

        composeRule.onNodeWithText(string(R.string.premium_trial_active_message, formattedDate)).assertExists()
    }

    @Test
    fun compraPendiente_muestraElAvisoPersistente() {
        val results = MutableSharedFlow<PurchaseResult>(replay = 1)
        results.tryEmit(PurchaseResult.Pending)
        setScreen(buildViewModel(purchaseResults = results))

        waitForText(string(R.string.premium_pending_title))
        composeRule.onNodeWithText(string(R.string.premium_pending_body)).assertExists()
    }

    @Test
    fun errorDeCompra_seMuestraEnUnSnackbar() {
        val results = MutableSharedFlow<PurchaseResult>(replay = 1)
        results.tryEmit(PurchaseResult.Error("fallo simulado"))
        setScreen(buildViewModel(purchaseResults = results))

        waitForText("fallo simulado")
        composeRule.onNodeWithText("fallo simulado").assertExists()
    }
}
