package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** PDF a Excel con un PDF real generado con iText y el `.xlsx` resultante abierto con Apache POI. */
class PdfToExcelUseCaseTest {
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var useCase: PdfToExcelUseCase

    private class Placed(
        val text: String,
        val x: Double,
        val y: Double,
    )

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_pdftoxlsx_files_").toFile()
        cacheDir = Files.createTempDirectory("docsmart_pdftoxlsx_cache_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        every { context.getString(any()) } returns "error"
        every { context.getString(any(), 1) } returns "Página 1"
        every { context.getString(any(), 2) } returns "Página 2"
        useCase = PdfToExcelUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createPdf(pages: List<List<Placed>>): ByteArray {
        val out = ByteArrayOutputStream()
        val pdf = PdfDocument(PdfWriter(out))
        val font = PdfFontFactory.createFont()
        for (items in pages) {
            val canvas = PdfCanvas(pdf.addNewPage())
            for (item in items) {
                canvas.beginText().setFontAndSize(font, 11f).moveText(item.x, item.y)
                canvas.showText(item.text)
                canvas.endText()
            }
        }
        if (pages.isEmpty()) pdf.addNewPage()
        pdf.close()
        return out.toByteArray()
    }

    private val table =
        listOf(
            Placed("Producto", 72.0, 700.0),
            Placed("Unidades", 250.0, 700.0),
            Placed("Total", 400.0, 700.0),
            Placed("Cuadernos", 72.0, 686.0),
            Placed("120", 250.0, 686.0),
            Placed("\$1.200.000", 400.0, 686.0),
            Placed("Lapices", 72.0, 672.0),
            Placed("45%", 250.0, 672.0),
            Placed("\$450.000", 400.0, 672.0),
        )

    @Test
    fun `una tabla del PDF queda en columnas con numeros reales`() =
        runTest {
            stubResolver(createPdf(listOf(table)))

            val result = useCase(mockk<Uri>(), "salida")

            assertTrue(result is ConversionResult.Success)
            val success = result as ConversionResult.Success
            assertEquals("xlsx", success.outputFile.extension)
            assertEquals(1, success.pageCount)
            XSSFWorkbook(success.outputFile.inputStream()).use { book ->
                val sheet = book.getSheetAt(0)
                assertEquals("Página 1", sheet.sheetName)
                val header = (0..2).map { sheet.getRow(0).getCell(it).stringCellValue }
                assertEquals(listOf("Producto", "Unidades", "Total"), header)
                assertEquals("Cuadernos", sheet.getRow(1).getCell(0).stringCellValue)
                assertEquals(120.0, sheet.getRow(1).getCell(1).numericCellValue)
                assertEquals(1200000.0, sheet.getRow(1).getCell(2).numericCellValue)
                assertEquals(0.45, sheet.getRow(2).getCell(1).numericCellValue)
                assertEquals(450000.0, sheet.getRow(2).getCell(2).numericCellValue)
            }
        }

    @Test
    fun `cada pagina del PDF es una hoja`() =
        runTest {
            stubResolver(createPdf(listOf(table, listOf(Placed("Segunda hoja", 72.0, 700.0)))))

            val result = useCase(mockk<Uri>(), "salida") as ConversionResult.Success

            assertEquals(2, result.pageCount)
            XSSFWorkbook(result.outputFile.inputStream()).use { book ->
                assertEquals(listOf("Página 1", "Página 2"), listOf(book.getSheetName(0), book.getSheetName(1)))
                assertEquals("Segunda hoja", book.getSheetAt(1).getRow(0).getCell(0).stringCellValue)
            }
        }

    @Test
    fun `un PDF sin texto devuelve Error y no deja archivos`() =
        runTest {
            stubResolver(createPdf(emptyList()))

            val result = useCase(mockk<Uri>(), "salida")

            assertTrue(result is ConversionResult.Error)
            assertFalse(File(filesDir, "converted/salida.xlsx").exists())
            assertEquals(0, cacheDir.listFiles()?.size ?: 0)
        }

    @Test
    fun `un archivo no legible devuelve Error`() =
        runTest {
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } returns null
            every { context.contentResolver } returns resolver

            assertTrue(useCase(uri, "salida") is ConversionResult.Error)
        }

    @Test
    fun `un archivo que no es un PDF devuelve Error y limpia el cache`() =
        runTest {
            stubResolver("esto no es un pdf".toByteArray())

            val result = useCase(mockk<Uri>(), "salida")

            assertTrue(result is ConversionResult.Error)
            assertEquals(0, cacheDir.listFiles()?.size ?: 0)
        }
}
