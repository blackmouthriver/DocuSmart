package com.docsmart.features.converter.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Mismos casos que `pdfExcel.test.ts` de la web: el resultado debe ser el mismo en ambas plataformas. */
class PdfSheetLayoutTest {
    private fun t(
        text: String,
        x: Float,
        y: Float,
        bold: Boolean = false,
    ) = TextFragment(text, x, y, text.length * 5.5f, 11f, bold)

    private fun rows(vararg fragments: TextFragment): List<List<SheetCell>> {
        val lines = PdfSheetLayout.buildLines(fragments.toList())
        return PdfSheetLayout.pageRows(lines)
    }

    /** Como la hoja real: el umbral fino de columnas (`buildSheets` de la web usa 0,6 y no el 2 de Word). */
    private fun spreadsheetRows(vararg fragments: TextFragment): List<List<SheetCell>> {
        val lines = PdfSheetLayout.buildLines(fragments.toList(), PdfSheetLayout.SPREADSHEET_SPLIT_GAP)
        return PdfSheetLayout.pageRows(lines)
    }

    private fun values(row: List<SheetCell>): List<Any> = row.map { it.number ?: it.text }

    @Test
    fun `parseNumber reconoce los formatos habituales`() {
        val cases =
            listOf(
                Triple("120", 120.0, NumFormat.INTEGER),
                Triple("1.500.000", 1500000.0, NumFormat.INTEGER),
                Triple("1,500,000", 1500000.0, NumFormat.INTEGER),
                Triple("1.234,56", 1234.56, NumFormat.DECIMAL),
                Triple("1,234.56", 1234.56, NumFormat.DECIMAL),
                Triple("3,5", 3.5, NumFormat.DECIMAL),
                Triple("0.75", 0.75, NumFormat.DECIMAL),
                Triple("\$2.000.000", 2000000.0, NumFormat.CURRENCY),
                Triple("\$ 1.250,50", 1250.5, NumFormat.CURRENCY2),
                Triple("COP 900", 900.0, NumFormat.CURRENCY),
                Triple("45%", 0.45, NumFormat.PERCENT),
                Triple("-12", -12.0, NumFormat.INTEGER),
                Triple("(300)", -300.0, NumFormat.INTEGER),
                Triple("− 4,25", -4.25, NumFormat.DECIMAL),
            )
        for ((raw, value, format) in cases) {
            assertEquals(value to format, NumberParser.parse(raw), raw)
        }
    }

    @Test
    fun `parseNumber rechaza lo que no es un numero`() {
        val rejected = listOf("", "Producto", "12/05/2026", "1.2.3", "300 123 4567x", "abc123", "12.", "1.23.456")
        for (raw in rejected) assertNull(NumberParser.parse(raw), raw)
    }

    @Test
    fun `parseNumber rechaza textos de mas de 30 caracteres`() {
        assertNull(NumberParser.parse("x".repeat(40)))
        assertNull(NumberParser.parse("1".repeat(40)))
    }

    @Test
    fun `arma filas con columnas alineadas, numeros reales y una fila vacia entre bloques`() {
        val result =
            rows(
                t("Reporte de ventas", 72f, 80f, bold = true),
                t("Producto", 72f, 130f, bold = true),
                t("Unidades", 250f, 130f, bold = true),
                t("Total", 400f, 130f, bold = true),
                t("Cuadernos", 72f, 144f),
                t("120", 250f, 144f),
                t("\$1.200.000", 400f, 144f),
                t("Lápices", 72f, 158f),
                t("\$450.000", 400f, 158f),
            )

        assertEquals(listOf(SheetCell(text = "Reporte de ventas", bold = true)), result[0])
        assertEquals(emptyList<SheetCell>(), result[1])
        assertEquals(listOf<Any>("Producto", "Unidades", "Total"), values(result[2]))
        assertTrue(result[2][0].bold)
        assertEquals(
            listOf(
                SheetCell(text = "Cuadernos"),
                SheetCell(number = 120.0, format = NumFormat.INTEGER),
                SheetCell(number = 1200000.0, format = NumFormat.CURRENCY),
            ),
            result[3],
        )
        assertEquals(listOf<Any>("Lápices", "", 450000.0), values(result[4]))
    }

    @Test
    fun `estilo extracto de Word - filas altas sin filas vacias y celdas en dos lineas`() {
        val result =
            spreadsheetRows(
                t("Fecha", 72f, 100f, true),
                t("Descripción", 150f, 100f, true),
                t("Débito", 300f, 100f, true),
                t("01/11/2026", 72f, 125f),
                t("Transferencia", 150f, 125f),
                t("\$ 250.000", 300f, 125f),
                t("recibida", 150f, 138f),
                t("05/11/2026", 72f, 163f),
                t("Servicios públicos del mes", 150f, 163f),
                t("\$ 212.300", 300f, 163f),
            )

        assertEquals(
            listOf(
                listOf<Any>("Fecha", "Descripción", "Débito"),
                listOf<Any>("01/11/2026", "Transferencia recibida", 250000.0),
                listOf<Any>("05/11/2026", "Servicios públicos del mes", 212300.0),
            ),
            result.map { values(it) },
        )
    }

    @Test
    fun `sin columnas cada linea va en la columna A`() {
        val result = rows(t("Hola", 72f, 100f), t("mundo", 72f, 114f))

        assertEquals(listOf(listOf(SheetCell(text = "Hola")), listOf(SheetCell(text = "mundo"))), result)
    }

    @Test
    fun `dos bloques que caen en la misma columna se unen`() {
        val result =
            rows(
                t("A", 72f, 100f),
                t("B", 300f, 100f),
                t("C", 72f, 114f),
                t("D", 300f, 114f),
                t("x", 72f, 128f),
                t("y", 100f, 128f),
                t("z", 300f, 128f),
            )

        assertEquals(listOf<Any>("x y", "z"), values(result[2]))
    }

    @Test
    fun `una pagina sin texto no genera filas`() {
        assertEquals(emptyList<List<SheetCell>>(), rows())
        assertEquals(emptyList<List<SheetCell>>(), rows(t("   ", 72f, 100f)))
    }
}
