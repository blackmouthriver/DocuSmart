package com.docsmart.features.premium.presentation

import app.cash.turbine.test
import com.docsmart.core.billing.BillingManager
import com.docsmart.core.billing.PurchaseResult
import com.docsmart.core.premium.PremiumManager
import com.docsmart.features.premium.data.repository.PremiumRepository
import com.docsmart.features.premium.domain.model.PremiumPlan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

    private val plan = PremiumPlan(
        id = "annual", titleRes = 1, price = "$99", periodRes = 2,
        isPopular = true, productId = "com.docsmart.premium.annual"
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
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        PremiumViewModel(premiumManager, premiumRepository, billingManager)

    @Test
    fun `restorePurchases ignora un segundo llamado mientras el primero esta en curso`() = runTest {
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
    fun `restorePurchases usa el mensaje localizado si Play Billing devuelve un error`() = runTest {
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
}
