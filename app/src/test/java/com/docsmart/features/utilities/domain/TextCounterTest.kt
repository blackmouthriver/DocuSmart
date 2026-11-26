package com.docsmart.features.utilities.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class TextCounterTest {
    private val es = Locale.forLanguageTag("es")

    private fun words(text: String) = TextCounter.wordsOf(text, es)

    @Test
    fun `cuenta palabras ignorando signos y espacios`() {
        assertEquals(listOf("Hola", "mundo"), words("  ¡Hola,   mundo!  "))
        assertEquals(0, words("").size)
        assertEquals(0, words("... --- !!!").size)
    }

    @Test
    fun `una palabra con guion y un apostrofe cuentan como una`() {
        assertEquals(listOf("bien-estar", "social"), words("bien-estar social"))
        assertEquals(listOf("don't", "go"), TextCounter.wordsOf("don't go", Locale.ENGLISH))
    }

    @Test
    fun `un enlace y un correo son una sola palabra`() {
        assertEquals(
            listOf("Visita", "https://docusmart.online/app", "o", "escribe", "a", "hola@docusmart.online"),
            words("Visita https://docusmart.online/app, o escribe a hola@docusmart.online."),
        )
        assertEquals(2, words("www.ejemplo.com sitio").size)
    }

    @Test
    fun `isLink distingue enlaces de texto normal`() {
        assertTrue(LinkDetector.isLink("https://a.co"))
        assertTrue(LinkDetector.isLink("www.a.co"))
        assertTrue(LinkDetector.isLink("a.b@c.org"))
        assertFalse(LinkDetector.isLink("http://"))
        assertFalse(LinkDetector.isLink("www."))
        assertFalse(LinkDetector.isLink("a@b"))
        assertFalse(LinkDetector.isLink("a@@b.com"))
        assertFalse(LinkDetector.isLink("palabra"))
    }

    @Test
    fun `las estadisticas basicas`() {
        val s = TextCounter.stats("Hola mundo.\nSegunda linea aqui.\n\nOtro parrafo!", es)
        assertEquals(7, s.words)
        assertEquals(3, s.sentences)
        assertEquals(2, s.paragraphs)
        assertEquals(4, s.lines)
        assertEquals(7 / 200.0, s.readingMinutes, 1e-9)
        assertEquals(7 / 130.0, s.speakingMinutes, 1e-9)
    }

    @Test
    fun `los espacios no cuentan en caracteres sin espacios y un emoji es un caracter`() {
        val s = TextCounter.stats("a b\tc", es)
        assertEquals(5, s.characters)
        assertEquals(3, s.charactersNoSpaces)
        assertEquals(1, TextCounter.stats("😀", es).characters)
    }

    @Test
    fun `un texto vacio da ceros`() {
        val s = TextCounter.stats("", es)
        assertEquals(0, s.characters)
        assertEquals(0, s.words)
        assertEquals(0, s.sentences)
        assertEquals(0, s.paragraphs)
        assertEquals(0, s.lines)
    }

    @Test
    fun `una abreviatura no corta la oracion`() {
        assertEquals(1, TextCounter.stats("El Sr. García llegó tarde.", es).sentences)
        assertEquals(1, TextCounter.stats("La Dra. Pérez habló con J. García hoy.", es).sentences)
        assertEquals(2, TextCounter.stats("Llegó tarde. Se fue pronto.", es).sentences)
    }

    @Test
    fun `los puntos de un enlace no cortan la oracion`() {
        assertEquals(1, TextCounter.stats("Entra a www.docusmart.online hoy mismo", es).sentences)
    }

    @Test
    fun `las palabras mas repetidas omiten las cortas y los numeros`() {
        val top = TextCounter.topWords("el gato y el perro: gato gato perro 2026 2026 de", es, limit = 5)
        assertEquals(listOf("gato", "perro"), top.map { it.word })
        assertEquals(3, top[0].count)
        assertEquals(2, top[1].count)
        assertEquals(27.3, top[0].percent, 0.0)
    }

    @Test
    fun `las palabras con la misma frecuencia se ordenan alfabeticamente`() {
        val top = TextCounter.topWords("zorro casa zorro casa mesa", es)
        assertEquals(listOf("casa", "zorro", "mesa"), top.map { it.word })
    }

    @Test
    fun `el largo minimo es menor en japones, chino y coreano`() {
        assertEquals(2, TextCounter.minWordLength("ja"))
        assertEquals(2, TextCounter.minWordLength("zh"))
        assertEquals(3, TextCounter.minWordLength("es"))
    }
}
