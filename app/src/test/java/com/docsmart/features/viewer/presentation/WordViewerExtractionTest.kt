package com.docsmart.features.viewer.presentation

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Reemplaza WordRunParsingTest.kt (RF pedido por el usuario 2026-09-03): el
 * visor de Word pasó de expresiones regulares sobre XML crudo a Apache POI
 * (XWPFDocument), ya probado en producción por WordToPdfUseCase/
 * WordToTextUseCase -- mismo patrón de fixture (.docx real construido en
 * memoria con la propia API de escritura de POI, no bytes sintéticos).
 * isHeadingStyleName()/detectWordFormat()/extractLegacyDocBlocks() ya están
 * cubiertas por WordFormatDetectionTest.kt (converter); estos tests cubren
 * solo el mapeo nuevo hacia WordBlock/WordParagraph/WordRun.
 */
class WordViewerExtractionTest {

    @Test
    fun `un run sin formato no tiene negrita, cursiva ni tamano explicito`() {
        val docx = buildDocx { doc -> doc.createParagraph().createRun().setText("Texto normal") }

        val blocks = extractWordBlocks(docx)

        val runs = (blocks.single() as WordParagraphBlock).paragraph.runs
        assertEquals(1, runs.size)
        assertEquals("Texto normal", runs[0].text)
        assertFalse(runs[0].bold)
        assertFalse(runs[0].italic)
        assertNull(runs[0].fontSizeSp)
    }

    @Test
    fun `negrita y cursiva se preservan por run`() {
        val docx = buildDocx { doc ->
            val para = doc.createParagraph()
            para.createRun().apply { setText("Normal "); isBold = false }
            para.createRun().apply { setText("Negrita"); isBold = true }
            para.createRun().apply { setText(" cursiva"); isItalic = true }
        }

        val runs = (extractWordBlocks(docx).single() as WordParagraphBlock).paragraph.runs

        assertEquals(3, runs.size)
        assertFalse(runs[0].bold)
        assertTrue(runs[1].bold)
        assertEquals("Negrita", runs[1].text)
        assertTrue(runs[2].italic)
    }

    @Test
    fun `runs con texto en blanco se descartan`() {
        val docx = buildDocx { doc ->
            val para = doc.createParagraph()
            para.createRun().setText("")
            para.createRun().setText("  ")
            para.createRun().setText("Real")
        }

        val runs = (extractWordBlocks(docx).single() as WordParagraphBlock).paragraph.runs

        assertEquals(1, runs.size)
        assertEquals("Real", runs[0].text)
    }

    @Test
    fun `un parrafo con estilo Heading1 se reconoce como encabezado`() {
        val docx = buildDocx { doc ->
            doc.createParagraph().apply {
                style = "Heading1"
                createRun().setText("Titulo")
            }
        }

        val block = extractWordBlocks(docx).single() as WordParagraphBlock

        assertTrue(block.paragraph.isHeading)
    }

    @Test
    fun `un parrafo sin estilo de encabezado no se marca como tal`() {
        val docx = buildDocx { doc -> doc.createParagraph().createRun().setText("Texto normal") }

        val block = extractWordBlocks(docx).single() as WordParagraphBlock

        assertFalse(block.paragraph.isHeading)
    }

    @Test
    fun `una tabla real se extrae como grilla, no como texto plano`() {
        val docx = buildDocx { doc ->
            val table = doc.createTable(2, 2)
            table.getRow(0).getCell(0).text = "A1"
            table.getRow(0).getCell(1).text = "B1"
            table.getRow(1).getCell(0).text = "A2"
            table.getRow(1).getCell(1).text = "B2"
        }

        val block = extractWordBlocks(docx).single() as WordTableBlock

        assertEquals(listOf(listOf("A1", "B1"), listOf("A2", "B2")), block.rows)
    }

    @Test
    fun `parrafos y tablas se preservan en el orden real del documento`() {
        val docx = buildDocx { doc ->
            doc.createParagraph().createRun().setText("Antes de la tabla")
            doc.createTable(1, 1).getRow(0).getCell(0).text = "Celda"
            doc.createParagraph().createRun().setText("Después de la tabla")
        }

        val blocks = extractWordBlocks(docx)

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is WordParagraphBlock)
        assertTrue(blocks[1] is WordTableBlock)
        assertTrue(blocks[2] is WordParagraphBlock)
    }

    // RF-CONV-07 (WordFormatDetectionTest.kt): un .doc legado (OLE2) real,
    // no un array de bytes sintético -- mismo fixture ya usado por
    // WordToPdfUseCaseTest/WordToTextUseCaseTest.
    @Test
    fun `un doc legado OLE2 se mapea a parrafos de un solo run`() {
        val blocks = extractWordBlocks(legacyDocStream())

        assertTrue(blocks.isNotEmpty())
        assertTrue(blocks.all { it is WordParagraphBlock })
        val allText = blocks.joinToString(" ") { block ->
            (block as WordParagraphBlock).paragraph.runs.joinToString("") { it.text }
        }
        assertTrue(allText.contains("Titulo de prueba") || allText.contains("Celda A1"))
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun buildDocx(build: (XWPFDocument) -> Unit): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        XWPFDocument().use { doc ->
            build(doc)
            doc.write(out)
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    private fun legacyDocStream(): ByteArrayInputStream {
        val bytes = checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/legacy-sample.doc")) {
            "No se encontró fixtures/legacy-sample.doc en recursos de test"
        }.use { it.readBytes() }
        return ByteArrayInputStream(bytes)
    }
}
