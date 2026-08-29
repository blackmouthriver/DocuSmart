package com.docsmart.features.converter.domain.usecase

import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.poifs.filesystem.FileMagic
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.InputStream

/**
 * RF-CONV-07: `XWPFDocument` (OOXML/.docx) y `HWPFDocument` (OLE2/.doc,
 * módulo `poi-scratchpad`) no comparten interfaz común -- a diferencia de
 * `WorkbookFactory` para Excel, que sí detecta y abstrae `.xls`/`.xlsx`
 * automáticamente. Cada caso de uso de Word necesita decidir cuál usar
 * ANTES de leer, o `XWPFDocument` lanza `NotOfficeXmlFileException` al
 * recibir un `.doc` real (firma OLE2 en vez de ZIP).
 */
internal enum class WordFileFormat { OOXML, OLE2, UNKNOWN }

/**
 * Detecta el formato real del archivo mirando su firma binaria
 * (`FileMagic`, parte de `poi-ooxml`/`poi`), no la extensión del nombre
 * -- un archivo puede llamarse ".docx" y ser en realidad un OLE2 renombrado,
 * o viceversa. `FileMagic.valueOf()` necesita un stream con mark/reset
 * soportado (hace `mark(8)` + `reset()` internamente), por eso se envuelve
 * en `BufferedInputStream`; el stream devuelto ya quedó reseteado al
 * inicio, listo para pasarse directo a `XWPFDocument`/`HWPFDocument` sin
 * volver a abrir el `Uri`.
 */
internal fun detectWordFormat(input: InputStream): Pair<WordFileFormat, InputStream> {
    val buffered = BufferedInputStream(input)
    val format = when (FileMagic.valueOf(buffered)) {
        FileMagic.OOXML -> WordFileFormat.OOXML
        FileMagic.OLE2  -> WordFileFormat.OLE2
        else            -> WordFileFormat.UNKNOWN
    }
    return format to buffered
}

// Carácter BEL (código decimal 7 en ASCII): marca de fin de celda/fila de
// tabla en el modelo de caracteres de HWPF -- no es texto real, se quita
// del párrafo extraído. Se construye con Char(7) en vez de escribirlo
// como literal dentro de un string para que quede legible en el código.
private val HWPF_CELL_MARK = Char(7).toString()

// A diferencia de `.docx` (donde `w:styleId` es un identificador interno
// SIEMPRE en inglés, independiente del idioma de la UI de Word), en `.doc`
// (HWPF) `StyleDescription.name` es el nombre VISIBLE del estilo, guardado
// en el idioma con el que se creó el documento -- un "Heading 1" creado con
// Word en español se llama "Título 1". El formato binario sí guarda un
// identificador numérico independiente del idioma (`sti`), pero la API
// pública de POI (`StyleDescription`) no lo expone, así que la única señal
// disponible es el nombre. Se cubren los idiomas que la app ya soporta
// (es/en/de/pt/ru); un `.doc` creado en otro idioma simplemente no tendrá
// sus encabezados detectados y caerá como párrafo normal -- degradado, no
// roto, mismo nivel de fidelidad ya aceptado por RNF-CONV-02.
private val HEADING_STYLE_NAME_PREFIXES = listOf(
    "heading", "título", "titulo", "überschrift", "uberschrift", "заголовок"
)
private val TITLE_STYLE_NAME_EXACT = listOf("title", "título", "titulo", "titel", "название")

internal fun isHeadingStyleName(styleName: String): Boolean {
    val normalized = styleName.trim().lowercase()
    if (normalized.isBlank()) return false
    return HEADING_STYLE_NAME_PREFIXES.any { normalized.startsWith(it) } ||
        normalized in TITLE_STYLE_NAME_EXACT
}

/**
 * Extrae párrafos de un `.doc` legado (formato binario OLE2, Word 97-2003)
 * vía `HWPFDocument`. Cada elemento es (texto, esEncabezado) -- mismo
 * shape que usa `WordToHtmlUseCase` para `.docx`, para poder alimentar el
 * mismo `buildHtml()` sin duplicar lógica de render.
 *
 * Nota de alcance: a diferencia de la ruta `.docx` (que sí separa celdas
 * de tabla con " | " vía `XWPFDocument.tables`), acá no hay una segunda
 * pasada de tablas -- el rango plano de `HWPFDocument` (`Range.getParagraph`)
 * ya incluye el texto de las celdas como párrafos normales en su posición
 * real del documento; agregar una iteración de tablas aparte duplicaría
 * ese contenido. El texto de una tabla en un `.doc` convertido queda
 * legible como líneas sueltas, sin separadores de columna -- mismo nivel
 * de fidelidad ya aceptado para el resto del módulo (RNF-CONV-02: estas
 * conversiones son texto plano, sin reproducir diseño visual).
 */
internal fun extractLegacyDocBlocks(input: InputStream): List<Pair<String, Boolean>> {
    val blocks = mutableListOf<Pair<String, Boolean>>()
    HWPFDocument(input).use { doc ->
        val range = doc.range
        val styles = doc.styleSheet
        for (i in 0 until range.numParagraphs()) {
            val para = range.getParagraph(i)
            val text = para.text().replace(HWPF_CELL_MARK, "").trim()
            if (text.isBlank()) continue
            val styleName = try {
                styles.getStyleDescription(para.styleIndex.toInt())?.name ?: ""
            } catch (e: Exception) {
                Timber.w("extractLegacyDocBlocks: no se pudo leer el estilo del párrafo $i — ${e.message}")
                ""
            }
            blocks.add(text to isHeadingStyleName(styleName))
        }
    }
    return blocks
}
