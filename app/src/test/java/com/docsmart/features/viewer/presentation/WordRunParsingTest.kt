package com.docsmart.features.viewer.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Antes WordViewerContent descartaba <w:rPr> por completo al extraer texto
 * de un párrafo -- un .docx con negrita/cursiva/tamaño reales se mostraba
 * como texto plano en el Visor, aunque PDF sí se renderiza visualmente.
 * `parseWordRuns()` reconstruye el estilo real por fragmento (<w:r>), igual
 * que ya hace `PdfToWordUseCase` al reconstruir un .docx desde un PDF
 * (RF-CONV-09) -- estos tests cubren esa reconstrucción, no la extracción
 * de texto plano (ya existente).
 */
class WordRunParsingTest {

    @Test
    fun `un run sin rPr no tiene negrita, cursiva ni tamano explicito`() {
        val paraXml = "<w:p><w:r><w:t>Texto normal</w:t></w:r></w:p>"

        val runs = parseWordRuns(paraXml)

        assertEquals(1, runs.size)
        assertEquals("Texto normal", runs[0].text)
        assertFalse(runs[0].bold)
        assertFalse(runs[0].italic)
        assertNull(runs[0].fontSizeSp)
    }

    @Test
    fun `w-b marca negrita`() {
        val paraXml = "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Negrita</w:t></w:r></w:p>"

        val runs = parseWordRuns(paraXml)

        assertTrue(runs[0].bold)
    }

    @Test
    fun `w-b con val 0 significa negrita apagada explicitamente`() {
        val paraXml = """<w:p><w:r><w:rPr><w:b w:val="0"/></w:rPr><w:t>No negrita</w:t></w:r></w:p>"""

        val runs = parseWordRuns(paraXml)

        assertFalse(runs[0].bold)
    }

    @Test
    fun `w-i marca cursiva`() {
        val paraXml = "<w:p><w:r><w:rPr><w:i/></w:rPr><w:t>Cursiva</w:t></w:r></w:p>"

        val runs = parseWordRuns(paraXml)

        assertTrue(runs[0].italic)
    }

    @Test
    fun `w-sz convierte de medios puntos a puntos enteros`() {
        val paraXml = """<w:p><w:r><w:rPr><w:sz w:val="28"/></w:rPr><w:t>Grande</w:t></w:r></w:p>"""

        val runs = parseWordRuns(paraXml)

        assertEquals(14, runs[0].fontSizeSp)
    }

    @Test
    fun `una palabra en negrita en medio de una oracion produce runs separados con estilo propio`() {
        val paraXml = """<w:p>
            <w:r><w:t xml:space="preserve">Hola </w:t></w:r>
            <w:r><w:rPr><w:b/></w:rPr><w:t>mundo</w:t></w:r>
            <w:r><w:t xml:space="preserve"> feliz.</w:t></w:r>
        </w:p>"""

        val runs = parseWordRuns(paraXml)

        assertEquals(3, runs.size)
        assertFalse(runs[0].bold)
        assertTrue(runs[1].bold)
        assertEquals("mundo", runs[1].text)
        assertFalse(runs[2].bold)
        assertEquals("Hola mundo feliz.", runs.joinToString("") { it.text })
    }

    @Test
    fun `runs con texto en blanco se descartan`() {
        val paraXml = "<w:p><w:r><w:t></w:t></w:r><w:r><w:t>  </w:t></w:r><w:r><w:t>Real</w:t></w:r></w:p>"

        val runs = parseWordRuns(paraXml)

        assertEquals(1, runs.size)
        assertEquals("Real", runs[0].text)
    }

    @Test
    fun `multiples w-t dentro del mismo run se concatenan`() {
        val paraXml = "<w:p><w:r><w:t>Uno</w:t><w:t>Dos</w:t></w:r></w:p>"

        val runs = parseWordRuns(paraXml)

        assertEquals(1, runs.size)
        assertEquals("UnoDos", runs[0].text)
    }

    // ── WORD_HEADING_STYLE_REGEX ───────────────────────────────────────────

    @Test
    fun `reconoce el identificador de estilo real que escribe Word en espanol`() {
        // Bug real encontrado en dispositivo: Word en español escribe
        // w:val="Ttulo1" (tilde quitada), no "Heading1" como se asumía --
        // "Titulo del documento" se mostraba como texto plano en el Visor.
        val paraXml = """<w:p><w:pPr><w:pStyle w:val="Ttulo1"/></w:pPr><w:r><w:t>Titulo</w:t></w:r></w:p>"""

        assertTrue(paraXml.contains(WORD_HEADING_STYLE_REGEX))
    }

    @Test
    fun `reconoce el identificador de estilo en ingles`() {
        val paraXml = """<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Title</w:t></w:r></w:p>"""

        assertTrue(paraXml.contains(WORD_HEADING_STYLE_REGEX))
    }

    @Test
    fun `un parrafo normal no se reconoce como encabezado`() {
        val paraXml = "<w:p><w:r><w:t>Texto normal sin estilo de encabezado.</w:t></w:r></w:p>"

        assertFalse(paraXml.contains(WORD_HEADING_STYLE_REGEX))
    }
}
