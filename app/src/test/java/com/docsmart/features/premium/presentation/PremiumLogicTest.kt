package com.docsmart.features.premium.presentation

import com.docsmart.features.premium.domain.model.PremiumPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
}
