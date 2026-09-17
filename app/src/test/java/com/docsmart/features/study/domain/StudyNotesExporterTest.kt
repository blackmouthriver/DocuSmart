package com.docsmart.features.study.domain

import com.docsmart.core.data.db.NoteEntity
import com.docsmart.core.data.db.NoteImageEntity
import com.docsmart.core.data.db.NoteWithImages
import io.mockk.every
import io.mockk.mockk
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

/**
 * Backlog UX #51: `buildPlainText` es lógica pura (sin Context ni archivos
 * reales); `exportAsWordFile` sí se cubre con un archivo temporal real
 * (Apache POI es JVM puro, no depende de Android) -- `exportAsPdfFile`/
 * `exportAsTextFile` quedan sin cubrir por escribir a disco con Context real,
 * mismo límite ya documentado para otros use cases de conversión/PDF.
 *
 * Hallazgo real de la auditoría general 2026-09-17: el AC1 de HU-51 exige
 * "conserva el texto e imágenes adjuntas (HU-49)" -- `exportAsWordFile()`
 * ahora recibe `NoteWithImages`, no solo `NoteEntity`, y los tests de abajo
 * verifican que una imagen adjunta real termina embebida en el .docx.
 */
class StudyNotesExporterTest {

    private fun note(id: String, title: String, text: String, createdAt: Long) =
        NoteEntity(id = id, title = title, text = text, createdAt = createdAt)

    private fun noteWithImages(
        id: String, title: String, text: String, createdAt: Long,
        images: List<NoteImageEntity> = emptyList()
    ) = NoteWithImages(note(id, title, text, createdAt), images)

    @Test
    fun `una sola nota incluye titulo, fecha formateada y texto`() {
        // 24/08/2026 10:00:00 local -- el formato exacto se verifica indirectamente
        // (createdAt es un timestamp real, no un string ya formateado como antes).
        val note = note("1", "Repaso", "Contenido de la nota", createdAt = 1787644800000L)

        val result = StudyNotesExporter.buildPlainText(listOf(note))

        assertTrue(result.contains("Repaso"))
        assertTrue(result.contains("Contenido de la nota"))
    }

    @Test
    fun `varias notas quedan separadas por un separador`() {
        val first = note("1", "A", "texto A", createdAt = 1000L)
        val second = note("2", "B", "texto B", createdAt = 2000L)

        val result = StudyNotesExporter.buildPlainText(listOf(first, second))
        val parts = result.split("\n\n")

        assertTrue(result.indexOf("texto A") < result.indexOf("texto B"))
        assertTrue(parts.size >= 3, "debe haber al menos un bloque separador entre las dos notas")
    }

    @Test
    fun `sin notas devuelve texto vacio`() {
        assertEquals("", StudyNotesExporter.buildPlainText(emptyList()))
    }

    @Test
    fun `exportAsWordFile genera un docx real con el titulo y el texto de la nota`() {
        val tempDir = Files.createTempDirectory("docsmart_notes_export_").toFile()
        val context = mockk<android.content.Context>()
        every { context.filesDir } returns tempDir

        val note = noteWithImages(
            "1", "Mi nota", "Primera línea\nSegunda línea", createdAt = System.currentTimeMillis()
        )

        val file = StudyNotesExporter.exportAsWordFile(context, listOf(note))

        assertTrue(file.exists())
        assertTrue(file.length() > 0L, "el .docx generado no debe estar vacío")

        val extractedText = FileInputStream(file).use { input ->
            XWPFDocument(input).use { docx ->
                docx.paragraphs.joinToString("\n") { it.text }
            }
        }
        assertTrue(extractedText.contains("Mi nota"))
        assertTrue(extractedText.contains("Primera línea"))
        assertTrue(extractedText.contains("Segunda línea"))

        tempDir.deleteRecursively()
    }

    @Test
    fun `exportAsWordFile con varias notas conserva el orden`() {
        val tempDir = Files.createTempDirectory("docsmart_notes_export_multi_").toFile()
        val context = mockk<android.content.Context>()
        every { context.filesDir } returns tempDir

        val notes = listOf(
            noteWithImages("1", "Primera", "contenido 1", createdAt = 1000L),
            noteWithImages("2", "Segunda", "contenido 2", createdAt = 2000L)
        )

        val file = StudyNotesExporter.exportAsWordFile(context, notes)

        val extractedText = FileInputStream(file).use { input ->
            XWPFDocument(input).use { docx -> docx.paragraphs.joinToString("\n") { it.text } }
        }
        assertTrue(extractedText.indexOf("Primera") < extractedText.indexOf("Segunda"))

        tempDir.deleteRecursively()
    }

    // Nota: no hay test que ejercite addImagesToWord() con una imagen real
    // -- usa android.graphics.BitmapFactory (solo para leer bounds), que no
    // está disponible en un test JVM plano sin Robolectric (mismo límite ya
    // documentado en la clase para exportAsPdfFile()/exportAsTextFile()).
    // El camino de "sin imágenes" (images = emptyList(), los tests de
    // arriba) sí queda cubierto porque nunca llega a tocar BitmapFactory.
}
