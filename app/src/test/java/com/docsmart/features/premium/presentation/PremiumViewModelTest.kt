package com.docsmart.features.premium.presentation

import android.app.Activity
import app.cash.turbine.test
import com.docsmart.core.billing.BillingManager
import com.docsmart.core.billing.PlanOffer
import com.docsmart.core.billing.PurchaseResult
import com.docsmart.core.premium.PremiumManager
import com.docsmart.features.premium.data.repository.PremiumRepository
import com.docsmart.features.premium.domain.model.PremiumPlan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Cubre los hallazgos M11/M12 de la auditoría general 2026-09-17: antes de
 * esos fixes, `restorePurchases()` no tenía guard de re-entrada (a
 * diferencia de `purchase()`) y nunca fijaba `purchaseErrorMessage`, así que
 * un error de Play Billing durante la restauración caía directo al
 * `debugMessage` crudo en inglés. El resto del ViewModel (planes, ofertas,
 * trial automático) no se cubre acá -- este archivo se centra en el
 * comportamiento nuevo de `restorePurchases()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {
    private lateinit var premiumManager: PremiumManager
    private lateinit var premiumRepository: PremiumRepository
    private lateinit var billingManager: BillingManager
    private lateinit var purchaseResultFlow: MutableSharedFlow<PurchaseResult>

    private val plan =
        PremiumPlan(
            id = "annual",
            titleRes = 1,
            price = "$99",
            periodRes = 2,
            isPopular = true,
            productId = "com.docsmart.premium.annual",
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        premiumManager = mockk(relaxed = true)
        every { premiumManager.isPremium } returns MutableStateFlow(false)
        every { premiumManager.isPaidPremium } returns MutableStateFlow(false)
        every { premiumManager.trialEndsAtMillis } returns MutableStateFlow(null)
        every { premiumManager.autoTrialDaysRemaining } returns MutableStateFlow(null)

        premiumRepository = mockk()
        every { premiumRepository.getAvailablePlans() } returns listOf(plan)

        purchaseResultFlow = MutableSharedFlow()
        billingManager = mockk(relaxed = true)
        every { billingManager.planOffers } returns MutableStateFlow(emptyMap())
        every { billingManager.purchaseResult } returns purchaseResultFlow
        // restorePurchases() ahora devuelve el desenlace: un relaxed mock devolvería
        // un mock de PurchaseResult que no coincide con ninguna rama del `when`.
        coEvery { billingManager.restorePurchases() } returns PurchaseResult.NoPurchasesToRestore
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = PremiumViewModel(premiumManager, premiumRepository, billingManager)

    @Test
    fun `restorePurchases ignora un segundo llamado mientras el primero esta en curso`() =
        runTest {
            // billingManager.restorePurchases() nunca se resuelve -- simula la
            // consulta real a Play Billing todavía en curso.
            coEvery { billingManager.restorePurchases() } coAnswers { awaitCancellation() }
            val viewModel = buildViewModel()

            viewModel.restorePurchases("sin compras", "restaurado", "error")
            viewModel.restorePurchases("sin compras", "restaurado", "error")

            coVerify(exactly = 1) { billingManager.restorePurchases() }
        }

    // BillingManager.emitResult() despacha el PurchaseResult.Error en una
    // corrutina aparte (`scope.launch` sobre su propio CoroutineScope de
    // IO, no el del llamador) -- en producción esa emisión real llega
    // DESPUÉS de que restorePurchases() del ViewModel ya terminó su propio
    // `_uiState.update`. Este test reproduce ese orden real en vez de
    // simular una emisión síncrona dentro del mock (que invertiría el
    // orden y ocultaría el bug que corrige M12).
    @Test
    fun `restorePurchases usa el mensaje localizado si Play Billing devuelve un error`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertTrue(awaitItem().isPurchasing.not())
                viewModel.restorePurchases("sin compras", "restaurado", "error de restauracion localizado")
                assertTrue(awaitItem().isPurchasing)
                // billingManager.restorePurchases() es un mock relajado que no
                // hace nada -- el ViewModel sigue con su propio update (todavía
                // sin saber que hubo un error).
                awaitItem()

                purchaseResultFlow.emit(PurchaseResult.Error("raw play billing debug message"))
                val afterError = awaitItem()
                assertEquals("error de restauracion localizado", afterError.errorMessage)
                assertTrue(afterError.isPurchasing.not())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── restorePurchases(): el desenlace devuelto define el estado final ──────

    // Bug real corregido: antes el ViewModel decidía el mensaje final por
    // isPaidPremium, sin mirar el resultado -- un Error o Pending real quedaba
    // pisado por "sin compras" según el orden de llegada de la emisión
    // asíncrona de purchaseResult.
    @Test
    fun `restorePurchases con desenlace Error muestra el mensaje localizado sin depender de la emision`() =
        runTest {
            coEvery { billingManager.restorePurchases() } returns PurchaseResult.Error("raw debug")
            val viewModel = buildViewModel()

            viewModel.restorePurchases("sin compras", "restaurado", "error localizado")

            val state = viewModel.uiState.value
            assertEquals("error localizado", state.errorMessage)
            assertFalse(state.isPurchasing)
            assertFalse(state.purchaseSuccess)
        }

    @Test
    fun `restorePurchases con compra restaurada marca exito y muestra el mensaje de restaurado`() =
        runTest {
            coEvery { billingManager.restorePurchases() } returns PurchaseResult.Success
            val viewModel = buildViewModel()

            viewModel.restorePurchases("sin compras", "restaurado", "error")

            val state = viewModel.uiState.value
            assertTrue(state.purchaseSuccess)
            assertEquals("restaurado", state.errorMessage)
            assertFalse(state.isPurchasing)
        }

    @Test
    fun `restorePurchases sin compras reales no se muestra como restaurado aunque haya trial automatico`() =
        runTest {
            every { premiumManager.isPremium } returns MutableStateFlow(true)
            every { premiumManager.isPaidPremium } returns MutableStateFlow(false)
            coEvery { billingManager.restorePurchases() } returns PurchaseResult.NoPurchasesToRestore
            val viewModel = buildViewModel()

            viewModel.restorePurchases("sin compras", "restaurado", "error")

            val state = viewModel.uiState.value
            assertFalse(state.purchaseSuccess)
            assertEquals("sin compras", state.errorMessage)
        }

    // Bug real corregido: la pantalla no pasa un mensaje de "pendiente" al
    // restaurar, así que errorMessage quedaba en "" (snackbar en blanco).
    @Test
    fun `restorePurchases con compra pendiente activa el aviso persistente sin mensaje en blanco`() =
        runTest {
            coEvery { billingManager.restorePurchases() } returns PurchaseResult.Pending
            val viewModel = buildViewModel()

            viewModel.restorePurchases("sin compras", "restaurado", "error")

            val state = viewModel.uiState.value
            assertTrue(state.isPendingPurchase)
            assertNull(state.errorMessage)
            assertFalse(state.isPurchasing)
        }

    @Test
    fun `restorePurchases sin compras apaga el aviso de compra pendiente previo`() =
        runTest {
            val viewModel = buildViewModel()
            purchaseResultFlow.emit(PurchaseResult.Pending)
            assertTrue(viewModel.uiState.value.isPendingPurchase)

            viewModel.restorePurchases("sin compras", "restaurado", "error")

            assertFalse(viewModel.uiState.value.isPendingPurchase)
        }

    // ── purchase() ────────────────────────────────────────────────────────────

    @Test
    fun `purchase con launchPurchase fallido muestra el mensaje de error y libera el estado`() =
        runTest {
            every { billingManager.launchPurchase(any(), plan.productId) } returns false
            val viewModel = buildViewModel()

            viewModel.purchase(mockk<Activity>(), "no se pudo comprar", "pendiente")

            val state = viewModel.uiState.value
            assertEquals("no se pudo comprar", state.errorMessage)
            assertFalse(state.isPurchasing)
        }

    @Test
    fun `purchase ignora un segundo toque mientras el flujo de compra sigue en curso`() =
        runTest {
            every { billingManager.launchPurchase(any(), any()) } returns true
            val viewModel = buildViewModel()
            val activity = mockk<Activity>()

            viewModel.purchase(activity, "error", "pendiente")
            viewModel.purchase(activity, "error", "pendiente")

            assertTrue(viewModel.uiState.value.isPurchasing)
            verify(exactly = 1) { billingManager.launchPurchase(any(), any()) }
        }

    @Test
    fun `resultado Success de Play Billing marca compra exitosa y sale de isPurchasing`() =
        runTest {
            every { billingManager.launchPurchase(any(), any()) } returns true
            val viewModel = buildViewModel()
            viewModel.purchase(mockk<Activity>(), "error", "pendiente")

            purchaseResultFlow.emit(PurchaseResult.Success)

            val state = viewModel.uiState.value
            assertTrue(state.purchaseSuccess)
            assertFalse(state.isPurchasing)
            assertNull(state.errorMessage)
        }

    @Test
    fun `resultado Pending muestra el mensaje capturado y el aviso persistente`() =
        runTest {
            every { billingManager.launchPurchase(any(), any()) } returns true
            val viewModel = buildViewModel()
            viewModel.purchase(mockk<Activity>(), "error", "pago pendiente")

            purchaseResultFlow.emit(PurchaseResult.Pending)

            val state = viewModel.uiState.value
            assertEquals("pago pendiente", state.errorMessage)
            assertTrue(state.isPendingPurchase)
            assertFalse(state.isPurchasing)
        }

    @Test
    fun `resultado Cancelled libera isPurchasing sin mostrar error`() =
        runTest {
            every { billingManager.launchPurchase(any(), any()) } returns true
            val viewModel = buildViewModel()
            viewModel.purchase(mockk<Activity>(), "error", "pendiente")

            purchaseResultFlow.emit(PurchaseResult.Cancelled)

            val state = viewModel.uiState.value
            assertFalse(state.isPurchasing)
            assertNull(state.errorMessage)
            assertFalse(state.purchaseSuccess)
        }

    @Test
    fun `resultado Error sin mensaje localizado cae al debugMessage`() =
        runTest {
            val viewModel = buildViewModel()

            purchaseResultFlow.emit(PurchaseResult.Error("debug crudo"))

            assertEquals("debug crudo", viewModel.uiState.value.errorMessage)
        }

    // ── ofertas de Play Billing ───────────────────────────────────────────────

    @Test
    fun `las ofertas de Play Billing reemplazan el precio y los dias de prueba del plan`() =
        runTest {
            val offers = MutableStateFlow<Map<String, PlanOffer>>(emptyMap())
            every { billingManager.planOffers } returns offers
            val viewModel = buildViewModel()
            assertEquals("$99", viewModel.uiState.value.plans.single().price)

            offers.value = mapOf(plan.productId to PlanOffer(price = "$2.99", trialDays = 7))

            val updated = viewModel.uiState.value.plans.single()
            assertEquals("$2.99", updated.price)
            assertEquals(7, updated.trialDays)
        }

    // Bug real corregido (ronda 23): observeOffers() solo refrescaba `plans`,
    // dejando `selectedPlan` con el precio/trialDays de respaldo -- el CTA de
    // compra (purchaseCtaFor) lee selectedPlan, así que mostraba datos
    // obsoletos aunque la tarjeta del plan ya tuviera el precio real.
    @Test
    fun `las ofertas de Play Billing tambien refrescan el plan seleccionado`() =
        runTest {
            val offers = MutableStateFlow<Map<String, PlanOffer>>(emptyMap())
            every { billingManager.planOffers } returns offers
            val viewModel = buildViewModel()
            assertEquals("$99", viewModel.uiState.value.selectedPlan?.price)

            offers.value = mapOf(plan.productId to PlanOffer(price = "$2.99", trialDays = 7))

            val selected = viewModel.uiState.value.selectedPlan
            assertEquals("$2.99", selected?.price)
            assertEquals(7, selected?.trialDays)
        }

    // Rama complementaria de la anterior: si la oferta no incluye el plan
    // seleccionado (find() no encuentra coincidencia por id), selectedPlan
    // debe conservarse tal cual, sin caer al primer plan de la lista.
    @Test
    fun `una oferta que no incluye el plan seleccionado no le cambia el plan seleccionado`() =
        runTest {
            val otherPlan = plan.copy(id = "monthly", productId = "com.docsmart.premium.monthly", price = "$9")
            every { premiumRepository.getAvailablePlans() } returns listOf(plan, otherPlan)
            val offers = MutableStateFlow<Map<String, PlanOffer>>(emptyMap())
            every { billingManager.planOffers } returns offers
            val viewModel = buildViewModel()
            viewModel.selectPlan(otherPlan)

            offers.value = mapOf(plan.productId to PlanOffer(price = "$2.99", trialDays = 7))

            val selected = viewModel.uiState.value.selectedPlan
            assertEquals("monthly", selected?.id)
            assertEquals("$9", selected?.price)
        }

    @Test
    fun `una oferta con precio en blanco conserva el precio de respaldo`() =
        runTest {
            val offers = MutableStateFlow<Map<String, PlanOffer>>(emptyMap())
            every { billingManager.planOffers } returns offers
            val viewModel = buildViewModel()

            offers.value = mapOf(plan.productId to PlanOffer(price = "  ", trialDays = null))

            assertEquals("$99", viewModel.uiState.value.plans.single().price)
        }

    @Test
    fun `dismissError limpia el mensaje de error`() =
        runTest {
            every { billingManager.launchPurchase(any(), any()) } returns false
            val viewModel = buildViewModel()
            viewModel.purchase(mockk<Activity>(), "no se pudo comprar", "pendiente")

            viewModel.dismissError()

            assertNull(viewModel.uiState.value.errorMessage)
        }
}
