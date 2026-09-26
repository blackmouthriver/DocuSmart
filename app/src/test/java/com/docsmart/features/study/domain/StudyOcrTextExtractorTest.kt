package com.docsmart.features.study.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `extractTextByOcr()`/`ocrAllPages()`/`ocrOnePage()` (StudyOcrTextExtractor.kt) dependen de
 * `android.graphics.pdf.PdfRenderer` y de ML Kit Text Recognition reales -- mismo límite ya
 * documentado en el proyecto para `OcrPdfUseCase` (ver ese Test), y la regla de estabilidad #5
 * prohíbe ejercitar ML Kit real en las pruebas (el motor on-device no es mockeable de forma
 * confiable ni corre igual en todos los entornos de CI). Lo único puro de este archivo es
 * `ocrTextBlocksToParagraphs()`, extraída específicamente para poder testearla: agrupa el texto
 * ya reconocido (harían falta Bitmaps/PdfRenderer/ML Kit reales para producirlo) en párrafos aptos
 * para TTS, igual que hace `groupPdfChunksIntoParagraphs` con el texto real de un PDF.
 */
class StudyOcrTextExtractorTest {
    @Test
    fun `normaliza saltos de linea y recorta espacios de cada bloque`() {
        val result = ocrTextBlocksToParagraphs(listOf("Primera linea\nSegunda linea del mismo bloque  "))

        assertEquals(listOf("Primera linea Segunda linea del mismo bloque"), result)
    }

    @Test
    fun `descarta bloques mas cortos o iguales al umbral de ruido`() {
        // MIN_PARAGRAPH_LENGTH = 5 -- un número de página suelto o una marca de agua corta
        // (4 y 5 caracteres) se descartan, solo el párrafo real (más de 5) queda.
        val result = ocrTextBlocksToParagraphs(listOf("Pag3", "12345", "Un párrafo real con texto suficiente"))

        assertEquals(listOf("Un párrafo real con texto suficiente"), result)
    }

    @Test
    fun `varios bloques reconocidos se preservan en orden`() {
        val result = ocrTextBlocksToParagraphs(listOf("Primer bloque reconocido", "Segundo bloque reconocido"))

        assertEquals(listOf("Primer bloque reconocido", "Segundo bloque reconocido"), result)
    }

    @Test
    fun `una lista vacia no lanza y devuelve una lista vacia`() {
        assertEquals(emptyList<String>(), ocrTextBlocksToParagraphs(emptyList()))
    }

    @Test
    fun `un bloque mas largo que el limite de TTS se parte para poder leerse`() {
        val longBlock = "Palabra ".repeat(1000).trim()

        val result = ocrTextBlocksToParagraphs(listOf(longBlock))

        assertEquals(true, result.size > 1, "un bloque tan largo debe partirse en varios fragmentos")
        result.forEach { assertEquals(true, it.length <= TTS_MAX_PARAGRAPH_CHARS) }
    }
}
