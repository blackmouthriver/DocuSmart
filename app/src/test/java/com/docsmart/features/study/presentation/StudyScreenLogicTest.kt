package com.docsmart.features.study.presentation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class StudyScreenLogicTest {
    @Test
    fun `diffNoteImages separa conservadas quitadas y nuevas`() {
        val original = mapOf("u1" to "img1", "u2" to "img2", "u3" to "img3")

        val diff = diffNoteImages(listOf("u1", "u3", "nueva"), original)

        diff.keptImages shouldBe listOf("img1", "img3")
        diff.removedImages shouldBe listOf("img2")
        diff.newImageUris shouldBe listOf("nueva")
    }

    @Test
    fun `diffNoteImages sin cambios no quita ni agrega nada`() {
        val diff = diffNoteImages(listOf("u1"), mapOf("u1" to "img1"))

        diff.keptImages shouldBe listOf("img1")
        diff.removedImages shouldBe emptyList()
        diff.newImageUris shouldBe emptyList()
    }

    @Test
    fun `diffNoteImages quitando todas las imagenes las marca como quitadas`() {
        val diff = diffNoteImages(emptyList<String>(), mapOf("u1" to "img1"))

        diff.removedImages shouldBe listOf("img1")
    }

    @Test
    fun `formatPomodoroClock rellena con ceros`() {
        formatPomodoroClock(5, 7) shouldBe "05:07"
        formatPomodoroClock(25, 0) shouldBe "25:00"
        formatPomodoroClock(0, 59) shouldBe "00:59"
    }

    @Test
    fun `weekBarHeightDp tiene minimo de 4 y maximo de 32`() {
        val counts = intArrayOf(0, 1, 4, 8)

        weekBarHeightDp(0, counts) shouldBe 4
        weekBarHeightDp(1, counts) shouldBe 4
        weekBarHeightDp(4, counts) shouldBe 16
        weekBarHeightDp(8, counts) shouldBe 32
    }

    @Test
    fun `weekBarHeightDp con todos en cero no divide por cero`() {
        weekBarHeightDp(0, intArrayOf(0, 0, 0)) shouldBe 4
        weekBarHeightDp(0, intArrayOf()) shouldBe 4
    }

    @Test
    fun `isReminderInPast incluye el instante actual`() {
        isReminderInPast(100L, 100L) shouldBe true
        isReminderInPast(99L, 100L) shouldBe true
        isReminderInPast(101L, 100L) shouldBe false
    }

    @Test
    fun `noteReminderPresetMillis cae a las 9 del dia esperado`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 9, 19)

        val millis = noteReminderPresetMillis(3, today, zone)

        millis shouldBe LocalDate.of(2026, 9, 22).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
    }

    @Test
    fun `notesListScrollIndex es nulo si la nota no existe`() {
        notesListScrollIndex(-1, 2, 10) shouldBe null
    }

    @Test
    fun `notesListScrollIndex sin resaltados suma solo editor y cabecera`() {
        notesListScrollIndex(3, 0, 10) shouldBe 5
        notesListScrollIndex(0, 4, 0) shouldBe 2
    }

    @Test
    fun `notesListScrollIndex con resaltados suma titulo y separador`() {
        notesListScrollIndex(1, 3, 10) shouldBe (3 + 2 + 2 + 1)
    }

    @Test
    fun `pdfViewerPageIndex limita al rango valido`() {
        pdfViewerPageIndex(1, 5) shouldBe 0
        pdfViewerPageIndex(5, 5) shouldBe 4
        pdfViewerPageIndex(9, 5) shouldBe 4
        pdfViewerPageIndex(0, 5) shouldBe 0
        pdfViewerPageIndex(3, 0) shouldBe 0
    }

    @Test
    fun `applyPdfGesture limita el zoom`() {
        val base = PdfTransform(1f, 0f, 0f)

        applyPdfGesture(base, 10f, 0f, 0f, 100f, 200f).scale shouldBe 4f
        applyPdfGesture(base, 0.01f, 0f, 0f, 100f, 200f).scale shouldBe 0.5f
    }

    @Test
    fun `applyPdfGesture sin zoom no permite desplazarse`() {
        val result = applyPdfGesture(PdfTransform(1f, 0f, 0f), 1f, 50f, -50f, 100f, 200f)

        // Se compara con == primitivo: el límite puede ser -0f, que es igual a 0f numéricamente.
        (result.scale == 1f) shouldBe true
        (result.offsetX == 0f) shouldBe true
        (result.offsetY == 0f) shouldBe true
    }

    @Test
    fun `applyPdfGesture con zoom limita el desplazamiento a los bordes`() {
        // escala 2 -> maxX = 100*(2-1)/2 = 50, maxY = 200*(2-1)/2 = 100
        val result = applyPdfGesture(PdfTransform(2f, 40f, -90f), 1f, 30f, -30f, 100f, 200f)

        result.offsetX shouldBe 50f
        result.offsetY shouldBe -100f
    }

    @Test
    fun `parseUtteranceIndex extrae el sufijo numerico`() {
        parseUtteranceIndex("study_12") shouldBe 12
        parseUtteranceIndex("a_b_3") shouldBe 3
        parseUtteranceIndex("7") shouldBe 7
    }

    @Test
    fun `parseUtteranceIndex devuelve nulo si no es valido`() {
        parseUtteranceIndex(null) shouldBe null
        parseUtteranceIndex("study_x") shouldBe null
        parseUtteranceIndex("study_") shouldBe null
    }
}
