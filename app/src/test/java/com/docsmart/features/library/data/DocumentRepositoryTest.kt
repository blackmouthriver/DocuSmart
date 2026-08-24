package com.docsmart.features.library.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.docsmart.core.data.FavoritesRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Cubre el bug real encontrado en Biblioteca/Home (docs/requirements/visor-biblioteca.md):
 * `removeDocument()` solo filtraba la lista en memoria, nunca borraba el
 * archivo real — al recargar (`refresh`/reabrir la app) el documento
 * "eliminado" volvía a aparecer. `deleteDocument()` reemplaza ese hueco.
 */
class DocumentRepositoryTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var repository: DocumentRepository

    @BeforeEach
    fun setUp() {
        filesDir = Files.createTempDirectory("docsmart_docrepo_files_").toFile()
        context = mockk()
        every { context.filesDir } returns filesDir
        repository = DocumentRepository(context, mockk<FavoritesRepository>())
        mockkStatic(Uri::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
        filesDir.deleteRecursively()
    }

    @Test
    fun `deleteDocument borra un archivo generado por la app`() = runTest {
        val dir = File(filesDir, "converted").apply { mkdirs() }
        val file = File(dir, "documento.pdf").apply { writeText("contenido") }

        val deleted = repository.deleteDocument(file.absolutePath)

        assertTrue(deleted)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteDocument devuelve false si el archivo de la app no existe`() = runTest {
        val missing = File(filesDir, "no_existe.pdf")

        val deleted = repository.deleteDocument(missing.absolutePath)

        assertFalse(deleted)
    }

    @Test
    fun `deleteDocument borra un documento de MediaStore via ContentResolver`() = runTest {
        val uriString = "content://media/external/downloads/12345"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri
        val resolver = mockk<ContentResolver>()
        every { resolver.delete(mockUri, null, null) } returns 1
        every { context.contentResolver } returns resolver

        val deleted = repository.deleteDocument(uriString)

        assertTrue(deleted)
    }

    @Test
    fun `deleteDocument devuelve false si ContentResolver no pudo borrar`() = runTest {
        val uriString = "content://media/external/downloads/99999"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri
        val resolver = mockk<ContentResolver>()
        every { resolver.delete(mockUri, null, null) } returns 0
        every { context.contentResolver } returns resolver

        val deleted = repository.deleteDocument(uriString)

        assertFalse(deleted)
    }

    @Test
    fun `deleteDocument devuelve false si ContentResolver lanza excepcion de permisos`() = runTest {
        val uriString = "content://media/external/images/1"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri
        val resolver = mockk<ContentResolver>()
        every { resolver.delete(mockUri, null, null) } throws SecurityException("no permission")
        every { context.contentResolver } returns resolver

        val deleted = repository.deleteDocument(uriString)

        assertFalse(deleted)
    }
}
