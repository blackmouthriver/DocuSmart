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
 * Cubre el hueco real encontrado en Premium (docs/requirements/settings-premium.md):
 * `DailyLimitManager.canUsePdfTool()`/`registerPdfTool()` ya existían con
 * lógica completa, pero nunca se llamaban desde `PdfToolsViewModel` — el
 * límite diario de 3 usos gratis por herramienta PDF (requerimiento #16)
 * nunca se aplicaba en la práctica. Estos tests fijan el contrato de
 * `DailyLimitManager` como base para esa integración.
 */
class DailyLimitManagerTest {

    private lateinit var context: Context
    private lateinit var manager: DailyLimitManager

    @BeforeEach
    fun setUp() {
        context = mockk()
        every { context.getSharedPreferences(any(), any()) } returns fakeSharedPreferences()
        manager = DailyLimitManager(context)
    }

    @Test
    fun `canConvert es true antes de alcanzar el limite`() {
        assertTrue(manager.canConvert())
    }

    @Test
    fun `canConvert es false al alcanzar el limite de conversiones`() {
        repeat(DailyLimitManager.LIMIT_CONVERSIONS) { manager.registerConversion() }

        assertFalse(manager.canConvert())
    }

    @Test
    fun `addRewardedConversion aumenta el limite disponible`() {
        repeat(DailyLimitManager.LIMIT_CONVERSIONS) { manager.registerConversion() }
        assertFalse(manager.canConvert())

        manager.addRewardedConversion()

        assertTrue(manager.canConvert())
        assertEquals(DailyLimitManager.LIMIT_CONVERSIONS + 1, manager.getConversionLimit())
    }

    @Test
    fun `canUsePdfTool es true antes de alcanzar el limite`() {
        assertTrue(manager.canUsePdfTool("MERGE"))
    }

    @Test
    fun `canUsePdfTool es false al alcanzar el limite para esa herramienta`() {
        repeat(DailyLimitManager.LIMIT_PDF_TOOLS) { manager.registerPdfTool("SPLIT") }

        assertFalse(manager.canUsePdfTool("SPLIT"))
    }

    @Test
    fun `cada herramienta PDF tiene su propio contador independiente`() {
        repeat(DailyLimitManager.LIMIT_PDF_TOOLS) { manager.registerPdfTool("COMPRESS") }

        assertFalse(manager.canUsePdfTool("COMPRESS"))
        assertTrue(manager.canUsePdfTool("ROTATE"))
        assertTrue(manager.canUsePdfTool("MERGE"))
    }

    @Test
    fun `addRewardedPdfTool aumenta el limite disponible para todas las herramientas`() {
        repeat(DailyLimitManager.LIMIT_PDF_TOOLS) { manager.registerPdfTool("ROTATE") }
        assertFalse(manager.canUsePdfTool("ROTATE"))

        manager.addRewardedPdfTool()

        assertTrue(manager.canUsePdfTool("ROTATE"))
        assertEquals(DailyLimitManager.LIMIT_PDF_TOOLS + 1, manager.getPdfToolLimit())
    }

    @Test
    fun `getPdfToolCount refleja los usos registrados de esa herramienta`() {
        manager.registerPdfTool("MERGE")
        manager.registerPdfTool("MERGE")

        assertEquals(2, manager.getPdfToolCount("MERGE"))
        assertEquals(0, manager.getPdfToolCount("SPLIT"))
    }

    // RF-PDF-06: NUMBER_PAGES cayó al principio en la rama `else` de
    // `getPdfToolKey()` (sin case propio), lo que hacía que compartiera
    // contador con `KEY_CONVERSIONS` del Conversor -- usar "Numerar
    // páginas" habría consumido el límite diario de conversiones en vez
    // del propio. Este test fija que tiene su contador independiente.
    @Test
    fun `NUMBER_PAGES tiene su propio contador, independiente de conversiones y otras herramientas`() {
        repeat(DailyLimitManager.LIMIT_PDF_TOOLS) { manager.registerPdfTool("NUMBER_PAGES") }

        assertFalse(manager.canUsePdfTool("NUMBER_PAGES"))
        assertTrue(manager.canUsePdfTool("MERGE"))
        assertTrue(manager.canConvert())
        assertEquals(0, manager.getConversionCount())
    }

    // ── helper: SharedPreferences respaldado por un mapa real ────────────────

    private fun fakeSharedPreferences(): SharedPreferences {
        val store = mutableMapOf<String, Any?>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<String?>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            store[firstArg<String>()] = secondArg<Int>()
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
        return prefs
    }
}
