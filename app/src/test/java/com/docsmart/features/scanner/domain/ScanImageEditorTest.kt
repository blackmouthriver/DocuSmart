package com.docsmart.features.scanner.domain

import android.content.Context
import android.net.Uri
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * RF-SCAN-06/RF-SCAN-07: solo cubre la lógica pura (matriz de color y
 * dimensiones de reescalado) -- `ScanImageEditor.applyAdjustments()` en sí
 * usa `Bitmap`/`Canvas`/`ColorMatrixColorFilter` de `android.graphics`
 * (stub "not mocked" en unit tests, requiere Robolectric/instrumentación),
 * mismo límite ya documentado para otros use cases del proyecto que tocan
 * bitmaps reales (`CompressPdfUseCase`, ver `pdf-tools.md`).
 */
class ScanImageEditorTest {
    @Test
    fun `sin ajustes, la matriz de color es la identidad`() {
        val matrix = buildColorMatrix(brightness = 0, contrast = 0)

        assertEquals(
            listOf(
                1f,
                0f,
                0f,
                0f,
                0f,
                0f,
                1f,
                0f,
                0f,
                0f,
                0f,
                0f,
                1f,
                0f,
                0f,
                0f,
                0f,
                0f,
                1f,
                0f,
            ),
            matrix.toList(),
        )
    }

    @Test
    fun `contraste maximo duplica el factor de escala de cada canal`() {
        val matrix = buildColorMatrix(brightness = 0, contrast = 100)

        assertEquals(2f, matrix[0])
        assertEquals(2f, matrix[6])
        assertEquals(2f, matrix[12])
    }

    @Test
    fun `contraste minimo anula el factor de escala de cada canal`() {
        val matrix = buildColorMatrix(brightness = 0, contrast = -100)

        assertEquals(0f, matrix[0])
        assertEquals(0f, matrix[6])
        assertEquals(0f, matrix[12])
    }

    @Test
    fun `brillo positivo suma un desplazamiento positivo sin tocar el factor`() {
        val matrix = buildColorMatrix(brightness = 100, contrast = 0)

        assertEquals(1f, matrix[0], "el factor de contraste no cambia")
        assertEquals(255f, matrix[4])
        assertEquals(255f, matrix[9])
        assertEquals(255f, matrix[14])
    }

    @Test
    fun `brillo negativo resta un desplazamiento`() {
        val matrix = buildColorMatrix(brightness = -100, contrast = 0)

        assertEquals(-255f, matrix[4])
    }

    @Test
    fun `scaledDimensions al 100 por ciento no cambia las dimensiones`() {
        assertEquals(800 to 600, scaledDimensions(800, 600, 100))
    }

    @Test
    fun `scaledDimensions al 50 por ciento reduce a la mitad`() {
        assertEquals(400 to 300, scaledDimensions(800, 600, 50))
    }

    @Test
    fun `scaledDimensions al 25 por ciento redondea sin llegar a cero`() {
        val (width, height) = scaledDimensions(3, 3, 25)

        assertEquals(1, width)
        assertEquals(1, height)
    }

    // Hallazgo real de la auditoría general 2026-09-17 (B9): deleteCachedFile()
    // solo debe borrar un archivo si ESTA instancia lo creó vía
    // applyAdjustments() -- un URI que nunca pasó por acá (la URI original
    // del escaneo, o la de otra página) debe ser un no-op seguro, nunca un
    // error. `applyAdjustments()` en sí no se puede probar en un test JVM
    // puro (usa Bitmap/Canvas reales, ver la nota de la clase), pero este
    // contrato de "nunca tocar lo que no reconoce" sí es puro.
    @Test
    fun `deleteCachedFile no lanza para un URI que nunca creo esta instancia`() {
        val editor = ScanImageEditor(mockk<Context>(relaxed = true))
        val uriDesconocida = mockk<Uri>()

        assertDoesNotThrow { editor.deleteCachedFile(uriDesconocida) }
    }

    // ── deleteCachedFile(): solo borra lo que esta instancia creo (B9) ────────

    @Test
    fun `deleteCachedFile borra el archivo de una edicion propia y solo una vez`() {
        val dir = Files.createTempDirectory("scan_editor_test_").toFile()
        val owned = java.io.File(dir, "edit_1.jpg").apply { writeText("x") }
        val editor = ScanImageEditor(mockk<Context>(relaxed = true))
        val uri = mockk<Uri>()
        editor.trackOwnedFile(uri, owned)

        editor.deleteCachedFile(uri)

        assertFalse(owned.exists())
        // Un segundo borrado del mismo URI ya no lo reconoce: no-op seguro.
        assertDoesNotThrow { editor.deleteCachedFile(uri) }
        dir.deleteRecursively()
    }

    @Test
    fun `deleteCachedFile no toca el archivo de otro URI`() {
        val dir = Files.createTempDirectory("scan_editor_test_").toFile()
        val ownedA = java.io.File(dir, "edit_a.jpg").apply { writeText("a") }
        val ownedB = java.io.File(dir, "edit_b.jpg").apply { writeText("b") }
        val editor = ScanImageEditor(mockk<Context>(relaxed = true))
        val uriA = mockk<Uri>()
        val uriB = mockk<Uri>()
        editor.trackOwnedFile(uriA, ownedA)
        editor.trackOwnedFile(uriB, ownedB)

        editor.deleteCachedFile(uriA)

        assertFalse(ownedA.exists())
        assertTrue(ownedB.exists(), "la edicion de otra pagina no debe borrarse")
        dir.deleteRecursively()
    }

    @Test
    fun `deleteCachedFile con un URI desconocido no borra ninguna edicion registrada`() {
        val dir = Files.createTempDirectory("scan_editor_test_").toFile()
        val owned = java.io.File(dir, "edit_a.jpg").apply { writeText("a") }
        val editor = ScanImageEditor(mockk<Context>(relaxed = true))
        editor.trackOwnedFile(mockk<Uri>(), owned)

        editor.deleteCachedFile(mockk<Uri>())

        assertTrue(owned.exists())
        dir.deleteRecursively()
    }

    // ── buildColorMatrix() / scaledDimensions(): mas casos ────────────────────

    @Test
    fun `el contraste ancla el gris medio 128 y el brillo lo desplaza`() {
        // v' = factor * v + traslacion: con contraste solo, 128 debe quedar en 128.
        val contrastOnly = buildColorMatrix(brightness = 0, contrast = 50)
        assertEquals(128f, contrastOnly[0] * 128f + contrastOnly[4], 0.001f)

        // Con brillo +10 el mismo gris sube 25.5 (10 * 2.55).
        val withBrightness = buildColorMatrix(brightness = 10, contrast = 50)
        assertEquals(128f + 25.5f, withBrightness[0] * 128f + withBrightness[4], 0.001f)
    }

    @Test
    fun `brillo y contraste nunca alteran el canal alfa ni mezclan canales`() {
        val matrix = buildColorMatrix(brightness = 37, contrast = -42)

        // Fila alfa: 0,0,0,1,0
        assertEquals(listOf(0f, 0f, 0f, 1f, 0f), matrix.slice(15..19))
        // Sin mezcla entre R/G/B: solo la diagonal es distinta de cero.
        assertTrue(listOf(1, 2, 3, 5, 7, 8, 10, 11, 13).all { matrix[it] == 0f })
    }

    @Test
    fun `la matriz siempre tiene 20 valores`() {
        assertEquals(20, buildColorMatrix(-100, 100).size)
    }

    @Test
    fun `scaledDimensions escala anchos y altos distintos de forma independiente`() {
        assertEquals(1000 to 750, scaledDimensions(4000, 3000, 25))
    }

    @Test
    fun `scaledDimensions nunca devuelve una dimension menor a 1`() {
        assertEquals(1 to 1, scaledDimensions(1, 1, 1))
        assertEquals(1 to 1, scaledDimensions(10, 10, 0))
    }
}
