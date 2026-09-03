package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * RF-CONV-09: antes esta conversión solo volcaba texto plano (un .docx
 * mínimo escrito a mano con un párrafo por línea de `PdfTextExtractor`).
 * Ahora reconstruye negrita/cursiva/tamaño de fuente por fragmento real
 * (vía `PdfCanvasProcessor` + `TextRenderInfo`) y separa párrafos según el
 * espaciado vertical real entre líneas -- no cada salto de línea del PDF.
 * El PDF de prueba se arma con líneas en coordenadas Y controladas para
 * ejercitar exactamente esa lógica (ver constantes de `PdfToWordUseCase`:
 * gap > 1.6x el tamaño de fuente = párrafo nuevo; gap menor = misma línea
 * lógica ajustada).
 */
class PdfToWordUseCaseTest {

    private lateinit var cacheDir: File
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: PdfToWordUseCase

    @BeforeEach
    fun setUp() {
        cacheDir = Files.createTempDirectory("docsmart_pdftoword_cache_").toFile()
        filesDir = Files.createTempDirectory("docsmart_pdftoword_files_").toFile()
        context = mockk()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        useCase = PdfToWordUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        cacheDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    @Test
    fun `lineas separadas por poco espacio quedan en el mismo parrafo, un salto grande crea uno nuevo`() = runTest {
        stubResolver(createFormattedPdf())

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val outputFile = (result as ConversionResult.Success).outputFile
        assertEquals("docx", outputFile.extension)

        val doc = XWPFDocument(outputFile.inputStream())
        assertEquals(2, doc.paragraphs.size)
        val primerParrafo = doc.paragraphs[0].text
        assertTrue(primerParrafo.contains("Primera linea del primer parrafo."))
        assertTrue(primerParrafo.contains("Continua en la misma linea logica."))
    }

    @Test
    fun `preserva negrita, cursiva y tamano de fuente por fragmento real`() = runTest {
        stubResolver(createFormattedPdf())

        val result = useCase(mockk<Uri>(), "salida")

        val outputFile = (result as ConversionResult.Success).outputFile
        val doc = XWPFDocument(outputFile.inputStream())
        val segundoParrafo = doc.paragraphs[1]

        val boldRun = segundoParrafo.runs.first { it.text().contains("negrita") }
        assertTrue(boldRun.isBold)
        assertTrue(!boldRun.isItalic)

        val italicRun = segundoParrafo.runs.first { it.text().contains("cursiva") }
        assertTrue(italicRun.isItalic)
        assertEquals(16, italicRun.fontSize)
    }

    // Bug real reportado por el usuario 2026-09-03 (conversión desde un PDF
    // recibido por WhatsApp): dos fragmentos de texto en la MISMA línea,
    // separados por un hueco horizontal real pero sin ningún carácter " "
    // literal entre ellos (así codifican el espaciado muchos generadores de
    // PDF) -- antes del fix quedaban pegados: "Funza,Cundinamarca,".
    @Test
    fun `un hueco horizontal real entre fragmentos de la misma linea se convierte en espacio`() = runTest {
        stubResolver(createSameLineGapPdf())

        val result = useCase(mockk<Uri>(), "salida")

        val outputFile = (result as ConversionResult.Success).outputFile
        val doc = XWPFDocument(outputFile.inputStream())
        val texto = doc.paragraphs.joinToString(" ") { it.text }

        assertTrue(texto.contains("Funza, Cundinamarca,"))
    }

    @Test
    fun `PDF sin texto extraible devuelve Error`() = runTest {
        stubResolver(createBlankPdf())

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Error)
    }

    @Test
    fun `archivo no legible devuelve Error`() = runTest {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(uri) } returns null
        every { context.contentResolver } returns resolver

        val result = useCase(uri, "salida")

        assertTrue(result is ConversionResult.Error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createFormattedPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val normal = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA,
            "", EmbeddingStrategy.PREFER_EMBEDDED
        )
        val bold = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD,
            "", EmbeddingStrategy.PREFER_EMBEDDED
        )
        val italic = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA_OBLIQUE,
            "", EmbeddingStrategy.PREFER_EMBEDDED
        )

        val page = pdfDoc.addNewPage()
        val canvas = PdfCanvas(page)

        // Gap de 14pt con tamaño 12 (< 1.6*12=19.2) -- misma línea lógica.
        canvas.beginText().setFontAndSize(normal, 12f).moveText(50.0, 700.0)
            .showText("Primera linea del primer parrafo.").endText()
        canvas.beginText().setFontAndSize(normal, 12f).moveText(50.0, 686.0)
            .showText("Continua en la misma linea logica.").endText()

        // Gap de 40pt (> 1.6*12=19.2) -- nuevo párrafo.
        canvas.beginText().setFontAndSize(bold, 12f).moveText(50.0, 646.0)
            .showText("Este parrafo esta en negrita.").endText()
        // Gap de 14pt con tamaño 16 (< 1.6*16=25.6) -- misma línea lógica.
        canvas.beginText().setFontAndSize(italic, 16f).moveText(50.0, 632.0)
            .showText("Esta linea es cursiva y mas grande.").endText()

        pdfDoc.close()
        return out.toByteArray()
    }

    private fun createSameLineGapPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        val normal = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA,
            "", EmbeddingStrategy.PREFER_EMBEDDED
        )

        val page = pdfDoc.addNewPage()
        val canvas = PdfCanvas(page)

        // Misma Y (misma línea), sin espacio literal entre "Funza," y
        // "Cundinamarca," -- el hueco de 100pt entre el fin del primer
        // fragmento y el inicio del segundo es puramente un desplazamiento
        // de cursor, como hacen muchos conversores de WhatsApp/PDF.
        canvas.beginText().setFontAndSize(normal, 12f).moveText(50.0, 700.0)
            .showText("Funza,").endText()
        canvas.beginText().setFontAndSize(normal, 12f).moveText(150.0, 700.0)
            .showText("Cundinamarca,").endText()

        pdfDoc.close()
        return out.toByteArray()
    }

    private fun createBlankPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(out))
        pdfDoc.addNewPage()
        pdfDoc.close()
        return out.toByteArray()
    }
}
