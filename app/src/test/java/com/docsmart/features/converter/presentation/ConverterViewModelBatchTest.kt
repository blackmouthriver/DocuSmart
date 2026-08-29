package com.docsmart.features.converter.presentation

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.domain.usecase.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * RF-CONV-08: conversión por lotes (N archivos → N salidas), distinto del
 * comportamiento ya existente de IMAGE_TO_PDF (N imágenes → UN solo PDF
 * fusionado, que se mantiene sin cambios y se cubre acá como prueba de
 * regresión). No repite cobertura de cada rama de `runConversionForUri()`
 * (eso ya lo cubren los tests de cada use case individual) -- se enfoca en
 * la lógica nueva: activación del modo lote, resolución de nombres
 * originales, desambiguación de nombres duplicados y el corte por límite
 * diario a mitad de lote.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConverterViewModelBatchTest {

    private lateinit var context          : Context
    private lateinit var wordToText       : WordToTextUseCase
    private lateinit var convertImageToPdf: ConvertImageToPdfUseCase
    private lateinit var adManager        : AdManager
    private lateinit var dailyLimitManager: DailyLimitManager
    private lateinit var viewModel        : ConverterViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        context           = mockk()
        wordToText        = mockk()
        convertImageToPdf = mockk()
        adManager         = mockk()
        dailyLimitManager = mockk(relaxed = true)

        every { adManager.isPremium } returns MutableStateFlow(false)
        every { dailyLimitManager.canConvert() } returns true
        every { dailyLimitManager.getConversionCount() } returns 0
        every { dailyLimitManager.getConversionLimit() } returns 5

        viewModel = ConverterViewModel(
            convertImageToPdf = convertImageToPdf,
            pdfToImage        = mockk(),
            pdfToText         = mockk(),
            pdfToWord         = mockk(),
            pdfToHtml         = mockk(),
            imageFormat       = mockk(),
            wordToPdf         = mockk(),
            wordToText        = wordToText,
            wordToHtml        = mockk(),
            excelToPdf        = mockk(),
            excelToCsv        = mockk(),
            excelToHtml       = mockk(),
            pptToPdf          = mockk(),
            pptToText         = mockk(),
            adManager         = adManager,
            dailyLimitManager = dailyLimitManager
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lote de mas de un archivo produce un resultado por archivo, no una fusion`() = runTest {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        mockDisplayNames(mapOf(uri1 to "informe.docx", uri2 to "carta.docx"))

        val file1 = File.createTempFile("out1", ".txt")
        val file2 = File.createTempFile("out2", ".txt")
        coEvery { wordToText(uri1, "informe") } returns ConversionResult.Success(file1, 1, 1)
        coEvery { wordToText(uri2, "carta") } returns ConversionResult.Success(file2, 1, 1)

        viewModel.onTypeSelected(ConversionType.WORD_TO_TXT)
        viewModel.onFilesSelected(listOf(uri1, uri2))
        viewModel.convert(context)

        val state = viewModel.uiState.value
        assertEquals(2, state.batchResults.size)
        assertTrue(state.batchResults.all { it.result is ConversionResult.Success })
        assertEquals("informe.docx", state.batchResults[0].originalFileName)
        assertEquals("carta.docx", state.batchResults[1].originalFileName)
        assertEquals(null, state.conversionResult)
        coVerify(exactly = 1) { wordToText(uri1, "informe") }
        coVerify(exactly = 1) { wordToText(uri2, "carta") }
        coVerify(exactly = 2) { dailyLimitManager.registerConversion() }
    }

    @Test
    fun `IMAGE_TO_PDF con varios archivos sigue fusionando en un solo PDF, no activa el lote`() = runTest {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val merged = File.createTempFile("merged", ".pdf")
        coEvery { convertImageToPdf(imageUris = listOf(uri1, uri2), fileName = any()) } returns
            ConversionResult.Success(merged, 2, 10)

        viewModel.onTypeSelected(ConversionType.IMAGE_TO_PDF)
        viewModel.onFilesSelected(listOf(uri1, uri2))
        viewModel.convert(context)

        val state = viewModel.uiState.value
        assertTrue(state.batchResults.isEmpty())
        assertTrue(state.conversionResult is ConversionResult.Success)
        coVerify(exactly = 1) { convertImageToPdf(imageUris = listOf(uri1, uri2), fileName = any()) }
    }

    @Test
    fun `nombres originales duplicados en el lote se desambiguan para no sobrescribirse`() = runTest {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        mockDisplayNames(mapOf(uri1 to "informe.docx", uri2 to "informe.docx"))

        val file1 = File.createTempFile("out1", ".txt")
        val file2 = File.createTempFile("out2", ".txt")
        coEvery { wordToText(uri1, "informe") } returns ConversionResult.Success(file1, 1, 1)
        coEvery { wordToText(uri2, "informe (2)") } returns ConversionResult.Success(file2, 1, 1)

        viewModel.onTypeSelected(ConversionType.WORD_TO_TXT)
        viewModel.onFilesSelected(listOf(uri1, uri2))
        viewModel.convert(context)

        coVerify(exactly = 1) { wordToText(uri1, "informe") }
        coVerify(exactly = 1) { wordToText(uri2, "informe (2)") }
    }

    @Test
    fun `si se alcanza el limite diario a mitad del lote, los archivos restantes no se convierten`() = runTest {
        val uri1 = mockk<Uri>()
        val uri2 = mockk<Uri>()
        val uri3 = mockk<Uri>()
        mockDisplayNames(mapOf(uri1 to "a.docx", uri2 to "b.docx", uri3 to "c.docx"))

        // 1ra llamada: guard previo al lote. 2da-4ta: una por archivo dentro del lote.
        every { dailyLimitManager.canConvert() } returnsMany listOf(true, true, true, false)

        val file1 = File.createTempFile("out1", ".txt")
        val file2 = File.createTempFile("out2", ".txt")
        coEvery { wordToText(uri1, "a") } returns ConversionResult.Success(file1, 1, 1)
        coEvery { wordToText(uri2, "b") } returns ConversionResult.Success(file2, 1, 1)

        viewModel.onTypeSelected(ConversionType.WORD_TO_TXT)
        viewModel.onFilesSelected(listOf(uri1, uri2, uri3))
        viewModel.convert(context)

        val state = viewModel.uiState.value
        assertEquals(3, state.batchResults.size)
        assertTrue(state.batchResults[0].result is ConversionResult.Success)
        assertTrue(state.batchResults[1].result is ConversionResult.Success)
        assertTrue(state.batchResults[2].result is ConversionResult.Error)
        coVerify(exactly = 0) { wordToText(uri3, any()) }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun mockDisplayNames(names: Map<Uri, String>) {
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        names.forEach { (uri, name) ->
            val cursor = mockk<Cursor>(relaxed = true)
            every { cursor.moveToFirst() } returns true
            every { cursor.getString(0) } returns name
            every {
                resolver.query(uri, any<Array<String>>(), null, null, null)
            } returns cursor
        }
    }
}
