package com.docsmart.core.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `BillingManager` en sí no se puede construir en un test unitario JVM puro:
 * su constructor arma un `BillingClient` real vía `BillingClient.newBuilder(context)`
 * y lo conecta en `init {}` -- no hay forma de sustituirlo por un mock sin
 * refactorizar a inyectar el cliente (no hay Robolectric en este proyecto).
 *
 * Lo que sí es lógica pura, y es exactamente donde ya ocurrió un incidente
 * real en producción, es la decisión de qué hacer con el resultado de
 * `queryPurchasesAsync()` -- extraída a `evaluateRestoreOutcome()` para
 * poder fijarla con un test de regresión directo sobre el bug ya corregido
 * (2026-09-08): una consulta fallida (sin red, Play Store caído, etc.)
 * devuelve `purchasesList` vacía, exactamente igual que "el usuario
 * genuinamente no tiene compras" -- antes de la corrección, eso desactivaba
 * Premium a un usuario que sí había pagado, en cada arranque de la app.
 */
class BillingManagerTest {

    private fun purchaseOf(state: Int): Purchase = mockk {
        every { purchaseState } returns state
    }

    @Test
    fun `una consulta fallida no debe tratarse como que el usuario no tiene compras`() {
        val outcome = evaluateRestoreOutcome(
            responseCode = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            purchasesList = emptyList()
        )

        assertEquals(RestoreOutcome.QueryFailed, outcome)
    }

    @Test
    fun `una consulta fallida se trata como fallo aunque la lista traiga compras`() {
        // Nunca debería pasar en la práctica, pero si billingResult.responseCode
        // indica fallo, no hay que confiar en el contenido de purchasesList.
        val outcome = evaluateRestoreOutcome(
            responseCode = BillingClient.BillingResponseCode.ERROR,
            purchasesList = listOf(purchaseOf(Purchase.PurchaseState.PURCHASED))
        )

        assertEquals(RestoreOutcome.QueryFailed, outcome)
    }

    @Test
    fun `una consulta exitosa sin compras compradas se trata como que no hay nada que restaurar`() {
        val outcome = evaluateRestoreOutcome(
            responseCode = BillingClient.BillingResponseCode.OK,
            purchasesList = emptyList()
        )

        assertEquals(RestoreOutcome.NothingOwned, outcome)
    }

    @Test
    fun `una consulta exitosa con solo compras pendientes se trata como que no hay nada que restaurar`() {
        val outcome = evaluateRestoreOutcome(
            responseCode = BillingClient.BillingResponseCode.OK,
            purchasesList = listOf(purchaseOf(Purchase.PurchaseState.PENDING))
        )

        assertEquals(RestoreOutcome.NothingOwned, outcome)
    }

    @Test
    fun `una consulta exitosa con una compra activa la devuelve para restaurar`() {
        val purchase = purchaseOf(Purchase.PurchaseState.PURCHASED)

        val outcome = evaluateRestoreOutcome(
            responseCode = BillingClient.BillingResponseCode.OK,
            purchasesList = listOf(purchase)
        )

        assertTrue(outcome is RestoreOutcome.Owned)
        assertEquals(listOf(purchase), (outcome as RestoreOutcome.Owned).purchases)
    }

    @Test
    fun `filtra las compras pendientes y solo restaura las ya compradas`() {
        val pending = purchaseOf(Purchase.PurchaseState.PENDING)
        val purchased = purchaseOf(Purchase.PurchaseState.PURCHASED)

        val outcome = evaluateRestoreOutcome(
            responseCode = BillingClient.BillingResponseCode.OK,
            purchasesList = listOf(pending, purchased)
        )

        assertTrue(outcome is RestoreOutcome.Owned)
        assertEquals(listOf(purchased), (outcome as RestoreOutcome.Owned).purchases)
    }

    // HU-54: iso8601PeriodToDays() traduce la duración que Play Billing
    // devuelve para la fase de prueba gratuita ("freeTrialPeriod" en Play
    // Console) al número de días que se muestra en la UI ("7 días gratis").
    @Test
    fun `iso8601PeriodToDays interpreta dias`() {
        assertEquals(7, iso8601PeriodToDays("P7D"))
    }

    @Test
    fun `iso8601PeriodToDays interpreta semanas`() {
        assertEquals(14, iso8601PeriodToDays("P2W"))
    }

    @Test
    fun `iso8601PeriodToDays interpreta meses de forma aproximada`() {
        assertEquals(30, iso8601PeriodToDays("P1M"))
    }

    @Test
    fun `iso8601PeriodToDays interpreta anios de forma aproximada`() {
        assertEquals(365, iso8601PeriodToDays("P1Y"))
    }

    @Test
    fun `iso8601PeriodToDays devuelve null para un texto invalido`() {
        assertEquals(null, iso8601PeriodToDays("no-es-un-periodo"))
    }

    @Test
    fun `iso8601PeriodToDays devuelve null para un periodo de cero dias`() {
        // No debería pasar en la práctica (Play Console no permite un trial
        // de 0 días), pero si pasara, no debe tratarse como "hay trial".
        assertEquals(null, iso8601PeriodToDays("P0D"))
    }
}
