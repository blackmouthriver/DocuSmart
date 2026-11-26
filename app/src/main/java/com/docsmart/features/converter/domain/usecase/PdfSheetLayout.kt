package com.docsmart.features.converter.domain.usecase

import kotlin.math.abs

/** Formato numerico de una celda de Excel. */
internal enum class NumFormat { INTEGER, DECIMAL, CURRENCY, CURRENCY2, PERCENT }

/** Celda: texto o numero (con su formato), opcionalmente en negrita. */
internal data class SheetCell(
    val text: String = "",
    val number: Double? = null,
    val format: NumFormat? = null,
    val bold: Boolean = false,
) {
    val isEmpty: Boolean get() = number == null && text.isEmpty()

    companion object {
        /** Celda de un texto del PDF: si parece un numero ("1.200", "$ 5", "45%") se guarda como numero. */
        fun fromText(
            text: String,
            bold: Boolean,
        ): SheetCell {
            val parsed = NumberParser.parse(text)
            return if (parsed == null) {
                SheetCell(text = text, bold = bold)
            } else {
                SheetCell(number = parsed.first, format = parsed.second, bold = bold)
            }
        }
    }
}

/** Fragmento de texto de una pagina; [y] es la linea base medida hacia abajo desde el borde superior. */
internal data class TextFragment(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val size: Float,
    val bold: Boolean,
)

internal class PdfSegment(
    var x0: Float,
    var x1: Float,
    val text: StringBuilder = StringBuilder(),
    var bold: Boolean = true,
)

internal class PdfLine(
    val y: Float,
    val size: Float,
    val segments: List<PdfSegment>,
)

/**
 * PDF a Excel: un PDF guarda letras sueltas con su posicion, no filas ni columnas. Aqui cada linea
 * del PDF es una fila y las columnas salen de la alineacion del texto; los numeros se guardan como
 * numeros de Excel (con formato) para poder sumarlos. Es el mismo algoritmo que la web
 * (`pdfExcel.ts`), sin dependencias de Android ni de iText para poder probarlo en JVM.
 */
internal object PdfSheetLayout {
    private const val SAME_LINE_FACTOR = 0.45f
    private const val WORD_GAP_FACTOR = 0.15f
    private const val DEFAULT_SPLIT_GAP = 2f

    /** En una hoja de calculo una columna de mas no molesta: umbral fino entre columnas. */
    const val SPREADSHEET_SPLIT_GAP = 0.6f
    private const val ANCHOR_TOLERANCE = 1.5f
    private const val WRAPPED_LINE_FACTOR = 1.6f
    private const val BLANK_ROW_FACTOR = 1.8f

    /** Agrupa los fragmentos en lineas y cada linea en segmentos separados por huecos de [splitGap] tamanos. */
    fun buildLines(
        fragments: List<TextFragment>,
        splitGap: Float = DEFAULT_SPLIT_GAP,
    ): List<PdfLine> {
        // Los fragmentos de solo espacios no cuentan para la geometria: ocultarian el hueco entre columnas.
        val sorted = fragments.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.y }, { it.x }))
        val groups = mutableListOf<MutableList<TextFragment>>()
        for (fragment in sorted) {
            val group = groups.lastOrNull()
            if (group != null && sameLine(fragment, group[0])) {
                group.add(fragment)
            } else {
                groups.add(mutableListOf(fragment))
            }
        }
        return groups.mapNotNull { lineOf(it, splitGap) }
    }

    private fun lineOf(
        group: MutableList<TextFragment>,
        splitGap: Float,
    ): PdfLine? {
        group.sortBy { it.x }
        val size = group.maxOf { it.size }
        val segments = mutableListOf<PdfSegment>()
        var segment: PdfSegment? = null
        var previous: TextFragment? = null
        for (fragment in group) {
            val gap = previous?.let { fragment.x - (it.x + it.width) } ?: 0f
            var current = segment
            if (current == null || gap > splitGap * size) {
                current = PdfSegment(fragment.x, fragment.x + fragment.width)
                segments.add(current)
            } else if (needsSpace(gap, current, fragment)) {
                current.text.append(' ')
            }
            current.text.append(fragment.text)
            current.bold = current.bold && fragment.bold
            current.x1 = maxOf(current.x1, fragment.x + fragment.width)
            segment = current
            previous = fragment
        }
        val clean = segments.filter { it.text.isNotBlank() }
        clean.forEach { trim(it) }
        return if (clean.isEmpty()) null else PdfLine(group[0].y, size, clean)
    }

    /** Misma linea si la base esta cerca (tolera subindices y letras de otro tamano). */
    private fun sameLine(
        fragment: TextFragment,
        first: TextFragment,
    ): Boolean = abs(fragment.y - first.y) <= SAME_LINE_FACTOR * maxOf(fragment.size, first.size)

    /** Un hueco de palabra sin espacio real a ninguno de los lados pide un espacio. */
    private fun needsSpace(
        gap: Float,
        segment: PdfSegment,
        fragment: TextFragment,
    ): Boolean {
        val endsWithSpace = segment.text.isNotEmpty() && segment.text.last().isWhitespace()
        return gap > WORD_GAP_FACTOR * fragment.size && !endsWithSpace && !fragment.text.first().isWhitespace()
    }

    private fun trim(segment: PdfSegment) {
        val trimmed = segment.text.toString().trim()
        segment.text.setLength(0)
        segment.text.append(trimmed)
    }

    /** Inicios de segmento que caen cerca unos de otros (al menos dos por columna), tolerancia en puntos. */
    fun columnAnchors(
        lines: List<PdfLine>,
        tolerance: Float,
    ): List<Float> {
        val xs = lines.flatMap { line -> line.segments.map { it.x0 } }.sorted()
        val clusters = mutableListOf<MutableList<Float>>()
        for (x in xs) {
            val cluster = clusters.lastOrNull()
            if (cluster != null && x - cluster.last() <= tolerance) cluster.add(x) else clusters.add(mutableListOf(x))
        }
        return clusters.filter { it.size >= 2 }.map { it.min() }
    }

    /** Filas de una pagina: columnas segun la alineacion; lineas de un solo bloque, en la columna A. */
    fun pageRows(lines: List<PdfLine>): List<List<SheetCell>> {
        val multi = lines.filter { it.segments.size >= 2 }
        val size = lines.firstOrNull()?.size ?: 10f
        val anchors = if (multi.size >= 2) columnAnchors(multi, ANCHOR_TOLERANCE * size) else emptyList()
        val rows = mutableListOf<MutableList<SheetCell>>()
        // Espaciado tipico entre lineas (las tablas de Word tienen filas mas altas que el texto).
        val gaps = lines.drop(1).mapIndexed { k, line -> line.y - lines[k].y }.sorted()
        val typical = if (gaps.isEmpty()) size else gaps[gaps.size / 2]
        lines.forEachIndexed { index, line ->
            val previous = lines.getOrNull(index - 1)
            val gap = if (previous != null) line.y - previous.y else 0f
            if (previous != null && continuesCell(previous, line, anchors, gap, size, rows)) return@forEachIndexed
            // Un espacio claramente mayor que el habitual deja una fila vacia, como en el documento.
            val tallest = maxOf(line.size, previous?.size ?: 0f)
            val blankLimit = maxOf(BLANK_ROW_FACTOR * typical, WRAPPED_LINE_FACTOR * tallest)
            if (previous != null && gap > blankLimit) rows.add(mutableListOf())
            rows.add(rowOf(line, anchors, size))
        }
        return rows
    }

    private fun columnOf(
        x: Float,
        anchors: List<Float>,
        size: Float,
    ): Int {
        var column = 0
        anchors.forEachIndexed { i, anchor -> if (x >= anchor - ANCHOR_TOLERANCE * size) column = i }
        return column
    }

    /** Texto que sigue en la linea de abajo dentro de una celda ("Transferencia / recibida"): se une a esa celda. */
    private fun continuesCell(
        previous: PdfLine,
        line: PdfLine,
        anchors: List<Float>,
        gap: Float,
        size: Float,
        rows: MutableList<MutableList<SheetCell>>,
    ): Boolean {
        val wasRow = previous.segments.size >= 2 && anchors.size >= 2
        val eligible = wasRow && line.segments.size == 1 && gap <= WRAPPED_LINE_FACTOR * line.size
        val column = if (eligible) columnOf(line.segments[0].x0, anchors, size) else 0
        val cell = if (column > 0) rows.lastOrNull()?.getOrNull(column) else null
        val joinable = cell != null && cell.number == null && cell.text.isNotEmpty()
        if (cell != null && joinable) {
            rows[rows.size - 1][column] = cell.copy(text = cell.text + " " + line.segments[0].text)
        }
        return joinable
    }

    private fun rowOf(
        line: PdfLine,
        anchors: List<Float>,
        size: Float,
    ): MutableList<SheetCell> {
        if (line.segments.size < 2 || anchors.size < 2) {
            val text = line.segments.joinToString(" ") { it.text }
            return mutableListOf(SheetCell.fromText(text, line.segments.all { it.bold }))
        }
        val row = mutableListOf<SheetCell>()
        for (segment in line.segments) {
            val column = columnOf(segment.x0, anchors, size)
            while (row.size <= column) row.add(SheetCell())
            val existing = row[column]
            val text = segment.text.toString()
            // Dos bloques en la misma columna: se unen en la celda.
            row[column] =
                if (existing.isEmpty) {
                    SheetCell.fromText(text, segment.bold)
                } else {
                    val joined = "${existing.number?.toString() ?: existing.text} $text"
                    existing.copy(text = joined, number = null, format = null)
                }
        }
        return row
    }
}
