package com.docsmart.core.ads

import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Ronda 16: cubre el "reloj de confianza" de `DailyLimitManager` (logica pura
 * `computeTrustedClock`), el reseteo diario de "Extraer imagenes" y la
 * migracion del ancla defectuosa que nunca avanzaba tras un reinicio.
 *
 * En la JVM `SystemClock.elapsedRealtime()` lanza y `elapsedRealtimeMillisSafe()`
 * cae a `currentTimeMillis()`, asi que en los tests de integracion un ancla con
 * `reset_anchor_elapsed` gigante simula un reinicio del dispositivo.
 */
class DailyLimitClockTest {
    private lateinit var store: MutableMap<String, Any?>
    private lateinit var manager: DailyLimitManager

    @BeforeEach
    fun setUp() {
        store = mutableMapOf()
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns fakePrefs()
        manager = DailyLimitManager(context)
    }

    // ── computeTrustedClock (logica pura) ─────────────────────────────────────

    @Test
    fun `primera llamada ancla al reloj actual`() {
        val clock = computeTrustedClock(0L, 0L, currentWall = 5_000L, currentElapsed = 700L)

        assertEquals(TrustedClock(5_000L, 5_000L, 700L), clock)
    }

    @Test
    fun `avanza con elapsedRealtime y persiste el ancla nueva`() {
        val clock = computeTrustedClock(1_000L, 100L, currentWall = 10_000L, currentElapsed = 600L)

        assertEquals(TrustedClock(1_500L, 1_500L, 600L), clock)
    }

    @Test
    fun `un reloj de pared adelantado no hace avanzar el tiempo de confianza`() {
        val clock = computeTrustedClock(1_000L, 100L, currentWall = 99_999_999L, currentElapsed = 600L)

        assertEquals(1_500L, clock.trustedNow)
    }

    @Test
    fun `un reloj de pared por detras del ancla no mueve el ancla`() {
        val clock = computeTrustedClock(1_000L, 100L, currentWall = 900L, currentElapsed = 600L)

        assertEquals(TrustedClock(900L, 1_000L, 100L), clock)
    }

    @Test
    fun `tras un reinicio se congela en el ancla y se rebasa elapsed`() {
        val clock = computeTrustedClock(1_000L, 100L, currentWall = 99_999_999L, currentElapsed = 50L)

        assertEquals(TrustedClock(1_000L, 1_000L, 50L), clock)
    }

    @Test
    fun `tras reiniciar el tiempo vuelve a avanzar en tiempo real desde el ancla`() {
        val clock = computeTrustedClock(1_000L, 50L, currentWall = 99_999_999L, currentElapsed = 350L)

        assertEquals(1_300L, clock.trustedNow)
    }

    @Test
    fun `el ancla persistida antes de un reinicio conserva el tiempo ya transcurrido`() {
        val threeDays = 3L * 24 * 60 * 60 * 1000
        // Lectura antes del reinicio: el ancla avanza 3 dias.
        val before = computeTrustedClock(1_000L, 100L, currentWall = 10_000_000_000L, currentElapsed = 100L + threeDays)
        // Reinicio: elapsed vuelve a un valor chico.
        val after = computeTrustedClock(before.anchorWall, before.anchorElapsed, 10_000_000_000L, 5_000L)

        assertEquals(1_000L + threeDays, after.trustedNow)
    }

    // ── DailyLimitManager ─────────────────────────────────────────────────────

    @Test
    fun `EXTRACT_IMAGES se resetea al cambiar de dia`() {
        repeat(DailyLimitManager.LIMIT_PDF_TOOLS) { manager.registerPdfTool("EXTRACT_IMAGES") }
        assertFalse(manager.canUsePdfTool("EXTRACT_IMAGES"))

        // Simula que pasaron dias reales desde el ultimo reseteo.
        store["current_date"] = "2000-01-01"
        store["last_reset_trusted"] = 1L

        assertTrue(manager.canUsePdfTool("EXTRACT_IMAGES"))
        assertEquals(0, manager.getPdfToolCount("EXTRACT_IMAGES"))
    }

    @Test
    fun `migracion del ancla defectuoso no deja los contadores sin resetear indefinidamente`() {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        // Estado defectuoso previo: ancla vieja (10 dias), reinicio detectado
        // (elapsed del ancla gigante) y ultimo reseteo hace 3 dias.
        store["current_date"] = "2000-01-01"
        store["reset_anchor_wall"] = now - 10 * day
        store["reset_anchor_elapsed"] = Long.MAX_VALUE / 2
        store["last_reset_trusted"] = now - 3 * day
        store["count_conversions"] = 5

        // Seguro ante manipulacion: justo tras migrar no se resetea todavia.
        assertFalse(manager.canConvert())
        assertEquals(now - 3 * day, store["reset_anchor_wall"])
        assertEquals(2, store["reset_clock_version"])

        // Tras 21 h reales de uptime desde la migracion, el reseteo si ocurre.
        store["reset_anchor_elapsed"] = (store["reset_anchor_elapsed"] as Long) - 21L * 60 * 60 * 1000

        assertTrue(manager.canConvert())
        assertEquals(0, manager.getConversionCount())
    }

    @Test
    fun `adelantar el reloj de pared no resetea los contadores`() {
        repeat(DailyLimitManager.LIMIT_CONVERSIONS) { manager.registerConversion() }
        assertFalse(manager.canConvert())

        // Fecha guardada distinta de hoy (como si el usuario cambio la fecha),
        // pero el ultimo reseteo de confianza fue hace instantes.
        store["current_date"] = "2000-01-01"

        assertFalse(manager.canConvert())
    }

    private fun fakePrefs(): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String?>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Int>()
            editor
        }
        every { editor.putLong(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Long>()
            editor
        }
        every { editor.apply() } just Runs

        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers {
            (store[firstArg<String>()] as? String) ?: secondArg()
        }
        every { prefs.getInt(any(), any()) } answers {
            (store[firstArg<String>()] as? Int) ?: secondArg()
        }
        every { prefs.getLong(any(), any()) } answers {
            (store[firstArg<String>()] as? Long) ?: secondArg()
        }
        return prefs
    }
}
