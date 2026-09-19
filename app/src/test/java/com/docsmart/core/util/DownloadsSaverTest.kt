package com.docsmart.core.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

/**
 * Bajo el jar mockeable de Android que usan los tests unitarios de este
 * proyecto (sin Robolectric), `Build.VERSION.SDK_INT` queda en 0 por
 * defecto (los inicializadores estáticos se descartan al generar el jar) --
 * eso hace que `saveFile()`/`saveUri()` tomen siempre la rama "legacy"
 * (pre-Android 10) bajo test, la única alcanzable sin mockear
 * `ContentValues`/`MediaStore` (que si no se mockean explícitamente,
 * revientan con "Method ... not mocked"). Se mockea `Environment` para que
 * esa rama legacy escriba en un directorio temporal real, ejercitando así
 * la lógica real de saneo de nombre, colisión y manejo de errores.
 */
class DownloadsSaverTest {

    private lateinit var downloadsDir: File
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        downloadsDir = Files.createTempDirectory("docsmart_downloads_test_").toFile()
        context = mockk()
        mockkStatic(Environment::class)
        every { Environment.getExternalStoragePublicDirectory(any()) } returns downloadsDir
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Environment::class)
        downloadsDir.deleteRecursively()
    }

    // ── mimeTypeForExtension ────────────────────────────────────────────────

    @Test
    fun `mimeTypeForExtension reconoce las extensiones soportadas sin distinguir mayusculas`() {
        assertEquals("application/pdf", DownloadsSaver.mimeTypeForExtension("PDF"))
        assertEquals("text/plain", DownloadsSaver.mimeTypeForExtension("txt"))
        assertEquals("text/csv", DownloadsSaver.mimeTypeForExtension("csv"))
        assertEquals("image/jpeg", DownloadsSaver.mimeTypeForExtension("JPG"))
        assertEquals("image/jpeg", DownloadsSaver.mimeTypeForExtension("jpeg"))
        assertEquals("image/png", DownloadsSaver.mimeTypeForExtension("png"))
    }

    @Test
    fun `mimeTypeForExtension cae a octet-stream para una extension desconocida`() {
        assertEquals("application/octet-stream", DownloadsSaver.mimeTypeForExtension("xyz"))
    }

    // ── saveFile ──────────────────────────────────────────────────────────

    @Test
    fun `saveFile copia el contenido real al directorio de Descargas`() = runTest {
        val source = File(Files.createTempDirectory("docsmart_src_").toFile(), "documento.pdf")
        source.writeText("contenido real del pdf")

        val saved = DownloadsSaver.saveFile(context, source, "application/pdf")

        assertTrue(saved)
        val dest = File(downloadsDir, "documento.pdf")
        assertTrue(dest.exists())
        assertEquals("contenido real del pdf", dest.readText())
    }

    @Test
    fun `saveFile sanea un displayName con segmentos de ruta antes de usarlo como nombre destino`() {
        // Hallazgo real de esta ronda: displayName es parte de la firma
        // pública -- sin sanear, `File(dir, "../../evil.pdf")` (rama legacy)
        // reproduce el mismo path traversal ya corregido para el campo
        // "Nombre del archivo" en sanitizeOutputFileName() (revisión general
        // 2026-09-16).
        val source = File(Files.createTempDirectory("docsmart_src_").toFile(), "original.pdf")
        source.writeText("contenido")

        val saved = runBlocking {
            DownloadsSaver.saveFile(context, source, "application/pdf", displayName = "../../evil.pdf")
        }

        assertTrue(saved)
        assertTrue(File(downloadsDir, "evil.pdf").exists(), "debe guardarse con el nombre saneado dentro de Descargas")
        assertFalse(
            File(downloadsDir.parentFile?.parentFile, "evil.pdf").exists(),
            "no debe escapar del directorio de Descargas"
        )
    }

    @Test
    fun `saveFile no sobrescribe en silencio un archivo existente con el mismo nombre`() {
        // Hallazgo real de esta ronda (mismo escenario que el hallazgo #61 ya
        // corregido en SecurityManager.uniqueDestination()): dos documentos
        // distintos que comparten nombre ("Scan.pdf") y se guardan en
        // Descargas en Android 9 o anterior -- antes, el segundo pisaba en
        // silencio el contenido del primero (overwrite = true).
        File(downloadsDir, "documento.pdf").writeText("version vieja, no debe perderse")
        val source = File(Files.createTempDirectory("docsmart_src_").toFile(), "documento.pdf")
        source.writeText("version nueva")

        val saved = runBlocking { DownloadsSaver.saveFile(context, source, "application/pdf") }

        assertTrue(saved)
        assertEquals("version vieja, no debe perderse", File(downloadsDir, "documento.pdf").readText())
        val renamed = File(downloadsDir, "documento (1).pdf")
        assertTrue(
            renamed.exists(),
            "el nuevo archivo debe guardarse con un sufijo numerico en vez de pisar el existente"
        )
        assertEquals("version nueva", renamed.readText())
    }

    @Test
    fun `saveFile devuelve false si falla la copia, sin crashear`() {
        // El archivo origen no existe -> copyTo lanza FileNotFoundException.
        val missingSource = File(Files.createTempDirectory("docsmart_src_").toFile(), "no_existe.pdf")

        val saved = runBlocking { DownloadsSaver.saveFile(context, missingSource, "application/pdf") }

        assertFalse(saved)
    }

    @Test
    fun `saveFile relanza CancellationException en vez de tragarla como false`() {
        // Hallazgo real de esta ronda: catch (e: Exception) generico
        // atrapaba tambien CancellationException, rompiendo la cancelacion
        // cooperativa de corutinas (mismo patron ya corregido repetidas
        // veces en otras rondas de esta sesion).
        every { Environment.getExternalStoragePublicDirectory(any()) } throws CancellationException("cancelado")
        val source = File(Files.createTempDirectory("docsmart_src_").toFile(), "documento.pdf")
        source.writeText("contenido")

        assertThrows(CancellationException::class.java) {
            runBlocking { DownloadsSaver.saveFile(context, source, "application/pdf") }
        }
    }

    // ── saveUri ───────────────────────────────────────────────────────────

    @Test
    fun `saveUri copia el contenido real desde el ContentResolver al directorio de Descargas`() {
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        val sourceUri = mockk<Uri>()
        every { resolver.openInputStream(sourceUri) } returns ByteArrayInputStream("contenido de origen".toByteArray())

        val saved = runBlocking {
            DownloadsSaver.saveUri(context, sourceUri, "text/plain", "notas.txt")
        }

        assertTrue(saved)
        val dest = File(downloadsDir, "notas.txt")
        assertTrue(dest.exists())
        assertEquals("contenido de origen", dest.readText())
    }

    @Test
    fun `saveUri devuelve false si el ContentResolver no puede abrir la Uri de origen`() {
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        val sourceUri = mockk<Uri>()
        every { resolver.openInputStream(sourceUri) } returns null

        val saved = runBlocking {
            DownloadsSaver.saveUri(context, sourceUri, "text/plain", "notas.txt")
        }

        assertFalse(saved)
        assertFalse(File(downloadsDir, "notas.txt").exists())
    }

    @Test
    fun `saveUri no sobrescribe en silencio un archivo existente con el mismo nombre`() {
        File(downloadsDir, "notas.txt").writeText("version vieja")
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        val sourceUri = mockk<Uri>()
        every { resolver.openInputStream(sourceUri) } returns ByteArrayInputStream("version nueva".toByteArray())

        val saved = runBlocking {
            DownloadsSaver.saveUri(context, sourceUri, "text/plain", "notas.txt")
        }

        assertTrue(saved)
        assertEquals("version vieja", File(downloadsDir, "notas.txt").readText())
        assertEquals("version nueva", File(downloadsDir, "notas (1).txt").readText())
    }

    @Test
    fun `saveUri relanza CancellationException en vez de tragarla como false`() {
        val resolver = mockk<ContentResolver>()
        every { context.contentResolver } returns resolver
        val sourceUri = mockk<Uri>()
        every { resolver.openInputStream(sourceUri) } throws CancellationException("cancelado")

        assertThrows(CancellationException::class.java) {
            runBlocking { DownloadsSaver.saveUri(context, sourceUri, "text/plain", "notas.txt") }
        }
    }
}
