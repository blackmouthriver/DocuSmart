package com.docsmart.core.data

import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * `FavoritesRepository` guarda favoritos (Set<String> de documentId) y
 * alias de nombre (Map<String,String>) en dos SharedPreferences separadas.
 * Los fakes de abajo usan un backing real (Set/Map mutables) en vez de un
 * `mockk(relaxed = true)` -- así se puede instanciar un SEGUNDO repositorio
 * sobre el mismo backing para confirmar que los cambios de verdad se
 * persistieron a través de `SharedPreferences.Editor.apply()`, no que
 * quedaron solo en el estado en memoria del primer repositorio (`by lazy`).
 */
class FavoritesRepositoryTest {
    private lateinit var context: Context
    private lateinit var favoritesBacking: MutableSet<String>
    private lateinit var aliasesBacking: MutableMap<String, String>

    @BeforeEach
    fun setUp() {
        favoritesBacking = mutableSetOf()
        aliasesBacking = mutableMapOf()
        context = mockk()
        every {
            context.getSharedPreferences("docusmart_favorites", Context.MODE_PRIVATE)
        } returns fakeFavoritesPrefs(favoritesBacking)
        every {
            context.getSharedPreferences("docusmart_name_aliases", Context.MODE_PRIVATE)
        } returns fakeAliasPrefs(aliasesBacking)
    }

    private fun newRepository() = FavoritesRepository(context)

    // ── Favoritos ─────────────────────────────────────────────────────────

    @Test
    fun `isFavorite y getAllFavoriteIds reflejan el estado ya guardado en SharedPreferences`() {
        favoritesBacking.addAll(setOf("doc-1", "doc-2"))

        val repository = newRepository()

        assertTrue(repository.isFavorite("doc-1"))
        assertFalse(repository.isFavorite("doc-3"))
        assertEquals(setOf("doc-1", "doc-2"), repository.getAllFavoriteIds())
    }

    @Test
    fun `toggleFavorite marca como favorito y persiste el cambio en SharedPreferences`() =
        runTest {
            val repository = newRepository()

            val isNowFavorite = repository.toggleFavorite("doc-1")

            assertTrue(isNowFavorite)
            assertTrue(repository.isFavorite("doc-1"))
            // Confirma persistencia real (no solo en memoria): un segundo
            // repositorio sobre el mismo backing debe ver el mismo estado.
            assertEquals(setOf("doc-1"), newRepository().getAllFavoriteIds())
        }

    @Test
    fun `toggleFavorite alterna, un segundo llamado quita el favorito`() =
        runTest {
            val repository = newRepository()
            repository.toggleFavorite("doc-1")

            val isNowFavorite = repository.toggleFavorite("doc-1")

            assertFalse(isNowFavorite)
            assertFalse(repository.isFavorite("doc-1"))
            assertTrue(newRepository().getAllFavoriteIds().isEmpty())
        }

    @Test
    fun `removeFavorite quita el favorito sin importar el estado actual (idempotente)`() =
        runTest {
            val repository = newRepository()
            repository.toggleFavorite("doc-1")

            // Se llama dos veces seguidas -- a diferencia de toggleFavorite(),
            // no debe volver a agregarlo la segunda vez.
            repository.removeFavorite("doc-1")
            repository.removeFavorite("doc-1")

            assertFalse(repository.isFavorite("doc-1"))
            assertTrue(newRepository().getAllFavoriteIds().isEmpty())
        }

    @Test
    fun `removeFavorite sobre un documentId que nunca fue favorito no rompe nada`() =
        runTest {
            val repository = newRepository()

            repository.removeFavorite("nunca-favorito")

            assertTrue(repository.getAllFavoriteIds().isEmpty())
        }

    // ── Alias de nombre ───────────────────────────────────────────────────

    @Test
    fun `getAlias devuelve null si el documento no tiene alias`() {
        val repository = newRepository()

        assertNull(repository.getAlias("doc-1"))
    }

    @Test
    fun `saveAlias guarda el alias y persiste el cambio en SharedPreferences`() =
        runTest {
            val repository = newRepository()

            repository.saveAlias("doc-1", "Contrato firmado")

            assertEquals("Contrato firmado", repository.getAlias("doc-1"))
            assertEquals("Contrato firmado", newRepository().getAlias("doc-1"))
        }

    @Test
    fun `removeAlias elimina el alias y persiste el cambio en SharedPreferences`() =
        runTest {
            val repository = newRepository()
            repository.saveAlias("doc-1", "Contrato firmado")

            repository.removeAlias("doc-1")

            assertNull(repository.getAlias("doc-1"))
            assertNull(newRepository().getAlias("doc-1"))
        }

    // ── migrateId (hallazgo #50, revisión general 2026-09-16) ────────────

    @Test
    fun `migrateId mueve el favorito del id viejo al id nuevo sin dejarlo huerfano`() =
        runTest {
            val repository = newRepository()
            repository.toggleFavorite("ruta-vieja.pdf")

            repository.migrateId("ruta-vieja.pdf", "ruta-nueva.pdf")

            assertFalse(repository.isFavorite("ruta-vieja.pdf"), "el id viejo no debe seguir marcado")
            assertTrue(repository.isFavorite("ruta-nueva.pdf"), "el favorito debe seguir al documento a su id nuevo")
            // Persistencia real, no solo estado en memoria.
            assertEquals(setOf("ruta-nueva.pdf"), newRepository().getAllFavoriteIds())
        }

    @Test
    fun `migrateId mueve el alias del id viejo al id nuevo sin dejarlo huerfano`() =
        runTest {
            val repository = newRepository()
            repository.saveAlias("ruta-vieja.pdf", "Mi documento")

            repository.migrateId("ruta-vieja.pdf", "ruta-nueva.pdf")

            assertNull(repository.getAlias("ruta-vieja.pdf"), "el alias no debe seguir bajo el id viejo")
            assertEquals("Mi documento", repository.getAlias("ruta-nueva.pdf"))
            assertEquals("Mi documento", newRepository().getAlias("ruta-nueva.pdf"))
        }

    @Test
    fun `migrateId mueve favorito y alias juntos en una sola migracion`() =
        runTest {
            val repository = newRepository()
            repository.toggleFavorite("ruta-vieja.pdf")
            repository.saveAlias("ruta-vieja.pdf", "Mi documento")

            repository.migrateId("ruta-vieja.pdf", "ruta-nueva.pdf")

            assertTrue(repository.isFavorite("ruta-nueva.pdf"))
            assertEquals("Mi documento", repository.getAlias("ruta-nueva.pdf"))
            assertFalse(repository.isFavorite("ruta-vieja.pdf"))
            assertNull(repository.getAlias("ruta-vieja.pdf"))
        }

    @Test
    fun `migrateId no hace nada si el documento no era favorito ni tenia alias`() =
        runTest {
            val repository = newRepository()

            repository.migrateId("ruta-vieja.pdf", "ruta-nueva.pdf")

            assertFalse(repository.isFavorite("ruta-nueva.pdf"))
            assertNull(repository.getAlias("ruta-nueva.pdf"))
            assertTrue(repository.getAllFavoriteIds().isEmpty())
        }

    @Test
    fun `migrateId con el mismo id no altera favoritos ni alias`() =
        runTest {
            val repository = newRepository()
            repository.toggleFavorite("ruta.pdf")
            repository.saveAlias("ruta.pdf", "Mi documento")

            repository.migrateId("ruta.pdf", "ruta.pdf")

            assertTrue(repository.isFavorite("ruta.pdf"))
            assertEquals("Mi documento", repository.getAlias("ruta.pdf"))
        }

    // ── fakes respaldados por colecciones reales (mismo patrón que
    // TrashRepositoryTest.fakeSharedPreferences) ──────────────────────────

    private fun fakeFavoritesPrefs(backing: MutableSet<String>): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putStringSet(any(), any()) } answers {
            backing.clear()
            @Suppress("UNCHECKED_CAST")
            backing.addAll(secondArg<Set<String>>())
            editor
        }
        every { editor.apply() } just Runs

        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getStringSet(any(), any()) } answers { backing.toMutableSet() }
        return prefs
    }

    private fun fakeAliasPrefs(backing: MutableMap<String, String>): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            backing[firstArg<String>()] = secondArg<String>()
            editor
        }
        every { editor.remove(any()) } answers {
            backing.remove(firstArg<String>())
            editor
        }
        every { editor.apply() } just Runs

        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.all } answers { backing.toMap() }
        return prefs
    }
}
