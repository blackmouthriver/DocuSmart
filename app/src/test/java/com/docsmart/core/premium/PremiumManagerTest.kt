package com.docsmart.core.premium

import com.docsmart.core.ads.AdManager
import com.docsmart.features.premium.domain.model.PremiumFeature
import com.docsmart.testutil.fakeContextWithPrefs
import com.docsmart.testutil.fakePrefsStore
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `PremiumManager` es el guardián de todo lo que depende de si el usuario
 * pagó o no -- gatea funciones Premium y sincroniza `AdManager` (mostrar u
 * ocultar anuncios). A diferencia de `BillingManager`/`AdManager`, sus únicas
 * dependencias Android son `Context`/`SharedPreferences` (mockeables) y
 * `AdManager` (mockeado como clase, MockK nunca ejecuta su constructor real),
 * así que sí se puede testear de punta a punta en JVM puro.
 */
class PremiumManagerTest {

    private lateinit var adManager: AdManager
    private lateinit var store: MutableMap<String, Any>

    @BeforeEach
    fun setUp() {
        adManager = mockk(relaxed = true)
        store = fakePrefsStore()
    }

    private fun newManager(): PremiumManager =
        PremiumManager(fakeContextWithPrefs(store), adManager)

    @Test
    fun `arranca en free por defecto sin estado guardado previo`() {
        val manager = newManager()

        assertFalse(manager.isPremium.value)
    }

    @Test
    fun `carga el estado premium ya guardado en SharedPreferences`() {
        store["is_premium"] = true

        val manager = newManager()

        assertTrue(manager.isPremium.value)
    }

    // Bug real corregido 2026-09-07: AdManager.isPremium arrancaba siempre en
    // false y solo se ponía al día cuando el usuario abría la pantalla
    // Premium -- un suscriptor real que no visitaba esa pantalla seguía
    // viendo anuncios toda la sesión pese a tener el estado correcto ya
    // guardado. Se sincroniza al construirse PremiumManager.
    @Test
    fun `al construirse sincroniza el estado premium guardado con AdManager`() {
        store["is_premium"] = true

        newManager()

        verify { adManager.setPremium(true) }
    }

    @Test
    fun `activatePremium actualiza el estado, lo persiste y sincroniza AdManager`() {
        val manager = newManager()

        manager.activatePremium()

        assertTrue(manager.isPremium.value)
        assertEquals(true, store["is_premium"])
        verify { adManager.setPremium(true) }
    }

    @Test
    fun `deactivatePremium actualiza el estado, lo persiste y sincroniza AdManager`() {
        store["is_premium"] = true
        val manager = newManager()

        manager.deactivatePremium()

        assertFalse(manager.isPremium.value)
        assertEquals(false, store["is_premium"])
        verify { adManager.setPremium(false) }
    }

    @Test
    fun `isFeatureAvailable es true para todas las funciones cuando es premium`() {
        val manager = newManager()
        manager.activatePremium()

        PremiumFeature.entries.forEach { feature ->
            assertTrue(manager.isFeatureAvailable(feature), "esperaba ${feature.name} disponible en premium")
        }
    }

    @Test
    fun `isFeatureAvailable es false para todas las funciones actuales cuando no es premium`() {
        // Todas las PremiumFeature actuales tienen isAvailableFree = false --
        // si isFeatureAvailable devolviera true para alguna sin ser premium,
        // este test lo detectaría.
        val manager = newManager()

        PremiumFeature.entries.forEach { feature ->
            assertFalse(manager.isFeatureAvailable(feature), "esperaba ${feature.name} bloqueada sin premium")
        }
    }

    // Bug real corregido 2026-09-08: UNLIMITED_CONVERT estaba marcado
    // isAvailableFree = true pese a que el límite diario de 5 conversiones
    // (DailyLimitManager.LIMIT_CONVERSIONS) sí se aplica de verdad -- la
    // pantalla Premium le mentía al usuario sobre lo que Premium ofrece.
    @Test
    fun `UNLIMITED_CONVERT no esta marcado como disponible gratis`() {
        assertFalse(PremiumFeature.UNLIMITED_CONVERT.isAvailableFree)
    }
}
