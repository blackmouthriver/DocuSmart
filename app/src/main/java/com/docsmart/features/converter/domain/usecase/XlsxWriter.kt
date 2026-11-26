package com.docsmart.features.converter.domain.usecase

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class SheetData(
    val name: String,
    val rows: List<List<SheetCell>>,
)

/**
 * Escribe un `.xlsx` minimo a mano (un ZIP con XML), igual que la web: texto como celdas en linea,
 * numeros como numeros con su formato (moneda, porcentaje, miles) y negrita. Sin dependencias de
 * Android; mas liviano que armar el libro con Apache POI.
 */
internal object XlsxWriter {
    private const val MAX_SHEET_NAME = 31
    private const val MIN_COLUMN_WIDTH = 8
    private const val MAX_COLUMN_WIDTH = 60
    private const val COLUMN_PADDING = 2
    private const val LETTERS = 26
    private const val NUMERIC_STYLE_BASE = 2
    private const val BOLD_STYLE_OFFSET = 5
    private const val GROUP_DIGITS = 3
    private const val EXTRA_DECIMALS = 3
    private const val FIRST_CODE_POINT_OF_LETTERS = 65
    private const val MAX_EXACT = 1e15

    private val FORMAT_ORDER =
        listOf(NumFormat.INTEGER, NumFormat.DECIMAL, NumFormat.CURRENCY, NumFormat.CURRENCY2, NumFormat.PERCENT)

    /** Nombre de hoja valido para Excel: sin `[]:*?/\` y de a lo mas 31 caracteres. */
    fun sheetName(raw: String): String = raw.replace(Regex("""[\[\]:*?/\\]"""), " ").take(MAX_SHEET_NAME)

    fun write(
        sheets: List<SheetData>,
        out: OutputStream,
    ) {
        require(sheets.isNotEmpty()) { "un libro necesita al menos una hoja" }
        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml", XlsxPackage.contentTypes(sheets.size))
            zip.entry("_rels/.rels", XlsxPackage.packageRels())
            zip.entry("xl/workbook.xml", XlsxPackage.workbook(sheets))
            zip.entry("xl/_rels/workbook.xml.rels", XlsxPackage.workbookRels(sheets.size))
            zip.entry("xl/styles.xml", XlsxPackage.STYLES)
            sheets.forEachIndexed { i, sheet -> zip.entry("xl/worksheets/sheet${i + 1}.xml", sheetXml(sheet)) }
        }
    }

    private fun ZipOutputStream.entry(
        name: String,
        content: String,
    ) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    fun columnName(index: Int): String =
        if (index < LETTERS) {
            (FIRST_CODE_POINT_OF_LETTERS + index).toChar().toString()
        } else {
            columnName(index / LETTERS - 1) + (FIRST_CODE_POINT_OF_LETTERS + index % LETTERS).toChar()
        }

    private fun styleOf(cell: SheetCell): Int {
        val format = cell.format
        return when {
            format != null -> {
                val bold = if (cell.bold) BOLD_STYLE_OFFSET else 0
                NUMERIC_STYLE_BASE + FORMAT_ORDER.indexOf(format) + bold
            }
            cell.bold -> 1
            else -> 0
        }
    }

    /** Caracteres que ocupa la celda ya con formato ("$1.234.567,50"), para que no salga "#####". */
    private fun displayWidth(cell: SheetCell): Int {
        val number = cell.number ?: return cell.text.length
        val shown = if (cell.format == NumFormat.PERCENT) number * 100 else number
        val digits = kotlin.math.abs(shown).toLong().toString().length
        val separators = (digits - 1) / GROUP_DIGITS
        val format = cell.format
        val extra =
            (if (format == NumFormat.CURRENCY || format == NumFormat.CURRENCY2) 1 else 0) +
                (if (format == NumFormat.CURRENCY2 || format == NumFormat.DECIMAL) EXTRA_DECIMALS else 0) +
                (if (format == NumFormat.PERCENT) 1 else 0) +
                (if (number < 0) 1 else 0)
        return digits + separators + extra + if (cell.bold) 1 else 0
    }

    private fun numberText(value: Double): String =
        if (value == value.toLong().toDouble() && kotlin.math.abs(value) < MAX_EXACT) {
            value.toLong().toString()
        } else {
            value.toString()
        }

    private fun cellXml(
        cell: SheetCell,
        ref: String,
    ): String {
        val style = styleOf(cell).let { if (it != 0) """ s="$it"""" else "" }
        val number = cell.number
        return when {
            number != null -> """<c r="$ref"$style><v>${numberText(number)}</v></c>"""
            cell.text.isEmpty() -> ""
            else -> {
                val text = XlsxPackage.xmlText(cell.text)
                """<c r="$ref"$style t="inlineStr"><is><t xml:space="preserve">$text</t></is></c>"""
            }
        }
    }

    private fun columnXml(
        number: Int,
        width: Int,
    ): String = """<col min="$number" max="$number" width="$width" customWidth="1"/>"""

    private fun sheetXml(sheet: SheetData): String {
        val widths = mutableListOf<Int>()
        val rows =
            sheet.rows.mapIndexed { r, row ->
                val cells =
                    row.mapIndexed { i, cell ->
                        while (widths.size <= i) widths.add(MIN_COLUMN_WIDTH)
                        widths[i] = maxOf(widths[i], minOf(MAX_COLUMN_WIDTH, displayWidth(cell) + COLUMN_PADDING))
                        cellXml(cell, "${columnName(i)}${r + 1}")
                    }
                """<row r="${r + 1}">${cells.joinToString("")}</row>"""
            }
        val cols =
            if (widths.isEmpty()) {
                ""
            } else {
                "<cols>" +
                    widths.mapIndexed { i, w -> columnXml(i + 1, w) }.joinToString("") +
                    "</cols>"
            }
        val body = rows.joinToString("")
        val namespace = XlsxPackage.NS_MAIN
        return XlsxPackage.XML_HEAD + """<worksheet xmlns="$namespace">$cols<sheetData>$body</sheetData></worksheet>"""
    }
}
