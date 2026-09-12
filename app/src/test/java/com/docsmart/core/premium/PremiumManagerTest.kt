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

    private fun newManager(firstInstallTimeMillis: Long = 0L): PremiumManager =
        PremiumManager(fakeContextWithPrefs(store, firstInstallTimeMillis))

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

    // HU-54 (prueba gratuita): trialEndsAtMillis es solo para mensajes de UI
    // -- estos tests confirman que se persiste/limpia igual que isPremium,
    // sin tocar isFeatureAvailable/canPerform (ver tests de arriba, que ya
    // cubren que el gating no distingue trial de pago).
    @Test
    fun `activatePremium sin trial deja trialEndsAtMillis en null`() {
        val manager = newManager()

        manager.activatePremium()

        assertEquals(null, manager.trialEndsAtMillis.value)
    }

    @Test
    fun `activatePremium con trial guarda y persiste la fecha de fin`() {
        val manager = newManager()
        val trialEndsAtMillis = 1_800_000_000_000L

        manager.activatePremium(trialEndsAtMillis)

        assertEquals(trialEndsAtMillis, manager.trialEndsAtMillis.value)
        assertEquals(trialEndsAtMillis, store["trial_ends_at_millis"])
    }

    @Test
    fun `carga la fecha de fin de trial ya guardada en SharedPreferences`() {
        store["is_premium"] = true
        store["trial_ends_at_millis"] = 1_800_000_000_000L

        val manager = newManager()

        assertEquals(1_800_000_000_000L, manager.trialEndsAtMillis.value)
    }

    @Test
    fun `deactivatePremium limpia la fecha de fin de trial`() {
        val manager = newManager()
        manager.activatePremium(1_800_000_000_000L)

        manager.deactivatePremium()

        assertEquals(null, manager.trialEndsAtMillis.value)
        assertEquals(false, store["is_premium"])
    }

    // Trial automático sin tarjeta (pedido explícito del usuario
    // 2026-09-12): todo el que instala la app tiene 3 días de Premium
    // completo gratis, medido desde firstInstallTime -- sin suscripción, sin
    // pasar por activatePremium(). isPaidPremium es la que distingue esto de
    // un cliente pagador real (la usa PremiumScreen para no ocultarle el
    // botón de suscripción a alguien que solo está en el trial).
    @Test
    fun `un usuario recien instalado esta en trial automatico sin haber comprado nada`() {
        val manager = newManager(firstInstallTimeMillis = System.currentTimeMillis())

        assertTrue(manager.isPremium.value)
        assertFalse(manager.isPaidPremium.value)
        assertEquals(3, manager.autoTrialDaysRemaining.value)
    }

    @Test
    fun `un usuario instalado hace mas de 3 dias ya no esta en trial automatico`() {
        val haceCuatroDias = System.currentTimeMillis() - 4 * MILLIS_PER_DAY
        val manager = newManager(firstInstallTimeMillis = haceCuatroDias)

        assertFalse(manager.isPremium.value)
        assertEquals(null, manager.autoTrialDaysRemaining.value)
    }

    @Test
    fun `deactivatePremium no corta el trial automatico de un usuario recien instalado`() {
        // Este es el bug que se evitó a propósito: restorePurchases() llama
        // a deactivatePremium() en cada arranque de la app cuando no
        // encuentra ninguna compra que restaurar -- eso no debe cortarle el
        // trial automático a alguien que nunca intentó pagar.
        val manager = newManager(firstInstallTimeMillis = System.currentTimeMillis())

        manager.deactivatePremium()

        assertTrue(manager.isPremium.value)
        assertFalse(manager.isPaidPremium.value)
    }

    @Test
    fun `un usuario pagador sigue siendo premium aunque el trial automatico ya haya expirado`() {
        val haceCuatroDias = System.currentTimeMillis() - 4 * MILLIS_PER_DAY
        val manager = newManager(firstInstallTimeMillis = haceCuatroDias)

        manager.activatePremium()

        assertTrue(manager.isPremium.value)
        assertTrue(manager.isPaidPremium.value)
    }

    @Test
    fun `isWithinAutoTrial es verdadero justo el ultimo dia y falso al llegar al limite`() {
        val instalacion = 0L
        val trialDeTresDias = 3

        assertTrue(isWithinAutoTrial(instalacion, nowMillis = 2 * MILLIS_PER_DAY, trialDeTresDias))
        assertFalse(isWithinAutoTrial(instalacion, nowMillis = 3 * MILLIS_PER_DAY, trialDeTresDias))
    }

    @Test
    fun `autoTrialDaysRemaining cuenta hacia abajo y termina en null`() {
        val instalacion = 0L
        val trialDeTresDias = 3

        assertEquals(3, autoTrialDaysRemaining(instalacion, nowMillis = 0L, trialDeTresDias))
        assertEquals(2, autoTrialDaysRemaining(instalacion, nowMillis = MILLIS_PER_DAY, trialDeTresDias))
        assertEquals(1, autoTrialDaysRemaining(instalacion, nowMillis = 2 * MILLIS_PER_DAY, trialDeTresDias))
        assertEquals(null, autoTrialDaysRemaining(instalacion, nowMillis = 3 * MILLIS_PER_DAY, trialDeTresDias))
    }
}
