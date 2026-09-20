package com.docsmart.features.scanner.presentation

import android.net.Uri
import com.docsmart.features.scanner.domain.ScanColorMode
import com.docsmart.features.scanner.domain.ScanImageEditor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** El ViewModel solo delega en [ScanImageEditor]: se verifica que pase los parámetros y devuelva el resultado. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanImageEditorViewModelTest {
    private lateinit var editor: ScanImageEditor
    private lateinit var viewModel: ScanImageEditorViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        editor = mockk()
        viewModel = ScanImageEditorViewModel(editor)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `applyAdjustments entrega el uri resultante del editor`() {
        val input = mockk<Uri>()
        val output = mockk<Uri>()
        coEvery { editor.applyAdjustments(input, 10, -5, 80) } returns output

        var received: Uri? = null
        viewModel.applyAdjustments(input, 10, -5, 80) { received = it }

        assertEquals(output, received)
        coVerify(exactly = 1) { editor.applyAdjustments(input, 10, -5, 80) }
    }

    @Test
    fun `applyAdjustments entrega null si el editor falla`() {
        val input = mockk<Uri>()
        coEvery { editor.applyAdjustments(any(), any(), any(), any()) } returns null

        var called = false
        var received: Uri? = mockk()
        viewModel.applyAdjustments(input, 0, 0, 100) {
            called = true
            received = it
        }

        assertEquals(true, called)
        assertNull(received)
    }

    @Test
    fun `applyColorMode delega en el editor`() =
        runTest {
            val input = mockk<Uri>()
            val output = mockk<Uri>()
            coEvery { editor.applyColorMode(input, ScanColorMode.GRAYSCALE) } returns output

            assertEquals(output, viewModel.applyColorMode(input, ScanColorMode.GRAYSCALE))
        }

    @Test
    fun `deleteCachedFile delega en el editor`() {
        val uri = mockk<Uri>()
        every { editor.deleteCachedFile(uri) } returns Unit

        viewModel.deleteCachedFile(uri)

        verify(exactly = 1) { editor.deleteCachedFile(uri) }
    }
}
