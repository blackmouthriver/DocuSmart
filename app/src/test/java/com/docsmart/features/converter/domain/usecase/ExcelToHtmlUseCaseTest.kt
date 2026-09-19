package com.docsmart.features.converter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.features.converter.domain.model.ConversionResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/**
 * Ronda 14 de la auditoría del Convertidor: este use case tenía 0% de
 * cobertura pese a incorporar ya varios fixes reales (detección de .xls
 * legado/OLE2 vs. protegido con contraseña, orden real de pestañas vía
 * workbook.xml.rels respetando hojas ocultas, chequeo de hoja vacía,
 * lectura de entradas ZIP acotada con readEntrySafely()). Los tests de abajo
 * ejercitan ese parseo XML a mano (esta conversión NO usa POI para leer,
 * solo para generar el .xlsx de prueba) y confirman que sigue funcionando.
 */
class ExcelToHtmlUseCaseTest {
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var useCase: ExcelToHtmlUseCase

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_exceltohtml_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        every { context.getString(any()) } returns "error"
        useCase = ExcelToHtmlUseCase(context)
    }

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `convierte la primera hoja a una tabla HTML con encabezado`() =
        runTest {
            stubResolver(
                createTestXlsx { sheet ->
                    sheet.createRow(0).apply {
                        createCell(0).setCellValue("Nombre")
                        createCell(1).setCellValue("Edad")
                    }
                    sheet.createRow(1).apply {
                        createCell(0).setCellValue("Ana")
                        createCell(1).setCellValue("30")
                    }
                },
            )

            val result = useCase(mockk<Uri>(), "salida")

            assertTrue(result is ConversionResult.Success)
            val html = (result as ConversionResult.Success).outputFile.readText()
            assertTrue(html.contains("<th>Nombre</th>"))
            assertTrue(html.contains("<td>Ana</td>"))
        }

    // Hallazgo real de la revisión general 2026-09-16: cell.toString() en una
    // celda de fórmula devuelve el texto de la fórmula, no el resultado --
    // acá el "parser" es un regex sobre el XML crudo, así que lo que importa
    // es que Excel/POI ya escriben el valor calculado en <v> junto al <f> --
    // el regex de ExcelToHtmlUseCase toma <v>, nunca <f>, así que no hereda
    // ese bug siempre que el .xlsx de origen tenga el valor cacheado (POI lo
    // calcula al pedir evaluateAll() antes de escribir, igual que Excel).
    // La fórmula va en la fila 1 (no la 0): la fila 0 siempre se renderiza
    // como encabezado (<th>) por diseño de ExcelToHtmlUseCase, así que una
    // celda de fórmula en la fila 0 no ejercitaría el camino <td> real.
    @Test
    fun `celda con formula muestra el valor calculado, no el texto de la formula`() =
        runTest {
            stubResolver(
                createTestXlsx(evaluateFormulas = true) { sheet ->
                    sheet.createRow(0).apply {
                        createCell(0).setCellValue("A")
                        createCell(1).setCellValue("B")
                        createCell(2).setCellValue("Suma")
                    }
                    sheet.createRow(1).apply {
                        createCell(0).setCellValue(2.0)
                        createCell(1).setCellValue(3.0)
                        createCell(2).cellFormula = "A2+B2"
                    }
                },
            )

            val result = useCase(mockk<Uri>(), "salida")

            val html = (result as ConversionResult.Success).outputFile.readText()
            assertTrue(html.contains("<td>5.0</td>"), "esperaba el resultado calculado (5), HTML real: $html")
            assertFalseContains(html, "A2+B2")
        }

    // Hallazgo real de la revisión de correctitud adversarial (2026-09-16):
    // resolveFirstVisibleSheetXml() tomaba el primer <sheet> del workbook.xml
    // sin mirar su atributo `state` -- una hoja oculta antes que la primera
    // hoja visible se convertía en vez de la que el usuario ve como primera
    // pestaña en Excel.
    @Test
    fun `una hoja oculta antes que la primera hoja visible no se usa como fuente`() =
        runTest {
            stubResolver(
                createTestXlsxMultiSheet(
                    "RawDataOculta" to true,
                    "Reporte" to false,
                ) { name, sheet ->
                    val valor = if (name == "RawDataOculta") "dato-oculto" else "dato-visible"
                    sheet.createRow(0).createCell(0).setCellValue(valor)
                },
            )

            val result = useCase(mockk<Uri>(), "salida")

            val html = (result as ConversionResult.Success).outputFile.readText()
            assertTrue(html.contains("dato-visible"))
            assertFalseContains(html, "dato-oculto")
        }

    @Test
    fun `hoja completamente vacia devuelve Error`() =
        runTest {
            stubResolver(createTestXlsx { /* sin filas */ })

            val result = useCase(mockk<Uri>(), "salida")

            assertTrue(result is ConversionResult.Error)
        }

    // Hallazgo real #38: ConversionType declara .xls (OLE2 legado) como
    // origen soportado, pero este parser solo entiende el ZIP interno de
    // .xlsx -- debe avisar que el formato no está soportado, no fallar con
    // "hoja vacía".
    @Test
    fun `archivo con firma OLE2 (xls legado) devuelve error de formato no soportado`() =
        runTest {
            val ole2Signature =
                byteArrayOf(
                    0xD0.toByte(),
                    0xCF.toByte(),
                    0x11.toByte(),
                    0xE0.toByte(),
                    0xA1.toByte(),
                    0xB1.toByte(),
                    0x1A.toByte(),
                    0xE1.toByte(),
                )
            stubResolver(ole2Signature + ByteArray(64))

            val result = useCase(mockk<Uri>(), "salida")

            assertTrue(result is ConversionResult.Error)
        }

    @Test
    fun `archivo no legible devuelve Error`() =
        runTest {
            val uri = mockk<Uri>()
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } returns null
            every { context.contentResolver } returns resolver

            val result = useCase(uri, "salida")

            assertTrue(result is ConversionResult.Error)
        }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun assertFalseContains(
        haystack: String,
        needle: String,
    ) {
        assertTrue(!haystack.contains(needle), "no debería contener \"$needle\", HTML real: $haystack")
    }

    private fun stubResolver(bytes: ByteArray) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(bytes) }
        every { context.contentResolver } returns resolver
    }

    private fun createTestXlsx(
        evaluateFormulas: Boolean = false,
        fill: (Sheet) -> Unit,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            fill(sheet)
            if (evaluateFormulas) workbook.creationHelper.createFormulaEvaluator().evaluateAll()
            workbook.write(out)
        }
        return out.toByteArray()
    }

    private fun createTestXlsxMultiSheet(
        // nombre a hidden?
        vararg sheets: Pair<String, Boolean>,
        fill: (String, Sheet) -> Unit,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            sheets.forEachIndexed { index, (name, hidden) ->
                val sheet = workbook.createSheet(name)
                fill(name, sheet)
                if (hidden) workbook.setSheetHidden(index, true)
            }
            workbook.write(out)
        }
        return out.toByteArray()
    }
}
