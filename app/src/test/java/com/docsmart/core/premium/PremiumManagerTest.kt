package com.docsmart.core.premium

import com.docsmart.features.premium.domain.model.PremiumFeature
import com.docsmart.testutil.fakeContextWithPrefs
import com.docsmart.testutil.fakePrefsStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `PremiumManager` es el guardián de todo lo que depende de si el usuario
 * pagó o no -- gatea funciones Premium y es la única fuente de verdad del
 * estado (`AdManager.isPremium` es un passthrough directo de este,
 * consolidado 2026-09-09; ya no hay un segundo flag que sincronizar). Su
 * única dependencia Android es `Context`/`SharedPreferences` (mockeable),
 * así que sí se puede testear de punta a punta en JVM puro.
 */
class PremiumManagerTest {

    private lateinit var store: MutableMap<String, Any>

    @BeforeEach
    fun setUp() {
        store = fakePrefsStore()
    }

    private fun newManager(): PremiumManager =
        PremiumManager(fakeContextWithPrefs(store))

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

    @Test
    fun `activatePremium actualiza el estado y lo persiste`() {
        val manager = newManager()

        manager.activatePremium()

        assertTrue(manager.isPremium.value)
        assertEquals(true, store["is_premium"])
    }

    @Test
    fun `deactivatePremium actualiza el estado y lo persiste`() {
        store["is_premium"] = true
        val manager = newManager()

        manager.deactivatePremium()

        assertFalse(manager.isPremium.value)
        assertEquals(false, store["is_premium"])
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

    // Duplicación corregida 2026-09-09: `!adManager.isPremium.value &&
    // !dailyLimitManager.canX()` (o su inverso) estaba repetido en
    // ConverterViewModel, PdfToolsViewModel y ScanSessionViewModel, cada
    // uno leyendo el estado Premium directo de AdManager en vez de acá.
    @Test
    fun `canPerform es true si es premium sin evaluar el limite diario`() {
        val manager = newManager()
        manager.activatePremium()
        var dailyCheckLlamado = false

        val resultado = manager.canPerform { dailyCheckLlamado = true; false }

        assertTrue(resultado)
        assertFalse(dailyCheckLlamado)
    }

    @Test
    fun `canPerform delega en el limite diario cuando no es premium`() {
        val manager = newManager()

        assertTrue(manager.canPerform { true })
        assertFalse(manager.canPerform { false })
    }
}
