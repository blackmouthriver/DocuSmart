package com.docsmart.core.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    private fun purchaseOf(state: Int): Purchase =
        mockk {
            every { purchaseState } returns state
        }

    @Test
    fun `una consulta fallida no debe tratarse como que el usuario no tiene compras`() {
        val outcome =
            evaluateRestoreOutcome(
                responseCode = BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                purchasesList = emptyList(),
            )

        assertEquals(RestoreOutcome.QueryFailed, outcome)
    }

    @Test
    fun `una consulta fallida se trata como fallo aunque la lista traiga compras`() {
        // Nunca debería pasar en la práctica, pero si billingResult.responseCode
        // indica fallo, no hay que confiar en el contenido de purchasesList.
        val outcome =
            evaluateRestoreOutcome(
                responseCode = BillingClient.BillingResponseCode.ERROR,
                purchasesList = listOf(purchaseOf(Purchase.PurchaseState.PURCHASED)),
            )

        assertEquals(RestoreOutcome.QueryFailed, outcome)
    }

    @Test
    fun `una consulta exitosa sin compras compradas se trata como que no hay nada que restaurar`() {
        val outcome =
            evaluateRestoreOutcome(
                responseCode = BillingClient.BillingResponseCode.OK,
                purchasesList = emptyList(),
            )

        assertEquals(RestoreOutcome.NothingOwned, outcome)
    }

    // Hallazgo 3 de la auditoría de monetización 2026-09-18 (Media): esta
    // aserción codificaba el bug real -- antes, una compra PENDING (pago en
    // efectivo/transferencia, método común en Latinoamérica que tarda en
    // confirmarse) que reaparecía en una revalidación posterior se trataba
    // exactamente igual que "el usuario nunca compró nada", desactivando
    // Premium y mostrando "no se encontraron compras" en vez de indicar que
    // el pago sigue en trámite. Corregido para devolver `RestoreOutcome.Pending`
    // en vez de `NothingOwned` (ver el nuevo test más abajo).
    @Test
    fun `una consulta exitosa con solo compras pendientes se trata como pendiente, no como que no hay nada`() {
        val pending = purchaseOf(Purchase.PurchaseState.PENDING)

        val outcome =
            evaluateRestoreOutcome(
                responseCode = BillingClient.BillingResponseCode.OK,
                purchasesList = listOf(pending),
            )

        assertTrue(outcome is RestoreOutcome.Pending)
        assertEquals(listOf(pending), (outcome as RestoreOutcome.Pending).purchases)
    }

    @Test
    fun `una consulta exitosa con una compra activa la devuelve para restaurar`() {
        val purchase = purchaseOf(Purchase.PurchaseState.PURCHASED)

        val outcome =
            evaluateRestoreOutcome(
                responseCode = BillingClient.BillingResponseCode.OK,
                purchasesList = listOf(purchase),
            )

        assertTrue(outcome is RestoreOutcome.Owned)
        assertEquals(listOf(purchase), (outcome as RestoreOutcome.Owned).purchases)
    }

    @Test
    fun `filtra las compras pendientes y solo restaura las ya compradas`() {
        val pending = purchaseOf(Purchase.PurchaseState.PENDING)
        val purchased = purchaseOf(Purchase.PurchaseState.PURCHASED)

        val outcome =
            evaluateRestoreOutcome(
                responseCode = BillingClient.BillingResponseCode.OK,
                purchasesList = listOf(pending, purchased),
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

    // ── planOfferFor(): precio recurrente y días de prueba (HU-54) ─────────────

    private fun phase(
        micros: Long,
        price: String,
        period: String,
    ): ProductDetails.PricingPhase =
        mockk {
            every { priceAmountMicros } returns micros
            every { formattedPrice } returns price
            every { billingPeriod } returns period
        }

    private fun detailsWith(phases: List<ProductDetails.PricingPhase>?): ProductDetails {
        val holder = mockk<ProductDetails.PricingPhases>()
        every { holder.pricingPhaseList } returns phases.orEmpty()
        val offer = mockk<ProductDetails.SubscriptionOfferDetails>()
        every { offer.pricingPhases } returns holder
        val details = mockk<ProductDetails>()
        every { details.subscriptionOfferDetails } returns if (phases == null) null else listOf(offer)
        return details
    }

    @Test
    fun `planOfferFor separa la fase de prueba gratis del precio recurrente`() {
        val details =
            detailsWith(
                listOf(
                    phase(0L, "Gratis", "P7D"),
                    phase(2_990_000L, "2,99 US$", "P1M"),
                ),
            )

        assertEquals(PlanOffer(price = "2,99 US$", trialDays = 7), planOfferFor(details))
    }

    @Test
    fun `planOfferFor no depende del orden de las fases`() {
        val details =
            detailsWith(
                listOf(
                    phase(29_990_000L, "29,99 US$", "P1Y"),
                    phase(0L, "Gratis", "P1W"),
                ),
            )

        assertEquals(PlanOffer(price = "29,99 US$", trialDays = 7), planOfferFor(details))
    }

    @Test
    fun `planOfferFor sin fase de prueba deja trialDays en null`() {
        val details = detailsWith(listOf(phase(2_990_000L, "2,99 US$", "P1M")))

        assertEquals(PlanOffer(price = "2,99 US$", trialDays = null), planOfferFor(details))
    }

    @Test
    fun `planOfferFor sin ofertas devuelve precio vacio para que la UI use el de respaldo`() {
        assertEquals(PlanOffer(price = "", trialDays = null), planOfferFor(detailsWith(null)))
    }

    // ── trialEndsAtMillisOf(): fin de la prueba de una compra ─────────────────

    private fun purchaseAt(timeMillis: Long): Purchase =
        mockk {
            every { purchaseTime } returns timeMillis
        }

    @Test
    fun `trialEndsAtMillisOf suma los dias de prueba al momento de compra`() {
        val details =
            detailsWith(
                listOf(
                    phase(0L, "Gratis", "P7D"),
                    phase(2_990_000L, "2,99 US$", "P1M"),
                ),
            )

        val endsAt = trialEndsAtMillisOf(purchaseAt(1_000L), details)

        assertEquals(1_000L + 7L * 24 * 60 * 60 * 1000, endsAt)
    }

    @Test
    fun `trialEndsAtMillisOf devuelve null si el producto no tiene prueba gratis`() {
        val details = detailsWith(listOf(phase(2_990_000L, "2,99 US$", "P1M")))

        assertNull(trialEndsAtMillisOf(purchaseAt(1_000L), details))
    }

    @Test
    fun `trialEndsAtMillisOf devuelve null si los detalles del producto aun no se cargaron`() {
        // Ocurre si restorePurchases() corre antes de que queryProductDetails() termine.
        assertNull(trialEndsAtMillisOf(purchaseAt(1_000L), null))
    }

    @Test
    fun `trialEndsAtMillisOf devuelve null si el periodo de prueba no es interpretable`() {
        val details = detailsWith(listOf(phase(0L, "Gratis", "raro")))

        assertNull(trialEndsAtMillisOf(purchaseAt(1_000L), details))
    }

    @Test
    fun `iso8601PeriodToDays combina componentes`() {
        assertEquals(1 * 30 + 2 * 7 + 3, iso8601PeriodToDays("P1M2W3D"))
    }

    @Test
    fun `evaluateRestoreOutcome con varias compras compradas las devuelve todas`() {
        val first = purchaseOf(Purchase.PurchaseState.PURCHASED)
        val second = purchaseOf(Purchase.PurchaseState.PURCHASED)

        val outcome = evaluateRestoreOutcome(BillingClient.BillingResponseCode.OK, listOf(first, second))

        assertEquals(RestoreOutcome.Owned(listOf(first, second)), outcome)
    }

    @Test
    fun `evaluateRestoreOutcome ignora compras en estado no especificado`() {
        val unspecified = purchaseOf(Purchase.PurchaseState.UNSPECIFIED_STATE)

        val outcome = evaluateRestoreOutcome(BillingClient.BillingResponseCode.OK, listOf(unspecified))

        assertEquals(RestoreOutcome.NothingOwned, outcome)
    }
}
