package com.docsmart.features.scanner.presentation

import android.net.Uri
import com.docsmart.core.media.SoundEffectPlayer
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `ScannerViewModel` es estado puro (no lanza corrutinas), asi que se prueba
 * de forma sincrona. `Uri` se mockea: la clase real de `android.net` no
 * funciona en un test JVM plano.
 */
class ScannerViewModelTest {
    private lateinit var soundEffectPlayer: SoundEffectPlayer
    private lateinit var viewModel: ScannerViewModel

    @BeforeEach
    fun setUp() {
        soundEffectPlayer = mockk(relaxed = true)
        viewModel = ScannerViewModel(soundEffectPlayer)
    }

    @Test
    fun `el estado inicial es modo Documento sin paginas ni error`() {
        val state = viewModel.uiState.value

        assertEquals(ScannerMode.DOCUMENT, state.selectedMode)
        assertTrue(state.scannedPages.isEmpty())
        assertFalse(state.isPdf)
        assertFalse(state.isProcessing)
        assertNull(state.error)
    }

    @Test
    fun `setMode cambia el modo sin tocar el resto del estado`() {
        val pages = listOf(mockk<Uri>())
        viewModel.onScanComplete(pages, isPdf = true)

        viewModel.setMode(ScannerMode.PHOTO)

        val state = viewModel.uiState.value
        assertEquals(ScannerMode.PHOTO, state.selectedMode)
        assertEquals(pages, state.scannedPages)
        assertTrue(state.isPdf)
    }

    @Test
    fun `onScanComplete guarda las paginas y el formato y reproduce el sonido`() {
        val pages = listOf(mockk<Uri>(), mockk<Uri>())

        viewModel.onScanComplete(pages, isPdf = true)

        val state = viewModel.uiState.value
        assertEquals(pages, state.scannedPages)
        assertTrue(state.isPdf)
        assertFalse(state.isProcessing)
        verify(exactly = 1) { soundEffectPlayer.playScan() }
    }

    @Test
    fun `onScanComplete sin paginas no reproduce sonido`() {
        viewModel.onScanComplete(emptyList())

        assertTrue(viewModel.uiState.value.scannedPages.isEmpty())
        verify(exactly = 0) { soundEffectPlayer.playScan() }
    }

    @Test
    fun `onScanComplete usa isPdf falso por defecto`() {
        viewModel.onScanComplete(listOf(mockk<Uri>()), isPdf = true)

        viewModel.onScanComplete(listOf(mockk<Uri>()))

        assertFalse(viewModel.uiState.value.isPdf)
    }

    @Test
    fun `onError guarda el mensaje y apaga isProcessing`() {
        viewModel.onError("no se pudo escanear")

        val state = viewModel.uiState.value
        assertEquals("no se pudo escanear", state.error)
        assertFalse(state.isProcessing)
    }

    @Test
    fun `un escaneo exitoso limpia el error anterior`() {
        viewModel.onError("no se pudo escanear")

        viewModel.onScanComplete(listOf(mockk<Uri>()))

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `reset vuelve al estado inicial incluso al modo Documento`() {
        viewModel.setMode(ScannerMode.PHOTO)
        viewModel.onScanComplete(listOf(mockk<Uri>()), isPdf = true)
        viewModel.onError("x")

        viewModel.reset()

        assertEquals(ScannerUiState(), viewModel.uiState.value)
    }
}
