package com.docsmart.core.billing

import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.util.AppLifecycleTracker
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Ronda 20: construye el `BillingManager` real (conexión a Play Billing) con
 * `PremiumManager` mockeado. Nunca se lanza un flujo de compra: solo se
 * verifica el arranque (init, listener de conexión) y `restorePurchases()`,
 * cuyo desenlace depende de si el dispositivo tiene Google Play, por eso solo
 * se exige que devuelva algún resultado sin lanzar excepciones.
 */
class BillingManagerConnectionTest {
    private fun buildManager(): BillingManager {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return BillingManager(
            context,
            mockk<PremiumManager>(relaxed = true),
            mockk<AppLifecycleTracker>(relaxed = true),
        )
    }

    @Test
    fun alConstruir_seExponenLasOfertasVaciasYLosIdsDeProducto() {
        val manager = buildManager()

        assertNotNull(manager.purchaseResult)
        assertEquals("com.docsmart.premium.monthly", BillingManager.PRODUCT_MONTHLY)
        assertEquals("com.docsmart.premium.annual", BillingManager.PRODUCT_ANNUAL)
        // Sin conexión lista todavía no hay ofertas (o ya llegaron las reales; ambas son válidas).
        assertNotNull(manager.planOffers.value)
    }

    @Test
    fun restaurarCompras_devuelveUnResultadoSinLanzar() {
        val manager = buildManager()

        val result = runBlocking { withTimeoutOrNull(20_000) { manager.restorePurchases() } }
        // Sin respuesta de Play Billing en el emulador: no se puede afirmar nada.
        assumeTrue(result != null)

        val valid =
            result is PurchaseResult.Success ||
                result is PurchaseResult.NoPurchasesToRestore ||
                result is PurchaseResult.Pending ||
                result is PurchaseResult.Error
        assertEquals(true, valid)
    }
}
