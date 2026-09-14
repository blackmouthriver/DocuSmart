package com.docsmart.features.scanner.domain

import com.docsmart.testutil.fakeContextWithPrefs
import com.docsmart.testutil.fakePrefsStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HU-44 (backlog UX 2026-08-30/09-14): historial de QR -- mismo patrón de
 * test ya usado para `StudyNotesStorage` (Context/SharedPreferences
 * mockeados con `fakeContextWithPrefs`).
 */
class QrHistoryStorageTest {

    @Test
    fun `guardar y cargar preserva todos los campos de la entrada`() {
        val store = fakePrefsStore()
        val context = fakeContextWithPrefs(store)
        val entry = QrHistoryEntry(
            id = "1", content = "https://docsmart.app", typeName = "URL",
            source = QrHistorySource.CREATED, createdAtMillis = 1_000L
        )

        QrHistoryStorage.save(context, entry)
        val loaded = QrHistoryStorage.loadAll(context)

        assertEquals(listOf(entry), loaded)
    }

    @Test
    fun `las entradas nuevas quedan primero (mas reciente primero)`() {
        val store = fakePrefsStore()
        val context = fakeContextWithPrefs(store)
        val oldest = QrHistoryEntry("1", "a", "TEXT", QrHistorySource.SCANNED, 1_000L)
        val newest = QrHistoryEntry("2", "b", "TEXT", QrHistorySource.CREATED, 2_000L)

        QrHistoryStorage.save(context, oldest)
        QrHistoryStorage.save(context, newest)
        val loaded = QrHistoryStorage.loadAll(context)

        assertEquals(listOf("2", "1"), loaded.map { it.id })
    }

    @Test
    fun `al superar MAX_ENTRIES se descarta la mas vieja`() {
        val store = fakePrefsStore()
        val context = fakeContextWithPrefs(store)

        repeat(QrHistoryStorage.MAX_ENTRIES + 1) { i ->
            QrHistoryStorage.save(
                context,
                QrHistoryEntry(
                    id = "$i", content = "c$i", typeName = "TEXT",
                    source = QrHistorySource.SCANNED, createdAtMillis = i.toLong()
                )
            )
        }
        val loaded = QrHistoryStorage.loadAll(context)

        assertEquals(QrHistoryStorage.MAX_ENTRIES, loaded.size)
        // El id "0" (el primero guardado, el más viejo) debe haberse caído.
        assertTrue(loaded.none { it.id == "0" })
        // El último guardado (el más nuevo) debe seguir presente, primero.
        assertEquals("${QrHistoryStorage.MAX_ENTRIES}", loaded.first().id)
    }

    @Test
    fun `remove elimina solo la entrada indicada -- AC3`() {
        val store = fakePrefsStore()
        val context = fakeContextWithPrefs(store)
        QrHistoryStorage.save(context, QrHistoryEntry("1", "a", "TEXT", QrHistorySource.SCANNED, 1L))
        QrHistoryStorage.save(context, QrHistoryEntry("2", "b", "TEXT", QrHistorySource.SCANNED, 2L))
        QrHistoryStorage.save(context, QrHistoryEntry("3", "c", "TEXT", QrHistorySource.SCANNED, 3L))

        QrHistoryStorage.remove(context, "2")
        val loaded = QrHistoryStorage.loadAll(context)

        assertEquals(listOf("3", "1"), loaded.map { it.id })
    }

    @Test
    fun `clear vacia todo el historial`() {
        val store = fakePrefsStore()
        val context = fakeContextWithPrefs(store)
        QrHistoryStorage.save(context, QrHistoryEntry("1", "a", "TEXT", QrHistorySource.SCANNED, 1L))

        QrHistoryStorage.clear(context)

        assertTrue(QrHistoryStorage.loadAll(context).isEmpty())
    }

    @Test
    fun `json corrupto en preferencias devuelve lista vacia en vez de fallar`() {
        val store = fakePrefsStore()
        store["history_list"] = "esto no es json"
        val context = fakeContextWithPrefs(store)

        assertTrue(QrHistoryStorage.loadAll(context).isEmpty())
    }

    @Test
    fun `cargar sin historial guardado devuelve lista vacia`() {
        val store = fakePrefsStore()
        val context = fakeContextWithPrefs(store)

        assertTrue(QrHistoryStorage.loadAll(context).isEmpty())
    }

    @Test
    fun `el contenido protegido con prefijo se guarda y recupera tal cual -- RNF2`() {
        // El storage no cifra ni descifra nada -- solo persiste el string
        // que le pasa el caller. RNF2 (nunca texto plano de un QR protegido)
        // es responsabilidad de QUIEN llama a save() (pasar `finalContent`/
        // el valor crudo escaneado, ya cifrado con el prefijo de QrCrypto),
        // pero este test confirma que el storage en sí no lo altera.
        val store = fakePrefsStore()
        val context = fakeContextWithPrefs(store)
        val protectedContent = "${QrCrypto.PREFIX}abc123ciphertext=="
        val entry = QrHistoryEntry("1", protectedContent, "PROTECTED", QrHistorySource.SCANNED, 1L)

        QrHistoryStorage.save(context, entry)
        val loaded = QrHistoryStorage.loadAll(context)

        assertEquals(protectedContent, loaded.first().content)
    }
}
