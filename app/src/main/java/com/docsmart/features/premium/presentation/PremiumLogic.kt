package com.docsmart.features.premium.presentation

import com.docsmart.features.premium.domain.model.PremiumPlan

// Lógica pura extraída de PremiumScreen (ronda 17) para testearla en JVM.

/**
 * HU-54, AC1: la fecha de cobro solo se muestra mientras la prueba siga
 * vigente; una vez pasada (el usuario ya paga, o restauró una compra vieja)
 * la tarjeta vuelve al texto normal.
 */
internal fun isSubscriptionTrialActive(
    trialEndsAtMillis: Long?,
    nowMillis: Long,
): Boolean = trialEndsAtMillis != null && trialEndsAtMillis > nowMillis

/** Texto del botón principal de compra. */
internal sealed interface PurchaseCta {
    data object SelectPlan : PurchaseCta

    // HU-54 RF2: si el plan tiene prueba gratuita, el CTA lo dice en vez de mostrar el precio del primer cobro.
    data class StartTrial(
        val days: Int,
    ) : PurchaseCta

    data class GetPlan(
        val plan: PremiumPlan,
    ) : PurchaseCta
}

internal fun purchaseCtaFor(plan: PremiumPlan?): PurchaseCta =
    when {
        plan == null -> PurchaseCta.SelectPlan
        plan.trialDays != null -> PurchaseCta.StartTrial(plan.trialDays)
        else -> PurchaseCta.GetPlan(plan)
    }
