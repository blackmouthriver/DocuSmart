package com.docsmart.features.premium.presentation

import com.docsmart.features.premium.domain.model.PremiumPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PremiumLogicTest {
    private fun plan(trialDays: Int?) =
        PremiumPlan(
            id = "monthly",
            titleRes = 1,
            price = "2,99 US$",
            periodRes = 2,
            productId = "com.docsmart.premium.monthly",
            trialDays = trialDays,
        )

    @Test
    fun `isSubscriptionTrialActive es true solo si la fecha de fin es futura`() {
        assertTrue(isSubscriptionTrialActive(trialEndsAtMillis = 2_000L, nowMillis = 1_000L))
    }

    @Test
    fun `isSubscriptionTrialActive es false si la prueba ya termino o termina justo ahora`() {
        assertFalse(isSubscriptionTrialActive(1_000L, 1_000L))
        assertFalse(isSubscriptionTrialActive(500L, 1_000L))
    }

    @Test
    fun `isSubscriptionTrialActive es false sin fecha de fin`() {
        assertFalse(isSubscriptionTrialActive(null, 1_000L))
    }

    @Test
    fun `purchaseCtaFor sin plan pide seleccionar uno`() {
        assertEquals(PurchaseCta.SelectPlan, purchaseCtaFor(null))
    }

    @Test
    fun `purchaseCtaFor con prueba gratuita anuncia la prueba y no el precio`() {
        assertEquals(PurchaseCta.StartTrial(7), purchaseCtaFor(plan(trialDays = 7)))
    }

    @Test
    fun `purchaseCtaFor sin prueba muestra el plan con su precio`() {
        val plan = plan(trialDays = null)

        assertEquals(PurchaseCta.GetPlan(plan), purchaseCtaFor(plan))
    }

    // ── annualSavingsPercent (backlog #9) ─────────────────────────────────────

    @Test
    fun `annualSavingsPercent con los precios en COP da 43 y no 44`() {
        // 6.900 COP/mes * 12 = 82.800; anual 46.900 -> 43,36 %.
        assertEquals(43, annualSavingsPercent(6_900_000_000L, 46_900_000_000L))
    }

    @Test
    fun `annualSavingsPercent con los precios de respaldo en USD da 44`() {
        // 2,99 * 12 = 35,88; anual 19,99 -> 44,29 %.
        assertEquals(44, annualSavingsPercent(2_990_000L, 19_990_000L))
    }

    @Test
    fun `annualSavingsPercent redondea hacia abajo para no exagerar la promesa`() {
        // 12 meses = 1200; anual 1005 -> 16,25 % -> 16.
        assertEquals(16, annualSavingsPercent(100_000_000L, 1_005_000_000L))
    }

    @Test
    fun `annualSavingsPercent es null si falta un precio`() {
        assertNull(annualSavingsPercent(null, 46_900_000_000L))
        assertNull(annualSavingsPercent(6_900_000_000L, null))
    }

    @Test
    fun `annualSavingsPercent es null si el anual no es mas barato o no hay ahorro entero`() {
        assertNull(annualSavingsPercent(1_000_000L, 12_000_000L))
        assertNull(annualSavingsPercent(1_000_000L, 13_000_000L))
        assertNull(annualSavingsPercent(1_000_000_000L, 11_990_000_000L)) // 0,08 % -> 0
    }

    @Test
    fun `annualSavingsPercent ignora precios en cero o negativos`() {
        assertNull(annualSavingsPercent(0L, 46_900_000_000L))
        assertNull(annualSavingsPercent(6_900_000_000L, 0L))
    }
}
