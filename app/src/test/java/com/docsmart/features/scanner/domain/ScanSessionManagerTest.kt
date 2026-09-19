package com.docsmart.features.scanner.domain

import android.content.Context
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * `ScanSessionManager` es un `@Singleton` que sobrevive a la navegación
 * Scanner → ScanResult mientras el usuario sigue en esa sesión (ver el
 * comentario de la clase) -- la limpieza real al salir vive en la capa de
 * UI (`ScanResultScreen`: `goHomeAction`/`DisposableEffect`), fuera de
 * alcance de este archivo. Lo que sí es responsabilidad de esta clase, y lo
 * que cubren estos tests, es que la lista en memoria (dedup, favoritos,
 * rename, delete) se mantenga consistente con lo que reportan los
 * repositorios reales.
 */
class ScanSessionManagerTest {
    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var documentRepository: DocumentRepository
    private lateinit var trashRepository: TrashRepository
    private lateinit var manager: ScanSessionManager

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("docsmart_scansession_").toFile()
        context = mockk()
        every { context.getString(any(), any()) } returns "1.0 MB"
        favoritesRepository = mockk()
        every { favoritesRepository.isFavorite(any()) } returns false
        documentRepository = mockk()
        trashRepository = mockk()
        manager = ScanSessionManager(favoritesRepository, documentRepository, trashRepository)
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun scannedFile(name: String): File = File(tempDir, name).apply { writeText("contenido") }

    @Test
    fun `addFile agrega el documento a la sesion con sus datos basicos`() {
        val file = scannedFile("escaneo.pdf")

        manager.addFile(file, context)

        val files = manager.scannedFiles.value
        assertEquals(1, files.size)
        assertEquals(file.absolutePath, files[0].id)
        assertEquals("escaneo.pdf", files[0].name)
        assertEquals(DocumentType.PDF, files[0].type)
    }

    @Test
    fun `addFile no duplica el mismo archivo si se agrega dos veces`() {
        val file = scannedFile("escaneo.pdf")

        manager.addFile(file, context)
        manager.addFile(file, context)

        assertEquals(1, manager.scannedFiles.value.size)
    }

    @Test
    fun `addFile clasifica una imagen jpg como DocumentType IMAGE`() {
        val file = scannedFile("pagina.jpg")

        manager.addFile(file, context)

        assertEquals(
            DocumentType.IMAGE,
            manager.scannedFiles.value
                .first()
                .type,
        )
    }

    @Test
    fun `addFile marca isFavorite segun lo que reporta FavoritesRepository`() {
        val file = scannedFile("favorito.pdf")
        every { favoritesRepository.isFavorite(file.absolutePath) } returns true

        manager.addFile(file, context)

        assertTrue(
            manager.scannedFiles.value
                .first()
                .isFavorite,
        )
    }

    @Test
    fun `clear vacia la lista de archivos escaneados de la sesion`() {
        manager.addFile(scannedFile("uno.pdf"), context)
        manager.addFile(scannedFile("dos.pdf"), context)

        manager.clear()

        assertTrue(manager.scannedFiles.value.isEmpty())
    }

    @Test
    fun `toggleFavorite actualiza el estado del documento correspondiente en la sesion`() =
        runTest {
            val file = scannedFile("a.pdf")
            manager.addFile(file, context)
            coEvery { favoritesRepository.toggleFavorite(file.absolutePath) } returns true

            manager.toggleFavorite(file.absolutePath)

            assertTrue(
                manager.scannedFiles.value
                    .first()
                    .isFavorite,
            )
        }

    @Test
    fun `toggleFavorite no afecta otros documentos de la sesion`() =
        runTest {
            val fileA = scannedFile("a.pdf")
            val fileB = scannedFile("b.pdf")
            manager.addFile(fileA, context)
            manager.addFile(fileB, context)
            coEvery { favoritesRepository.toggleFavorite(fileA.absolutePath) } returns true

            manager.toggleFavorite(fileA.absolutePath)

            val files = manager.scannedFiles.value
            assertTrue(files.first { it.id == fileA.absolutePath }.isFavorite)
            assertFalse(files.first { it.id == fileB.absolutePath }.isFavorite)
        }

    @Test
    fun `renameDocument actualiza id y nombre con el resultado real del repositorio`() =
        runTest {
            val file = scannedFile("viejo.pdf")
            manager.addFile(file, context)
            val newPath = File(tempDir, "nuevo.pdf").absolutePath
            coEvery { documentRepository.renameDocument(file.absolutePath, "nuevo.pdf") } returns newPath

            manager.renameDocument(file.absolutePath, "nuevo.pdf")

            val updated = manager.scannedFiles.value.first()
            assertEquals(newPath, updated.id)
            assertEquals("nuevo.pdf", updated.name)
        }

    @Test
    fun `deleteDocument quita el archivo de la sesion si se movio a la papelera exitosamente`() =
        runTest {
            val file = scannedFile("borrar.pdf")
            manager.addFile(file, context)
            coEvery { trashRepository.moveToTrash(file.absolutePath) } returns true

            val result = manager.deleteDocument(file.absolutePath)

            assertTrue(result)
            assertTrue(manager.scannedFiles.value.isEmpty())
        }

    @Test
    fun `deleteDocument conserva el archivo en la sesion si no se pudo mover a la papelera`() =
        runTest {
            val file = scannedFile("nose-borra.pdf")
            manager.addFile(file, context)
            coEvery { trashRepository.moveToTrash(file.absolutePath) } returns false

            val result = manager.deleteDocument(file.absolutePath)

            assertFalse(result)
            assertEquals(1, manager.scannedFiles.value.size)
        }
}
