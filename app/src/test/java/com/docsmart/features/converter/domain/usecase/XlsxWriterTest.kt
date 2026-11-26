package com.docsmart.features.converter.domain.usecase

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** El `.xlsx` escrito a mano se abre con Apache POI (un lector independiente) y conserva valores y estilos. */
class XlsxWriterTest {
    private fun roundTrip(sheets: List<SheetData>): XSSFWorkbook {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sheets, out)
        return XSSFWorkbook(ByteArrayInputStream(out.toByteArray()))
    }

    private val sample =
        listOf(
            SheetData(
                XlsxWriter.sheetName("Ventas [2026]"),
                listOf(
                    listOf(SheetCell(text = "Producto & total <ok>", bold = true)),
                    emptyList(),
                    listOf(
                        SheetCell(text = "Cuadernos"),
                        SheetCell(number = 1200000.0, format = NumFormat.CURRENCY, bold = true),
                        SheetCell(number = 0.45, format = NumFormat.PERCENT),
                        SheetCell(number = 1234.5, format = NumFormat.DECIMAL),
                        SheetCell(number = 42.0, format = NumFormat.INTEGER),
                        SheetCell(number = 9.99, format = NumFormat.CURRENCY2),
                    ),
                    List(28) { SheetCell(number = it.toDouble()) },
                ),
            ),
            SheetData("Otra", emptyList()),
        )

    @Test
    fun `el libro se abre y conserva los nombres de hoja`() {
        roundTrip(sample).use { book ->
            assertEquals(2, book.numberOfSheets)
            assertEquals("Ventas  2026 ", book.getSheetName(0))
            assertEquals("Otra", book.getSheetName(1))
        }
    }

    @Test
    fun `el texto se escapa y los numeros son numeros con su formato`() {
        roundTrip(sample).use { book ->
            val sheet = book.getSheetAt(0)
            assertEquals("Producto & total <ok>", sheet.getRow(0).getCell(0).stringCellValue)
            assertTrue(sheet.getRow(0).getCell(0).cellStyle.font.bold)

            val row = sheet.getRow(2)
            assertEquals("Cuadernos", row.getCell(0).stringCellValue)
            val currency = row.getCell(1)
            assertEquals(CellType.NUMERIC, currency.cellType)
            assertEquals(1200000.0, currency.numericCellValue)
            assertTrue(currency.cellStyle.dataFormatString.contains("#,##0"))
            assertTrue(currency.cellStyle.font.bold)
            assertEquals(0.45, row.getCell(2).numericCellValue)
            assertEquals("0%", row.getCell(2).cellStyle.dataFormatString)
            assertEquals(1234.5, row.getCell(3).numericCellValue)
            assertEquals(42.0, row.getCell(4).numericCellValue)
            assertEquals(9.99, row.getCell(5).numericCellValue)
            assertFalse(row.getCell(4).cellStyle.font.bold)
        }
    }

    @Test
    fun `las filas vacias se conservan y las columnas pasan de la Z`() {
        roundTrip(sample).use { book ->
            val sheet = book.getSheetAt(0)
            assertEquals(0, sheet.getRow(1)?.physicalNumberOfCells ?: 0)
            // 28 columnas: la ultima es AB (indice 27)
            val last = sheet.getRow(3).getCell(27)
            assertNotNull(last)
            assertEquals(27.0, last.numericCellValue)
            assertEquals("AB4", last.address.formatAsString())
        }
    }

    @Test
    fun `el ancho de columna cuenta el formato`() {
        val sheets = listOf(SheetData("A", listOf(listOf(SheetCell(number = 385420.5, format = NumFormat.CURRENCY2)))))
        roundTrip(sheets).use { book ->
            // \$385.420,50 = 11 caracteres + 2 de margen; POI lo devuelve en 1/256 de caracter
            assertEquals(13, book.getSheetAt(0).getColumnWidth(0) / 256)
        }
    }

    @Test
    fun `los nombres de columna siguen el orden de Excel`() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AB", XlsxWriter.columnName(27))
        assertEquals("BA", XlsxWriter.columnName(52))
    }

    @Test
    fun `un nombre de hoja largo o con caracteres prohibidos se limpia`() {
        assertEquals("a b c d e f ", XlsxWriter.sheetName("a/b:c*d?e[f]"))
        assertEquals(31, XlsxWriter.sheetName("x".repeat(80)).length)
    }

    @Test
    fun `sin hojas es un error`() {
        assertThrows(IllegalArgumentException::class.java) { XlsxWriter.write(emptyList(), ByteArrayOutputStream()) }
    }
}
