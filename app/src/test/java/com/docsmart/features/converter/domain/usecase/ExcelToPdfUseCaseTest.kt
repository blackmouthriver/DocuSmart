package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
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
 * Ronda 14 de la auditoría del Convertidor: este use case tenía 0% de
 * cobertura. Al revisarlo con datos reales del reporte JaCoCo se encontró un
 * bug real (ver el test de "hoja sin contenido real"): `writeWorkbookToPdf()`
 * devolvía `wrote=true` con solo poder ABRIR el Workbook, sin importar si
 * alguna fila tenía contenido -- un .xlsx vacío "convertía" con éxito a un
 * PDF que solo trae los encabezados "=== NombreHoja ===", a diferencia de
 * ExcelToCsvUseCase/ExcelToHtmlUseCase (mismo escenario), que sí devuelven
 * `converter_error_empty_spreadsheet`. Corregido en esta ronda.
 */
class ExcelToPdfUseCaseTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: ExcelToPdfUseCase

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_exceltopdf_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        // Los mensajes de error ahora vienen de context.getString() (i18n,
        // repaso general 2026-09-14) -- estos tests solo verifican el tipo
        // de resultado, no el texto exacto.
        every { context.getString(any()) } returns "error"
        useCase = ExcelToPdfUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `convierte una hoja con datos a un PDF con texto extraible`() = runTest {
        stubResolver(createTestXlsx { sheet ->
            sheet.createRow(0).apply { createCell(0).setCellValue("Nombre"); createCell(1).setCellValue("Edad") }
            sheet.createRow(1).apply { createCell(0).setCellValue("Ana"); createCell(1).setCellValue("30") }
        })

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val outputFile = (result as ConversionResult.Success).outputFile
        assertEquals("pdf", outputFile.extension)
        val text = extractPdfText(outputFile)
        assertTrue(text.contains("Nombre"))
        assertTrue(text.contains("Ana"))
    }

    @Test
    fun `todas las hojas del workbook se incluyen en el PDF, no solo la primera`() = runTest {
        stubResolver(createTestXlsxMultiSheet("Hoja1" to "dato-uno", "Hoja2" to "dato-dos"))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val text = extractPdfText((result as ConversionResult.Success).outputFile)
        assertTrue(text.contains("dato-uno"))
        assertTrue(text.contains("dato-dos"))
        assertTrue(text.contains("Hoja1"))
        assertTrue(text.contains("Hoja2"))
    }

    // Bug real corregido en esta ronda: ver el comentario de la clase.
    @Test
    fun `hoja sin ninguna celda con contenido real devuelve Error y no deja un PDF huerfano`() = runTest {
        stubResolver(createTestXlsx { sheet ->
            // La hoja existe y tiene filas, pero todas sus celdas están en
            // blanco -- mismo escenario que "hoja vacía" para el usuario.
            sheet.createRow(0)
            sheet.createRow(1)
        })

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Error)
        val orphanedPdfs = File(filesDir, "converted").listFiles { f -> f.extension == "pdf" }
        assertTrue(orphanedPdfs.isNullOrEmpty(), "no debería quedar un .pdf huérfano: ${orphanedPdfs?.map { it.name }}")
    }

    @Test
    fun `workbook sin ninguna hoja con filas devuelve Error`() = runTest {
        stubResolver(createTestXlsx { /* sin filas */ })

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Error)
    }

    // Hallazgo real de la revisión de corrección 2026-09-16: evaluator.evaluate()
    // puede lanzar para UNA celda puntual (ej. referencia circular) -- sin el
    // try/catch por celda de formatCellSafely(), esa excepción abortaría TODA
    // la conversión en vez de solo esa celda.
    @Test
    fun `una celda con referencia circular no aborta la conversion completa`() = runTest {
        stubResolver(createTestXlsx { sheet ->
            val row = sheet.createRow(0)
            row.createCell(0).setCellValue("Dato normal")
            // B1 y C1 se referencian mutuamente -- POI detecta el ciclo y
            // lanza CircularReferenceException al evaluar cualquiera de las
            // dos, dentro del propio use case (no se evalúa al escribir el
            // fixture).
            row.createCell(1).cellFormula = "C1"
            row.createCell(2).cellFormula = "B1"
        })

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success, "la conversión no debería abortar por una sola celda: $result")
        val text = extractPdfText((result as ConversionResult.Success).outputFile)
        assertTrue(text.contains("Dato normal"))
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

    private fun extractPdfText(pdfFile: File): String {
        PdfDocument(PdfReader(pdfFile)).use { pdfDoc ->
            return (1..pdfDoc.numberOfPages).joinToString("\n") { i ->
                PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i))
            }
        }
    }

    private fun createTestXlsx(fill: (Sheet) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            fill(sheet)
            workbook.write(out)
        }
        return out.toByteArray()
    }

    private fun createTestXlsxMultiSheet(vararg sheets: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            sheets.forEach { (name, value) ->
                workbook.createSheet(name).createRow(0).createCell(0).setCellValue(value)
            }
            workbook.write(out)
        }
        return out.toByteArray()
    }
}
