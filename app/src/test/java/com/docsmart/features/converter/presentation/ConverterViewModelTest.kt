package com.docsmart.features.converter.presentation

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.docsmart.R
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.media.SoundEffectPlayer
import com.docsmart.core.premium.PremiumManager
import com.docsmart.core.util.DownloadsSaver
import com.docsmart.features.converter.domain.model.ConversionResult
import com.docsmart.features.converter.domain.model.ConversionType
import com.docsmart.features.converter.domain.usecase.ConvertImageToPdfUseCase
import com.docsmart.features.converter.domain.usecase.ImageFormatUseCase
import com.docsmart.features.converter.domain.usecase.PdfToImageUseCase
import com.docsmart.features.converter.domain.usecase.WordToTextUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Ronda 15: complementa ConverterViewModelBatchTest (que cubre la logica del
 * modo lote) con las transiciones de estado de convert() en modo archivo
 * unico, los guards de doble toque, el manejo de excepciones/cancelacion, el
 * guardado en Descargas y el reseteo de las banderas de "guardado".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConverterViewModelTest {
    private lateinit var context: Context
    private lateinit var convertImageToPdf: ConvertImageToPdfUseCase
    private lateinit var pdfToImage: PdfToImageUseCase
    private lateinit var imageFormat: ImageFormatUseCase
    private lateinit var wordToText: WordToTextUseCase
    private lateinit var adManager: AdManager
    private lateinit var dailyLimitManager: DailyLimitManager
    private lateinit var premiumManager: PremiumManager
    private lateinit var soundEffectPlayer: SoundEffectPlayer
    private lateinit var isPremium: MutableStateFlow<Boolean>
    private lateinit var viewModel: ConverterViewModel

    private val uri = mockk<Uri>()
    private val outFile = File("salida.pdf")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        context = mockk()
        convertImageToPdf = mockk()
        pdfToImage = mockk()
        imageFormat = mockk()
        wordToText = mockk()
        adManager = mockk()
        dailyLimitManager = mockk(relaxed = true)
        premiumManager = mockk()
        soundEffectPlayer = mockk(relaxed = true)
        isPremium = MutableStateFlow(false)

        every { dailyLimitManager.canConvert() } returns true
        every { dailyLimitManager.getConversionCount() } returns 0
        every { dailyLimitManager.getConversionLimit() } returns 5
        every { premiumManager.isPremium } returns isPremium
        every { premiumManager.canPerform(any()) } answers { firstArg<() -> Boolean>().invoke() }

        viewModel =
            ConverterViewModel(
                convertImageToPdf = convertImageToPdf,
                pdfToImage = pdfToImage,
                pdfToText = mockk(),
                pdfToWord = mockk(),
                pdfToHtml = mockk(),
                imageFormat = imageFormat,
                wordToPdf = mockk(),
                wordToText = wordToText,
                wordToHtml = mockk(),
                excelToPdf = mockk(),
                excelToCsv = mockk(),
                excelToHtml = mockk(),
                pptToPdf = mockk(),
                pptToText = mockk(),
                adManager = adManager,
                dailyLimitManager = dailyLimitManager,
                premiumManager = premiumManager,
                soundEffectPlayer = soundEffectPlayer,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun success(
        file: File = outFile,
        extra: List<File> = emptyList(),
    ) = ConversionResult.Success(file, 1, 4, extra)

    private fun selectWordToText(vararg uris: Uri) {
        viewModel.onTypeSelected(ConversionType.WORD_TO_TXT)
        viewModel.onFilesSelected(uris.toList())
    }

    private fun mockDisplayNames(names: Map<Uri, String>) {
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        names.forEach { (u, name) ->
            val cursor = mockk<Cursor>(relaxed = true)
            every { cursor.moveToFirst() } returns true
            every { cursor.getString(0) } returns name
            every { resolver.query(u, any<Array<String>>(), null, null, null) } returns cursor
        }
    }

    // ── init / seleccion ────────────────────────────────────────────────────

    @Test
    fun `al crearse carga el contador y el limite diarios`() {
        viewModel.uiState.value.conversionCount shouldBe 0
        viewModel.uiState.value.conversionLimit shouldBe 5
    }

    @Test
    fun `onTypeSelected adopta el archivo precargado solo si la categoria coincide y una sola vez`() {
        viewModel.preloadFile(uri, "PDF")

        viewModel.onTypeSelected(ConversionType.PDF_TO_TXT)
        viewModel.uiState.value.selectedFiles shouldBe listOf(uri)

        viewModel.onTypeSelected(ConversionType.PDF_TO_WORD)
        viewModel.uiState.value.selectedFiles shouldBe emptyList()
    }

    @Test
    fun `onTypeSelected descarta el archivo precargado de otra categoria`() {
        viewModel.preloadFile(uri, "Imagen")

        viewModel.onTypeSelected(ConversionType.PDF_TO_TXT)

        viewModel.uiState.value.selectedFiles shouldBe emptyList()
    }

    @Test
    fun `removeImage quita solo ese archivo y clearAll vuelve al estado inicial`() {
        val other = mockk<Uri>()
        viewModel.onFilesSelected(listOf(uri, other))

        viewModel.removeImage(uri)
        viewModel.uiState.value.selectedFiles shouldBe listOf(other)

        viewModel.onFileNameChange("algo")
        viewModel.clearAll()
        viewModel.uiState.value.selectedFiles shouldBe emptyList()
        viewModel.uiState.value.fileName shouldBe ""
    }

    // ── convert(): archivo unico ────────────────────────────────────────────

    @Test
    fun `convert sin tipo o sin archivos no hace nada`() =
        runTest {
            viewModel.convert(context)
            viewModel.onTypeSelected(ConversionType.WORD_TO_TXT)
            viewModel.convert(context)

            coVerify(exactly = 0) { wordToText(any(), any()) }
            viewModel.uiState.value.isConverting shouldBe false
        }

    @Test
    fun `convert con exito guarda el resultado, registra un uso y reproduce el sonido`() =
        runTest {
            selectWordToText(uri)
            viewModel.onFileNameChange("../informe")
            coEvery { wordToText(uri, "informe") } returns success()
            every { dailyLimitManager.getConversionCount() } returns 1

            viewModel.convert(context)

            val state = viewModel.uiState.value
            state.isConverting shouldBe false
            state.outputFile shouldBe outFile
            state.conversionResult shouldBe success()
            state.conversionCount shouldBe 1
            verify(exactly = 1) { dailyLimitManager.registerConversion() }
            verify(exactly = 1) { soundEffectPlayer.playConvert() }
        }

    @Test
    fun `convert con resultado Error muestra el mensaje y no consume el limite`() =
        runTest {
            selectWordToText(uri)
            coEvery { wordToText(any(), any()) } returns ConversionResult.Error("archivo danado")

            viewModel.convert(context)

            viewModel.uiState.value.errorMessage shouldBe "archivo danado"
            viewModel.uiState.value.isConverting shouldBe false
            verify(exactly = 0) { dailyLimitManager.registerConversion() }
            verify(exactly = 0) { soundEffectPlayer.playConvert() }
        }

    @Test
    fun `convert con el limite diario alcanzado abre el dialogo y no convierte`() =
        runTest {
            every { dailyLimitManager.canConvert() } returns false
            selectWordToText(uri)

            viewModel.convert(context)

            viewModel.uiState.value.showLimitDialog shouldBe true
            viewModel.uiState.value.isConverting shouldBe false
            coVerify(exactly = 0) { wordToText(any(), any()) }
        }

    @Test
    fun `un doble toque en Convertir convierte una sola vez y cuenta un solo uso`() =
        runTest {
            selectWordToText(uri)
            val gate = CompletableDeferred<ConversionResult>()
            coEvery { wordToText(any(), any()) } coAnswers { gate.await() }

            viewModel.convert(context)
            viewModel.convert(context)

            viewModel.uiState.value.isConverting shouldBe true
            coVerify(exactly = 1) { wordToText(any(), any()) }
            gate.complete(success())
            verify(exactly = 1) { dailyLimitManager.registerConversion() }
        }

    @Test
    fun `una excepcion del use case resetea isConverting y muestra el error`() =
        runTest {
            selectWordToText(uri)
            every { context.getString(R.string.general_error_format, "boom") } returns "Error: boom"
            coEvery { wordToText(any(), any()) } throws IllegalStateException("boom")

            viewModel.convert(context)

            viewModel.uiState.value.isConverting shouldBe false
            viewModel.uiState.value.errorMessage shouldBe "Error: boom"
            verify(exactly = 0) { dailyLimitManager.registerConversion() }
        }

    @Test
    fun `un OutOfMemoryError resetea isConverting y muestra el error desconocido`() =
        runTest {
            selectWordToText(uri)
            every { context.getString(R.string.converter_error_unknown) } returns "desconocido"
            coEvery { wordToText(any(), any()) } throws OutOfMemoryError("sin memoria")

            viewModel.convert(context)

            viewModel.uiState.value.isConverting shouldBe false
            viewModel.uiState.value.errorMessage shouldBe "desconocido"
        }

    @Test
    fun `una cancelacion no se muestra como un error de conversion`() =
        runTest {
            selectWordToText(uri)
            coEvery { wordToText(any(), any()) } throws CancellationException("cancelado")

            viewModel.convert(context)

            viewModel.uiState.value.errorMessage shouldBe null
            verify(exactly = 0) { dailyLimitManager.registerConversion() }
        }

    @Test
    fun `IMAGE_TO_PDF con alta resolucion solo la aplica a usuarios Premium`() =
        runTest {
            coEvery { convertImageToPdf(any(), any(), any()) } returns success()
            viewModel.onTypeSelected(ConversionType.IMAGE_TO_PDF)
            viewModel.onFilesSelected(listOf(uri))

            viewModel.convert(context, highResolutionPdf = true)
            coVerify(exactly = 1) { convertImageToPdf(listOf(uri), any(), false) }

            isPremium.value = true
            viewModel.convert(context, highResolutionPdf = true)
            coVerify(exactly = 1) { convertImageToPdf(listOf(uri), any(), true) }
        }

    @Test
    fun `un archivo unico de imagen a JPG usa ImageFormatUseCase`() =
        runTest {
            coEvery { imageFormat(uri, ConversionType.IMAGE_TO_JPG, "foto") } returns success()
            viewModel.onTypeSelected(ConversionType.IMAGE_TO_JPG)
            viewModel.onFilesSelected(listOf(uri))
            viewModel.onFileNameChange("foto")

            viewModel.convert(context)

            coVerify(exactly = 1) { imageFormat(uri, ConversionType.IMAGE_TO_JPG, "foto") }
        }

    @Test
    fun `convertToPdf fija IMAGE_TO_PDF si no habia tipo elegido`() =
        runTest {
            coEvery { convertImageToPdf(any(), any(), any()) } returns success()
            viewModel.onFilesSelected(listOf(uri))

            viewModel.convertToPdf(context)

            viewModel.uiState.value.selectedType shouldBe ConversionType.IMAGE_TO_PDF
            coVerify(exactly = 1) { convertImageToPdf(listOf(uri), any(), false) }
        }

    @Test
    fun `generateFromScan con formato de imagen convierte a ese formato con el nombre elegido`() =
        runTest {
            coEvery { imageFormat(uri, ConversionType.IMAGE_TO_JPG, "escaneo") } returns success()
            viewModel.onFilesSelected(listOf(uri))

            viewModel.generateFromScan(
                context = context,
                imageType = ConversionType.IMAGE_TO_JPG,
                highResolution = false,
                fileName = "escaneo",
            )

            coVerify(exactly = 1) { imageFormat(uri, ConversionType.IMAGE_TO_JPG, "escaneo") }
        }

    @Test
    fun `dismissError y onScanError actualizan el mensaje de error`() {
        viewModel.onScanError("sin camara")
        viewModel.uiState.value.errorMessage shouldBe "sin camara"

        viewModel.dismissError()
        viewModel.uiState.value.errorMessage shouldBe null
    }

    // ── lote: aislamiento de errores por archivo ────────────────────────────

    // Bug real corregido en la ronda 15: una excepcion no atrapada del use case
    // de UN archivo abortaba todo el lote (los restantes ni se intentaban).
    @Test
    fun `en un lote, la excepcion de un archivo no aborta los demas`() =
        runTest {
            val uri2 = mockk<Uri>()
            mockDisplayNames(mapOf(uri to "a.docx", uri2 to "b.docx"))
            every { context.getString(R.string.general_error_format, "boom") } returns "Error: boom"
            coEvery { wordToText(uri, "a") } throws IllegalStateException("boom")
            coEvery { wordToText(uri2, "b") } returns success(File("b.txt"))
            selectWordToText(uri, uri2)

            viewModel.convert(context)

            val state = viewModel.uiState.value
            state.isConverting shouldBe false
            state.batchResults.size shouldBe 2
            (state.batchResults[0].result is ConversionResult.Error) shouldBe true
            (state.batchResults[1].result is ConversionResult.Success) shouldBe true
            verify(exactly = 1) { dailyLimitManager.registerConversion() }
        }

    // ── Descargas ───────────────────────────────────────────────────────────

    // Bug real corregido en la ronda 15: convert() no limpiaba las banderas de
    // "guardado" -- tras guardar un lote y volver a convertir, la pantalla
    // seguia mostrando "Guardado en Descargas" para archivos nunca guardados.
    @Test
    fun `una nueva conversion limpia las banderas de guardado de la anterior`() =
        runTest {
            mockkObject(DownloadsSaver)
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } returns true
            selectWordToText(uri)
            coEvery { wordToText(any(), any()) } returns success()
            viewModel.convert(context)
            viewModel.saveToDownloads(context)
            viewModel.uiState.value.savedToDownloads shouldBe true

            val gate = CompletableDeferred<ConversionResult>()
            coEvery { wordToText(any(), any()) } coAnswers { gate.await() }
            viewModel.convert(context)

            viewModel.uiState.value.savedToDownloads shouldBe false
            viewModel.uiState.value.conversionResult shouldBe null
            gate.complete(success())
        }

    @Test
    fun `elegir archivos nuevos tras guardar un lote limpia el resultado y la bandera de lote`() =
        runTest {
            mockkObject(DownloadsSaver)
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } returns true
            val uri2 = mockk<Uri>()
            mockDisplayNames(mapOf(uri to "a.docx", uri2 to "b.docx"))
            coEvery { wordToText(uri, "a") } returns success(File("a.txt"))
            coEvery { wordToText(uri2, "b") } returns success(File("b.txt"))
            selectWordToText(uri, uri2)
            viewModel.convert(context)
            viewModel.saveAllToDownloads(context)
            viewModel.uiState.value.batchSavedToDownloads shouldBe true

            viewModel.onFilesSelected(listOf(uri))

            viewModel.uiState.value.batchSavedToDownloads shouldBe false
            viewModel.uiState.value.batchResults shouldBe emptyList()
        }

    @Test
    fun `saveToDownloads sin resultado no hace nada`() =
        runTest {
            mockkObject(DownloadsSaver)

            viewModel.saveToDownloads(context)

            coVerify(exactly = 0) { DownloadsSaver.saveFile(any(), any(), any(), any()) }
        }

    @Test
    fun `saveToDownloads guarda tambien las paginas extra de PDF a imagen y usa el mime por extension`() =
        runTest {
            mockkObject(DownloadsSaver)
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } returns true
            val page1 = File("doc_pagina1.jpg")
            val page2 = File("doc_pagina2.jpg")
            coEvery { pdfToImage(uri, any()) } returns success(page1, listOf(page2))
            viewModel.onTypeSelected(ConversionType.PDF_TO_IMAGE)
            viewModel.onFilesSelected(listOf(uri))
            viewModel.convert(context)

            viewModel.saveToDownloads(context)

            coVerify(exactly = 1) { DownloadsSaver.saveFile(any(), page1, "image/jpeg", any()) }
            coVerify(exactly = 1) { DownloadsSaver.saveFile(any(), page2, "image/jpeg", any()) }
            viewModel.uiState.value.savedToDownloads shouldBe true
        }

    @Test
    fun `saveToDownloads intenta todos los archivos aunque el primero falle y avisa del error`() =
        runTest {
            mockkObject(DownloadsSaver)
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } returnsMany listOf(false, true)
            every { context.getString(R.string.pdf_tools_save_error) } returns "no se guardo"
            coEvery { pdfToImage(uri, any()) } returns success(File("p1.jpg"), listOf(File("p2.jpg")))
            viewModel.onTypeSelected(ConversionType.PDF_TO_IMAGE)
            viewModel.onFilesSelected(listOf(uri))
            viewModel.convert(context)

            viewModel.saveToDownloads(context)

            coVerify(exactly = 2) { DownloadsSaver.saveFile(any(), any(), any(), any()) }
            viewModel.uiState.value.errorMessage shouldBe "no se guardo"
            viewModel.uiState.value.savedToDownloads shouldBe false
            viewModel.uiState.value.isSaving shouldBe false
        }

    @Test
    fun `un doble toque en Guardar guarda una sola vez`() =
        runTest {
            mockkObject(DownloadsSaver)
            val gate = CompletableDeferred<Boolean>()
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } coAnswers { gate.await() }
            selectWordToText(uri)
            coEvery { wordToText(any(), any()) } returns success()
            viewModel.convert(context)

            viewModel.saveToDownloads(context)
            viewModel.saveToDownloads(context)

            viewModel.uiState.value.isSaving shouldBe true
            coVerify(exactly = 1) { DownloadsSaver.saveFile(any(), any(), any(), any()) }
            gate.complete(true)
            viewModel.uiState.value.isSaving shouldBe false
        }

    @Test
    fun `una excepcion guardando libera isSaving y muestra el error`() =
        runTest {
            mockkObject(DownloadsSaver)
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } throws IllegalStateException("disco")
            every { context.getString(R.string.general_error_format, "disco") } returns "Error: disco"
            selectWordToText(uri)
            coEvery { wordToText(any(), any()) } returns success()
            viewModel.convert(context)

            viewModel.saveToDownloads(context)

            viewModel.uiState.value.isSaving shouldBe false
            viewModel.uiState.value.errorMessage shouldBe "Error: disco"
        }

    @Test
    fun `saveAllToDownloads sin archivos exitosos no hace nada`() =
        runTest {
            mockkObject(DownloadsSaver)

            viewModel.saveAllToDownloads(context)

            coVerify(exactly = 0) { DownloadsSaver.saveFile(any(), any(), any(), any()) }
            viewModel.uiState.value.isSaving shouldBe false
        }

    @Test
    fun `saveAllToDownloads con un fallo muestra el error del lote`() =
        runTest {
            mockkObject(DownloadsSaver)
            coEvery { DownloadsSaver.saveFile(any(), any(), any(), any()) } returnsMany listOf(false, true)
            every { context.getString(R.string.converter_batch_save_error) } returns "lote incompleto"
            val uri2 = mockk<Uri>()
            mockDisplayNames(mapOf(uri to "a.docx", uri2 to "b.docx"))
            coEvery { wordToText(uri, "a") } returns success(File("a.txt"))
            coEvery { wordToText(uri2, "b") } returns success(File("b.txt"))
            selectWordToText(uri, uri2)
            viewModel.convert(context)

            viewModel.saveAllToDownloads(context)

            coVerify(exactly = 2) { DownloadsSaver.saveFile(any(), any(), any(), any()) }
            viewModel.uiState.value.errorMessage shouldBe "lote incompleto"
            viewModel.uiState.value.batchSavedToDownloads shouldBe false
            viewModel.uiState.value.isSaving shouldBe false
        }

    // ── Rewarded ad ─────────────────────────────────────────────────────────

    @Test
    fun `ver anuncio otorga una conversion extra y refresca el limite`() {
        val activity = mockk<Activity>()
        val onRewarded = slot<() -> Unit>()
        every { adManager.showRewardedAd(activity, capture(onRewarded), any()) } just runs

        viewModel.watchAdForConversion(activity)
        every { dailyLimitManager.getConversionLimit() } returns 6
        onRewarded.captured.invoke()

        verify(exactly = 1) { dailyLimitManager.addRewardedConversion() }
        viewModel.uiState.value.conversionLimit shouldBe 6
    }

    @Test
    fun `si el anuncio falla se muestra el mensaje localizado`() {
        val activity = mockk<Activity>()
        val onFailed = slot<() -> Unit>()
        every { adManager.showRewardedAd(activity, any(), capture(onFailed)) } just runs
        every { activity.getString(R.string.pdf_tools_ad_not_available) } returns "sin anuncio"

        viewModel.watchAdForConversion(activity)
        onFailed.captured.invoke()

        viewModel.uiState.value.errorMessage shouldBe "sin anuncio"
    }
}
