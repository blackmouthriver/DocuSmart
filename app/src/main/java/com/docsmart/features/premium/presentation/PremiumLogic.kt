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

/**
 * Backlog #9: porcentaje que se ahorra al pagar el plan anual en vez de 12 meses del mensual, con
 * los precios reales (micro-unidades) que devuelve Play. Se redondea hacia abajo para no exagerar
 * la promesa (6.900 COP/mes vs 46.900 COP/año = 43,4 % -> 43, no 44). `null` si falta algún precio o
 * el anual no es realmente más barato: en ese caso no se muestra el badge.
 */
internal fun annualSavingsPercent(
    monthlyMicros: Long?,
    annualMicros: Long?,
): Int? {
    val yearlyAtMonthly = monthlyMicros?.takeIf { it > 0L }?.times(MONTHS_PER_YEAR)
    val annual = annualMicros?.takeIf { it > 0L }
    return if (yearlyAtMonthly == null || annual == null || annual >= yearlyAtMonthly) {
        null
    } else {
        ((yearlyAtMonthly - annual) * PERCENT / yearlyAtMonthly).toInt().takeIf { it > 0 }
    }
}

private const val MONTHS_PER_YEAR = 12L
private const val PERCENT = 100L
