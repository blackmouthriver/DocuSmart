package com.docsmart.features.converter.domain.usecase

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * `legacy-sample.doc` (src/test/resources/fixtures) es un .doc real generado
 * con Microsoft Word vía automatización COM (no un byte array sintético) --
 * `HWPFDocument` no ofrece una API para crear un documento en blanco desde
 * cero como sí hace `XWPFDocument`, así que un fixture real es la única
 * forma de probar `extractLegacyDocBlocks()` contra el formato binario OLE2
 * real en vez de solo su firma. Contiene: un párrafo "Título 1", dos
 * párrafos normales y una tabla 2x2.
 */
class WordFormatDetectionTest {

    @Test
    fun `detecta OOXML en un docx real`() {
        val (format, _) = detectWordFormat(ByteArrayInputStream(createTestDocx()))
        assertEquals(WordFileFormat.OOXML, format)
    }

    @Test
    fun `detecta OLE2 en un doc legado real`() {
        val (format, _) = detectWordFormat(legacyDocBytes().inputStream())
        assertEquals(WordFileFormat.OLE2, format)
    }

    @Test
    fun `detecta UNKNOWN para bytes que no son ni OOXML ni OLE2`() {
        val (format, _) = detectWordFormat(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))
        assertEquals(WordFileFormat.UNKNOWN, format)
    }

    @Test
    fun `extrae parrafos y detecta el encabezado del doc legado`() {
        val (_, input) = detectWordFormat(legacyDocBytes().inputStream())

        val blocks = extractLegacyDocBlocks(input)

        assertEquals(1, blocks.count { it.second })
        assertEquals("Titulo de prueba", blocks.first { it.second }.first)
        assertTrue(blocks.any { it.first == "Primer parrafo del documento legado." && !it.second })
        assertTrue(blocks.any { it.first == "Segundo parrafo con mas texto de prueba." && !it.second })
    }

    @Test
    fun `extrae el texto de las celdas de tabla del doc legado sin duplicarlo`() {
        val (_, input) = detectWordFormat(legacyDocBytes().inputStream())

        val blocks = extractLegacyDocBlocks(input)
        val allText = blocks.joinToString(" ") { it.first }

        assertTrue(allText.contains("Celda A1"))
        assertTrue(allText.contains("Celda B2"))
        assertEquals(1, blocks.count { it.first.contains("Celda A1") })
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun legacyDocBytes(): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/legacy-sample.doc")) {
            "No se encontró fixtures/legacy-sample.doc en recursos de test"
        }.use { it.readBytes() }

    private fun createTestDocx(): ByteArray {
        val out = ByteArrayOutputStream()
        XWPFDocument().use { doc ->
            doc.createParagraph().createRun().setText("Documento OOXML de prueba")
            doc.write(out)
        }
        return out.toByteArray()
    }
}
