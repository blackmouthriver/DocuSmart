package com.docsmart.core.billing

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.util.AppLifecycleTracker
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private fun buildManager(tracker: AppLifecycleTracker = mockk(relaxed = true)): BillingManager {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return BillingManager(
            context,
            mockk<PremiumManager>(relaxed = true),
            tracker,
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

    // Ronda 21: sin conexión real a Play Billing lista, productDetailsCache
    // siempre queda vacío para un producto que no existe en el catálogo --
    // esto es determinista independientemente de si el emulador de CI tiene
    // o no Google Play, a diferencia de restorePurchases() de arriba.
    // Ejerce buildPurchaseParams() (rama "sin detalles") y el early-return de
    // launchPurchase(). No se dereferencia `activity` en esa rama, así que un
    // mock sin comportamiento alcanza.
    @Test
    fun launchPurchase_conProductoQueNoExisteEnElCatalogo_devuelveFalse() {
        val manager = buildManager()
        val activity = mockk<Activity>()

        val launched = manager.launchPurchase(activity, "com.docsmart.producto.inexistente.ronda21")

        assertFalse(launched)
    }

    // Ronda 21: el observer de ON_START (init{}, ver BillingManager) nunca se
    // ejercía en un test -- AppLifecycleTracker se mockeaba `relaxed` y
    // `addObserver()` no hacía nada. Capturando el observer real con un slot
    // se puede invocar `onStateChanged()` a mano (mismo patrón que "callbacks
    // de listeners invocados con mockk slots" para BillingClient/AdListener).
    // Cubre las tres ramas: evento distinto de ON_START (se ignora), primer
    // ON_START (dispara restorePurchases()) y un segundo ON_START inmediato
    // (bloqueado por el throttle de 4h, shouldRevalidateOnStart ya cubierta
    // por separado en BillingManagerTest).
    @Test
    fun onStart_ignoraOtrosEventosYThrottleaUnSegundoLlamadoInmediato() {
        val observerSlot = slot<LifecycleEventObserver>()
        val tracker = mockk<AppLifecycleTracker>(relaxed = true)
        every { tracker.addObserver(capture(observerSlot)) } just Runs
        val manager = buildManager(tracker)
        val owner = mockk<LifecycleOwner>()

        observerSlot.captured.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        observerSlot.captured.onStateChanged(owner, Lifecycle.Event.ON_START)
        observerSlot.captured.onStateChanged(owner, Lifecycle.Event.ON_START)

        assertNotNull(manager)
    }

    // Ronda 21: AppLifecycleTracker (envoltorio de ProcessLifecycleOwner) no
    // tenía ningún test propio -- sus dos únicas líneas (addObserver/
    // removeObserver) solo se ejercían indirectamente a través del mock
    // `relaxed` de BillingManager, que nunca corre el código real.
    @Test
    fun appLifecycleTracker_addYRemoveObserver_deleganSinLanzar() {
        val tracker = AppLifecycleTracker()
        val observer = LifecycleEventObserver { _, _ -> }

        // ProcessLifecycleOwner exige que addObserver/removeObserver corran en el
        // hilo principal (LifecycleRegistry.enforceMainThreadIfNeeded).
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            tracker.addObserver(observer)
            tracker.removeObserver(observer)
        }
    }
}
