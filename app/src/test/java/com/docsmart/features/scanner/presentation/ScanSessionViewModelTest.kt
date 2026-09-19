package com.docsmart.features.scanner.presentation

import android.app.Activity
import android.content.Context
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.scanner.domain.ScanSessionManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * `ScanSessionViewModel` estaba en 0%: cubre el limite diario de escaneos
 * guardados (8/dia, Premium exento), el anuncio recompensado que suma un uso
 * y las acciones sobre archivos de la sesion (que ya no deben tumbar la app si
 * el archivo dejo de existir).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanSessionViewModelTest {
    private lateinit var sessionManager: ScanSessionManager
    private lateinit var dailyLimitManager: DailyLimitManager
    private lateinit var premiumManager: PremiumManager
    private lateinit var adManager: AdManager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        sessionManager = mockk()
        every { sessionManager.scannedFiles } returns MutableStateFlow<List<DocumentUiModel>>(emptyList())
        dailyLimitManager = mockk()
        every { dailyLimitManager.getScanSavedCount() } returns 3
        every { dailyLimitManager.getScanSavedLimit() } returns 8
        premiumManager = mockk()
        // canPerform() real: Premium pasa siempre, si no evalua el chequeo diario.
        every { premiumManager.canPerform(any()) } answers { firstArg<() -> Boolean>().invoke() }
        adManager = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = ScanSessionViewModel(sessionManager, dailyLimitManager, premiumManager, adManager)

    @Test
    fun `el estado inicial refleja el contador y el limite diario reales`() {
        val state = buildViewModel().saveLimitState.value

        assertEquals(3, state.savedCount)
        assertEquals(8, state.savedLimit)
        assertFalse(state.showLimitDialog)
    }

    @Test
    fun `requestScanSaveSlot permite guardar si aun quedan usos hoy`() {
        every { dailyLimitManager.canSaveScan() } returns true
        val viewModel = buildViewModel()

        assertTrue(viewModel.requestScanSaveSlot())
        assertFalse(viewModel.saveLimitState.value.showLimitDialog)
    }

    @Test
    fun `requestScanSaveSlot con el limite agotado bloquea y muestra el dialogo`() {
        every { dailyLimitManager.canSaveScan() } returns false
        val viewModel = buildViewModel()

        assertFalse(viewModel.requestScanSaveSlot())
        assertTrue(viewModel.saveLimitState.value.showLimitDialog)
    }

    @Test
    fun `Premium nunca se bloquea aunque el limite diario este agotado`() {
        every { premiumManager.canPerform(any()) } returns true
        every { dailyLimitManager.canSaveScan() } returns false
        val viewModel = buildViewModel()

        assertTrue(viewModel.requestScanSaveSlot())
        assertFalse(viewModel.saveLimitState.value.showLimitDialog)
    }

    @Test
    fun `dismissScanLimitDialog cierra el dialogo`() {
        every { dailyLimitManager.canSaveScan() } returns false
        val viewModel = buildViewModel()
        viewModel.requestScanSaveSlot()

        viewModel.dismissScanLimitDialog()

        assertFalse(viewModel.saveLimitState.value.showLimitDialog)
    }

    @Test
    fun `registerScanSaved registra el uso y refresca el contador`() {
        every { dailyLimitManager.registerScanSaved() } just Runs
        every { dailyLimitManager.getScanSavedCount() } returnsMany listOf(3, 4)
        val viewModel = buildViewModel()
        assertEquals(3, viewModel.saveLimitState.value.savedCount)

        viewModel.registerScanSaved()

        verify(exactly = 1) { dailyLimitManager.registerScanSaved() }
        assertEquals(4, viewModel.saveLimitState.value.savedCount)
    }

    // ── Anuncio recompensado ──────────────────────────────────────────────────

    @Test
    fun `watchAdForScanSave cierra el dialogo y al ser recompensado suma un uso y refresca el limite`() {
        val onRewarded = slot<() -> Unit>()
        every { adManager.showRewardedAd(any(), capture(onRewarded), any()) } just Runs
        every { dailyLimitManager.canSaveScan() } returns false
        every { dailyLimitManager.addRewardedScanSave() } just Runs
        every { dailyLimitManager.getScanSavedLimit() } returnsMany listOf(8, 9)
        val viewModel = buildViewModel()
        viewModel.requestScanSaveSlot()
        assertTrue(viewModel.saveLimitState.value.showLimitDialog)

        viewModel.watchAdForScanSave(mockk<Activity>())

        assertFalse(viewModel.saveLimitState.value.showLimitDialog)
        verify(exactly = 0) { dailyLimitManager.addRewardedScanSave() }

        onRewarded.captured.invoke()

        verify(exactly = 1) { dailyLimitManager.addRewardedScanSave() }
        assertEquals(9, viewModel.saveLimitState.value.savedLimit)
    }

    @Test
    fun `si el anuncio falla no se suma ningun uso`() {
        val onFailed = slot<() -> Unit>()
        every { adManager.showRewardedAd(any(), any(), capture(onFailed)) } just Runs
        every { dailyLimitManager.addRewardedScanSave() } just Runs
        val viewModel = buildViewModel()

        viewModel.watchAdForScanSave(mockk<Activity>())
        onFailed.captured.invoke()

        verify(exactly = 0) { dailyLimitManager.addRewardedScanSave() }
    }

    // ── Acciones sobre archivos de la sesion ──────────────────────────────────

    @Test
    fun `addFile y clearSession delegan en ScanSessionManager`() {
        val file = File("scan.pdf")
        val context = mockk<Context>()
        every { sessionManager.addFile(file, context) } just Runs
        every { sessionManager.clear() } just Runs
        val viewModel = buildViewModel()

        viewModel.addFile(file, context)
        viewModel.clearSession()

        verify { sessionManager.addFile(file, context) }
        verify { sessionManager.clear() }
    }

    @Test
    fun `toggleFavorite renameDocument y deleteDocument delegan en ScanSessionManager`() =
        runTest {
            coEvery { sessionManager.toggleFavorite("d1") } just Runs
            coEvery { sessionManager.renameDocument("d1", "nuevo") } just Runs
            coEvery { sessionManager.deleteDocument("d1") } returns true
            val viewModel = buildViewModel()

            viewModel.toggleFavorite("d1")
            viewModel.renameDocument("d1", "nuevo")
            viewModel.deleteDocument("d1")

            coVerify { sessionManager.toggleFavorite("d1") }
            coVerify { sessionManager.renameDocument("d1", "nuevo") }
            coVerify { sessionManager.deleteDocument("d1") }
        }

    // Bug real corregido: renombrar/mover a la papelera un archivo que ya no
    // existe lanza en viewModelScope.launch sin manejador y tumbaba la app.
    @Test
    fun `una falla al renombrar no propaga la excepcion`() =
        runTest {
            coEvery { sessionManager.renameDocument("d1", "x") } throws java.io.IOException("ya no existe")
            val viewModel = buildViewModel()

            viewModel.renameDocument("d1", "x")

            coVerify(exactly = 1) { sessionManager.renameDocument("d1", "x") }
        }

    @Test
    fun `una falla al eliminar no propaga la excepcion`() =
        runTest {
            coEvery { sessionManager.deleteDocument("d1") } throws IllegalStateException("papelera")
            val viewModel = buildViewModel()

            viewModel.deleteDocument("d1")

            coVerify(exactly = 1) { sessionManager.deleteDocument("d1") }
        }

    @Test
    fun `una falla al marcar favorito no propaga la excepcion`() =
        runTest {
            coEvery { sessionManager.toggleFavorite("d1") } throws IllegalStateException("db")
            val viewModel = buildViewModel()

            viewModel.toggleFavorite("d1")

            coVerify(exactly = 1) { sessionManager.toggleFavorite("d1") }
        }
}
