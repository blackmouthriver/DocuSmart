package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
 * Cubre el bug real encontrado en Conversión (docs/requirements/conversion.md):
 * "Excel → CSV" estaba enrutado a `ExcelToPdfUseCase` — seleccionar esa
 * opción entregaba un PDF, no un .csv. Este use case reemplaza ese hueco.
 */
class ExcelToCsvUseCaseTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: ExcelToCsvUseCase

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_exceltocsv_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        useCase = ExcelToCsvUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `convierte filas y columnas a CSV separado por comas`() = runTest {
        stubResolver(createTestXlsx(listOf(listOf("Nombre", "Edad"), listOf("Ana", "30"))))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Success)
        val outputFile = (result as ConversionResult.Success).outputFile
        assertEquals("csv", outputFile.extension)
        val lines = outputFile.readLines()
        assertEquals("Nombre,Edad", lines[0])
        assertEquals("Ana,30", lines[1])
    }

    @Test
    fun `escapa valores con comas y comillas segun el estandar CSV`() = runTest {
        stubResolver(createTestXlsx(listOf(listOf("Empresa, S.A.", "dijo \"hola\""))))

        val result = useCase(mockk<Uri>(), "salida")

        val outputFile = (result as ConversionResult.Success).outputFile
        assertEquals("\"Empresa, S.A.\",\"dijo \"\"hola\"\"\"", outputFile.readLines()[0])
    }

    @Test
    fun `hoja vacia devuelve Error`() = runTest {
        stubResolver(createTestXlsx(emptyList()))

        val result = useCase(mockk<Uri>(), "salida")

        assertTrue(result is ConversionResult.Error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createTestXlsx(rows: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            rows.forEachIndexed { rowIndex, cells ->
                val row = sheet.createRow(rowIndex)
                cells.forEachIndexed { cellIndex, value ->
                    row.createCell(cellIndex).setCellValue(value)
                }
            }
            workbook.write(out)
        }
        return out.toByteArray()
    }
}
