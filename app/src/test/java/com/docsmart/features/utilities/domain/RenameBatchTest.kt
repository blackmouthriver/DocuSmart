package com.docsmart.features.utilities.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RenameBatchTest {
    private val names = listOf("IMG_001.JPG", "IMG_002.JPG", "informe final.pdf")

    @Test
    fun `sin opciones los nombres no cambian`() {
        assertEquals(names, RenameBatch.renameAll(names, RenameOptions()))
        assertFalse(RenameBatch.changesAnything(names, RenameOptions()))
    }

    @Test
    fun `buscar y reemplazar solo toca el nombre y no la extension`() {
        val options = RenameOptions(find = "IMG", replace = "Foto")
        val out = RenameBatch.renameAll(listOf("IMG_001.jpg", "IMG_jpg.jpg"), options)
        assertEquals(listOf("Foto_001.jpg", "Foto_jpg.jpg"), out)
        assertTrue(RenameBatch.changesAnything(names, RenameOptions(find = "IMG", replace = "Foto")))
    }

    @Test
    fun `prefijo, sufijo y numeracion con ceros`() {
        val out =
            RenameBatch.renameAll(
                listOf("a.png", "b.png", "c.png"),
                RenameOptions(
                    newName = "Viaje",
                    prefix = "2026 - ",
                    suffix = " ok",
                    numbering = true,
                    start = 8,
                    digits = 3,
                ),
            )
        assertEquals(listOf("2026 - Viaje ok 008.png", "2026 - Viaje ok 009.png", "2026 - Viaje ok 010.png"), out)
    }

    @Test
    fun `mayusculas y minusculas no afectan la extension`() {
        val upper = RenameOptions(caseMode = CaseMode.UPPER)
        assertEquals(listOf("IMG_001.JPG"), RenameBatch.renameAll(listOf("img_001.JPG"), upper))
        assertEquals(
            listOf("informe final.pdf"),
            RenameBatch.renameAll(listOf("INFORME Final.pdf"), RenameOptions(caseMode = CaseMode.LOWER)),
        )
    }

    @Test
    fun `los caracteres prohibidos se reemplazan y los bordes se limpian`() {
        assertEquals(listOf("a-b-c.txt"), RenameBatch.renameAll(listOf("x.txt"), RenameOptions(newName = "a/b:c")))
        assertEquals(listOf("hola.txt"), RenameBatch.renameAll(listOf("x.txt"), RenameOptions(newName = "  .hola.  ")))
    }

    @Test
    fun `un nombre vacio usa el de reserva`() {
        assertEquals(listOf("archivo.pdf"), RenameBatch.renameAll(listOf("x.pdf"), RenameOptions(newName = "...")))
        val dots = RenameOptions(newName = "...")
        assertEquals(listOf("doc.pdf"), RenameBatch.renameAll(listOf("x.pdf"), dots, fallback = "doc"))
    }

    @Test
    fun `los nombres repetidos se distinguen sin importar mayusculas`() {
        val out = RenameBatch.renameAll(listOf("a.pdf", "b.PDF", "c.pdf"), RenameOptions(newName = "Doc"))
        assertEquals(listOf("Doc.pdf", "Doc (2).PDF", "Doc (3).pdf"), out)
        assertEquals(out.map { it.lowercase() }.toSet().size, out.size)
    }

    @Test
    fun `un archivo oculto no tiene extension y los multiples puntos conservan la ultima`() {
        assertEquals(".gitignore" to "", RenameBatch.split(".gitignore"))
        assertEquals("backup.tar" to ".gz", RenameBatch.split("backup.tar.gz"))
        assertEquals("sin_punto" to "", RenameBatch.split("sin_punto"))
        assertEquals("termina." to "", RenameBatch.split("termina."))
    }

    @Test
    fun `los digitos y el inicio se limitan a rangos validos`() {
        val options = RenameOptions(newName = "n", numbering = true, start = -5, digits = 99)
        val out = RenameBatch.renameAll(listOf("a.txt"), options)
        assertEquals(listOf("n 000000.txt"), out)
    }
}
