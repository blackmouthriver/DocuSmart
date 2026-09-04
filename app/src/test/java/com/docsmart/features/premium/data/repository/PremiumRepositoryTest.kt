package com.docsmart.features.premium.data.repository

import com.docsmart.R
import com.docsmart.core.remoteconfig.RemoteConfigManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RF pedido por el usuario 2026-09-04 ("configuración de monetización"):
 * qué plan se destaca y si se muestra el badge de ahorro ahora los decide
 * Remote Config, no un valor fijo en el código.
 */
class PremiumRepositoryTest {

    @Test
    fun `plan anual destacado y badge de ahorro visible por defecto`() {
        val remoteConfig = mockk<RemoteConfigManager>()
        every { remoteConfig.isAnnualPlanHighlighted() } returns true
        every { remoteConfig.showSavingsBadge() } returns true

        val plans = PremiumRepository(remoteConfig).getAvailablePlans()

        val monthly = plans.single { it.id == "monthly" }
        val annual  = plans.single { it.id == "annual" }
        assertTrue(annual.isPopular)
        assertTrue(!monthly.isPopular)
        assertEquals(R.string.premium_savings_44, annual.savingsLabelRes)
    }

    @Test
    fun `Remote Config puede destacar el plan mensual en vez del anual`() {
        val remoteConfig = mockk<RemoteConfigManager>()
        every { remoteConfig.isAnnualPlanHighlighted() } returns false
        every { remoteConfig.showSavingsBadge() } returns true

        val plans = PremiumRepository(remoteConfig).getAvailablePlans()

        val monthly = plans.single { it.id == "monthly" }
        val annual  = plans.single { it.id == "annual" }
        assertTrue(monthly.isPopular)
        assertTrue(!annual.isPopular)
    }

    @Test
    fun `Remote Config puede ocultar el badge de ahorro`() {
        val remoteConfig = mockk<RemoteConfigManager>()
        every { remoteConfig.isAnnualPlanHighlighted() } returns true
        every { remoteConfig.showSavingsBadge() } returns false

        val annual = PremiumRepository(remoteConfig).getAvailablePlans().single { it.id == "annual" }

        assertNull(annual.savingsLabelRes)
    }
}
