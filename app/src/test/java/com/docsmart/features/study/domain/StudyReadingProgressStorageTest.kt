package com.docsmart.features.study.domain

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.docsmart.testutil.fakeContextWithPrefs
import com.docsmart.testutil.fakePrefsStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Progreso de lectura en voz alta por documento (Modo Estudio). `Uri.parse` es
 * un metodo estatico de `android.net` que no funciona en un test JVM plano, asi
 * que se mockea con `mockkStatic` solo para poder verificar la liberacion del
 * permiso persistente (`releasePersistableUriPermission`).
 */
class StudyReadingProgressStorageTest {
    private val store = fakePrefsStore()
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private val parsedUris = mutableMapOf<String, Uri>()

    @BeforeEach
    fun setUp() {
        context = fakeContextWithPrefs(store)
        resolver = mockk(relaxed = true)
        every { context.contentResolver } returns resolver
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { parsedUris.getOrPut(firstArg<String>()) { mockk<Uri>() } }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun progress(
        uri: String,
        paragraph: Int = 0,
        readAt: Long = 1L,
    ) = ReadingProgress(
        uri = uri,
        documentName = "doc-$uri",
        paragraphIndex = paragraph,
        totalParagraphs = 50,
        currentPage = 2,
        totalPages = 9,
        lastReadAtMillis = readAt,
    )

    @Test
    fun `save y loadAll conservan todos los campos`() {
        val saved = progress("content://a", paragraph = 7, readAt = 123L)

        StudyReadingProgressStorage.save(context, saved)

        assertEquals(listOf(saved), StudyReadingProgressStorage.loadAll(context))
    }

    @Test
    fun `sin datos guardados loadAll devuelve una lista vacia`() {
        assertTrue(StudyReadingProgressStorage.loadAll(context).isEmpty())
    }

    @Test
    fun `guardar el mismo documento reemplaza su progreso y lo sube al principio sin duplicarlo`() {
        StudyReadingProgressStorage.save(context, progress("content://a", paragraph = 1))
        StudyReadingProgressStorage.save(context, progress("content://b", paragraph = 2))

        StudyReadingProgressStorage.save(context, progress("content://a", paragraph = 30))

        val all = StudyReadingProgressStorage.loadAll(context)
        assertEquals(listOf("content://a", "content://b"), all.map { it.uri })
        assertEquals(30, all.first().paragraphIndex)
    }

    @Test
    fun `findFor devuelve el progreso del documento o null si no existe`() {
        StudyReadingProgressStorage.save(context, progress("content://a", paragraph = 4))

        assertEquals(4, StudyReadingProgressStorage.findFor(context, "content://a")?.paragraphIndex)
        assertNull(StudyReadingProgressStorage.findFor(context, "content://otro"))
    }

    @Test
    fun `al superar el maximo se descarta el mas antiguo y se libera solo su permiso persistente`() {
        val max = StudyReadingProgressStorage.MAX_ENTRIES
        (0 until max).forEach { StudyReadingProgressStorage.save(context, progress("content://doc$it")) }

        StudyReadingProgressStorage.save(context, progress("content://nuevo"))

        val uris = StudyReadingProgressStorage.loadAll(context).map { it.uri }
        assertEquals(max, uris.size)
        assertEquals("content://nuevo", uris.first())
        assertTrue("content://doc0" !in uris, "el mas antiguo debe descartarse")
        verify(exactly = 1) {
            resolver.releasePersistableUriPermission(
                parsedUris.getValue("content://doc0"),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        verify(exactly = 1) { resolver.releasePersistableUriPermission(any(), any()) }
    }

    @Test
    fun `remove quita el documento y libera su permiso`() {
        StudyReadingProgressStorage.save(context, progress("content://a"))
        StudyReadingProgressStorage.save(context, progress("content://b"))

        StudyReadingProgressStorage.remove(context, "content://a")

        assertEquals(listOf("content://b"), StudyReadingProgressStorage.loadAll(context).map { it.uri })
        verify(exactly = 1) {
            resolver.releasePersistableUriPermission(
                parsedUris.getValue("content://a"),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    @Test
    fun `si liberar el permiso falla igual se quita el documento`() {
        every { resolver.releasePersistableUriPermission(any(), any()) } throws SecurityException("sin permiso")
        StudyReadingProgressStorage.save(context, progress("content://a"))

        StudyReadingProgressStorage.remove(context, "content://a")

        assertTrue(StudyReadingProgressStorage.loadAll(context).isEmpty())
    }

    @Test
    fun `un JSON corrupto se ve como lista vacia y el siguiente guardado se recupera`() {
        store["progress_list"] = "esto no es json"
        assertTrue(StudyReadingProgressStorage.loadAll(context).isEmpty())

        StudyReadingProgressStorage.save(context, progress("content://a"))

        assertEquals(listOf("content://a"), StudyReadingProgressStorage.loadAll(context).map { it.uri })
    }

    // Bug real corregido: una sola entrada danada hacia que loadAll() devolviera
    // lista vacia, y como save() reescribe la lista completa a partir de eso,
    // el siguiente guardado borraba el progreso de TODOS los documentos.
    @Test
    fun `una entrada danada se salta sin perder las demas`() {
        store["progress_list"] =
            """[{"name":"sin uri"},"basura",{"uri":"content://ok","name":"bueno","paragraphIndex":3}]"""

        val all = StudyReadingProgressStorage.loadAll(context)

        assertEquals(1, all.size)
        assertEquals("content://ok", all.single().uri)
        assertEquals(3, all.single().paragraphIndex)
    }

    @Test
    fun `guardar tras encontrar una entrada danada conserva las entradas validas`() {
        store["progress_list"] = """[{"name":"sin uri"},{"uri":"content://ok","name":"bueno"}]"""

        StudyReadingProgressStorage.save(context, progress("content://nuevo"))

        val uris = StudyReadingProgressStorage.loadAll(context).map { it.uri }
        assertEquals(listOf("content://nuevo", "content://ok"), uris)
    }

    @Test
    fun `los campos opcionales ausentes toman valores por defecto`() {
        store["progress_list"] = """[{"uri":"content://ok"}]"""

        val entry = StudyReadingProgressStorage.loadAll(context).single()

        assertEquals("", entry.documentName)
        assertEquals(0, entry.paragraphIndex)
        assertEquals(0, entry.totalParagraphs)
        assertEquals(1, entry.currentPage)
        assertEquals(1, entry.totalPages)
        assertEquals(0L, entry.lastReadAtMillis)
    }

    // ── pageForParagraph() ────────────────────────────────────────────────────

    @Test
    fun `pageForParagraph sin limites devuelve la pagina 1`() {
        assertEquals(1, pageForParagraph(5, emptyList()))
    }

    @Test
    fun `pageForParagraph ubica el parrafo segun los limites acumulados por pagina`() {
        // Pagina 1: parrafos 0-2, pagina 2: 3-5, pagina 3: 6-9.
        val boundaries = listOf(3, 6, 10)

        assertEquals(1, pageForParagraph(0, boundaries))
        assertEquals(1, pageForParagraph(2, boundaries))
        assertEquals(2, pageForParagraph(3, boundaries))
        assertEquals(2, pageForParagraph(5, boundaries))
        assertEquals(3, pageForParagraph(6, boundaries))
        assertEquals(3, pageForParagraph(9, boundaries))
    }

    @Test
    fun `pageForParagraph mas alla del ultimo limite devuelve la ultima pagina`() {
        assertEquals(3, pageForParagraph(50, listOf(3, 6, 10)))
    }
}
