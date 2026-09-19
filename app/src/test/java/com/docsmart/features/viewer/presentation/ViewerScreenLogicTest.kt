package com.docsmart.features.viewer.presentation

import androidx.compose.ui.geometry.Offset
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.AnnotationType
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ViewerScreenLogicTest {
    // ── hitTestAnnotation ─────────────────────────────────────────────────

    private fun annotation(
        id: String,
        type: AnnotationType,
        x: Float,
        y: Float,
        w: Float = 0f,
        h: Float = 0f,
    ) = AnnotationEntity(
        id = id,
        documentId = "doc",
        type = type,
        page = 1,
        xPts = x,
        yPts = y,
        widthPts = w,
        heightPts = h,
        color = 0,
        text = "",
        createdAt = 0L,
    )

    @Test
    fun `un tap dentro del rectangulo de un resaltado lo devuelve`() {
        // PDF (origen abajo-izq) x=10,y=70,w=20,h=10 en pagina de 100pt -> pantalla x 10..30, y 20..30
        val highlight = annotation("h", AnnotationType.HIGHLIGHT, 10f, 70f, 20f, 10f)

        val hit = hitTestAnnotation(Offset(15f, 25f), listOf(highlight), 1f, 100f, 10f)

        assertSame(highlight, hit)
    }

    @Test
    fun `un tap fuera del resaltado no devuelve nada`() {
        val highlight = annotation("h", AnnotationType.HIGHLIGHT, 10f, 70f, 20f, 10f)

        assertNull(hitTestAnnotation(Offset(5f, 25f), listOf(highlight), 1f, 100f, 10f))
        assertNull(hitTestAnnotation(Offset(15f, 35f), listOf(highlight), 1f, 100f, 10f))
    }

    @Test
    fun `el area del resaltado escala con displayScale`() {
        val highlight = annotation("h", AnnotationType.HIGHLIGHT, 10f, 70f, 20f, 10f)

        // Con escala 2: x 20..60, y 40..60
        assertSame(highlight, hitTestAnnotation(Offset(50f, 50f), listOf(highlight), 2f, 100f, 10f))
        assertNull(hitTestAnnotation(Offset(15f, 25f), listOf(highlight), 2f, 100f, 10f))
    }

    @Test
    fun `una nota se detecta dentro del radio tactil y no fuera`() {
        val note = annotation("n", AnnotationType.NOTE, 50f, 50f)

        assertSame(note, hitTestAnnotation(Offset(55f, 55f), listOf(note), 1f, 100f, 10f))
        assertSame(note, hitTestAnnotation(Offset(60f, 50f), listOf(note), 1f, 100f, 10f))
        assertNull(hitTestAnnotation(Offset(70f, 70f), listOf(note), 1f, 100f, 10f))
    }

    @Test
    fun `con anotaciones superpuestas gana la mas reciente`() {
        val old = annotation("viejo", AnnotationType.HIGHLIGHT, 10f, 70f, 20f, 10f)
        val recent = annotation("nuevo", AnnotationType.HIGHLIGHT, 10f, 70f, 20f, 10f)

        val hit = hitTestAnnotation(Offset(15f, 25f), listOf(old, recent), 1f, 100f, 10f)

        assertSame(recent, hit)
    }

    @Test
    fun `sin anotaciones el hit test devuelve null`() {
        assertNull(hitTestAnnotation(Offset(1f, 1f), emptyList(), 1f, 100f, 10f))
    }

    // ── truncateViewerText / matchingTextLines ────────────────────────────

    @Test
    fun `un texto dentro del limite no se modifica`() {
        assertEquals("hola", truncateViewerText("hola", 4, "aviso"))
    }

    @Test
    fun `un texto que excede el limite se recorta y agrega el aviso`() {
        assertEquals("abc\n\naviso", truncateViewerText("abcdef", 3, "aviso"))
    }

    @Test
    fun `matchingTextLines filtra lineas sin distinguir mayusculas`() {
        val text = "Uno\nDOS y tres\ncuatro\r\nTRES mas"

        assertEquals(listOf("DOS y tres", "TRES mas"), matchingTextLines(text, "tres"))
    }

    @Test
    fun `matchingTextLines sin coincidencias devuelve lista vacia`() {
        assertTrue(matchingTextLines("a\nb", "zzz").isEmpty())
    }

    // ── extractExcelSheets ────────────────────────────────────────────────

    private fun workbookStream(build: (Workbook) -> Unit): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { wb ->
            build(wb)
            wb.write(out)
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    @Test
    fun `extractExcelSheets lee hojas, filas y celdas como texto`() {
        val stream =
            workbookStream { wb ->
                val sheet = wb.createSheet("Datos")
                sheet.createRow(0).apply {
                    createCell(0).setCellValue("Nombre")
                    createCell(1).setCellValue("Edad")
                }
                sheet.createRow(1).apply {
                    createCell(0).setCellValue("Ana")
                    createCell(1).setCellValue(30.0)
                }
            }

        val sheets = extractExcelSheets(stream)

        assertEquals(1, sheets.size)
        assertEquals("Datos", sheets[0].name)
        assertEquals(listOf("Nombre", "Edad"), sheets[0].rows[0].cells)
        assertEquals(listOf("Ana", "30"), sheets[0].rows[1].cells)
    }

    @Test
    fun `extractExcelSheets omite hojas vacias y filas en blanco y rellena celdas faltantes`() {
        val stream =
            workbookStream { wb ->
                wb.createSheet("Vacia")
                val sheet = wb.createSheet("Con datos")
                sheet.createRow(0).createCell(2).setCellValue("x")
                sheet.createRow(1).createCell(0).setCellValue("   ")
            }

        val sheets = extractExcelSheets(stream)

        assertEquals(listOf("Con datos"), sheets.map { it.name })
        assertEquals(1, sheets[0].rows.size)
        assertEquals(listOf("", "", "x"), sheets[0].rows[0].cells)
    }

    @Test
    fun `extractExcelSheets evalua formulas`() {
        val stream =
            workbookStream { wb ->
                val row = wb.createSheet("F").createRow(0)
                row.createCell(0).setCellValue(2.0)
                row.createCell(1).cellFormula = "A1*3"
            }

        val sheets = extractExcelSheets(stream)

        assertEquals(listOf("2", "6"), sheets.single().rows.single().cells)
    }

    @Test
    fun `extractExcelSheets conserva el orden de las hojas`() {
        val stream =
            workbookStream { wb ->
                wb.createSheet("Primera").createRow(0).createCell(0).setCellValue("a")
                wb.createSheet("Segunda").createRow(0).createCell(0).setCellValue("b")
            }

        assertEquals(listOf("Primera", "Segunda"), extractExcelSheets(stream).map { it.name })
    }
}
