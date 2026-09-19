package com.docsmart.features.scanner.domain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

enum class QrHistorySource { CREATED, SCANNED }

data class QrHistoryEntry(
    val id: String,
    // Payload final tal cual se codificó/leyó -- si el QR estaba protegido
    // con contraseña, esto YA incluye el prefijo `QrCrypto.PREFIX` y el
    // texto cifrado (nunca el contenido en texto plano) -- RNF2.
    val content: String,
    // "URL"/"TEXT"/"EMAIL"/"PHONE"/"IMAGE"/"DOCUMENT"/"WIFI"/"CONTACT"/
    // "EVENT" (mismo vocabulario que ya usan QrContentType.name y los
    // literales de HU-43), o "PROTECTED" cuando source=SCANNED y el QR
    // leído está cifrado (el tipo real no se conoce hasta desbloquearlo).
    val typeName: String,
    val source: QrHistorySource,
    val createdAtMillis: Long,
)

/**
 * HU-44 (backlog UX 2026-08-30/09-14): historial de los últimos códigos QR
 * creados o leídos -- mismo patrón de persistencia ya usado por
 * `StudyReadingProgressStorage`/`StudyNotesStorage` (SharedPreferences +
 * JSON, sin Room/Hilt, funciones estáticas que reciben `Context` explícito
 * y se llaman directo desde el Composable). Máximo [MAX_ENTRIES] -- la más
 * antigua se descarta al llegar al límite (RF1).
 */
object QrHistoryStorage {
    private const val PREFS_NAME = "qr_history"
    private const val KEY_LIST = "history_list"
    const val MAX_ENTRIES = 50

    // Un JSON corrupto o inesperado debe verse como lista vacía, no como un
    // crash de la pantalla de Historial.
    @Suppress("TooGenericExceptionCaught")
    fun loadAll(context: Context): List<QrHistoryEntry> =
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_LIST, "[]") ?: "[]"
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                QrHistoryEntry(
                    id = obj.getString("id"),
                    content = obj.getString("content"),
                    typeName = obj.optString("type", "TEXT"),
                    source =
                        runCatching { QrHistorySource.valueOf(obj.getString("source")) }
                            .getOrDefault(QrHistorySource.SCANNED),
                    createdAtMillis = obj.optLong("createdAt", 0L),
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cargando historial de QR")
            emptyList()
        }

    // Inserta al principio (más reciente primero) y recorta al tope,
    // descartando lo más viejo -- RF1.
    @Suppress("TooGenericExceptionCaught")
    fun save(
        context: Context,
        entry: QrHistoryEntry,
    ) {
        try {
            val combined = (listOf(entry) + loadAll(context)).take(MAX_ENTRIES)
            persist(context, combined)
        } catch (e: Exception) {
            Timber.e(e, "Error guardando historial de QR")
        }
    }

    // AC3: elimina solo esta entrada -- no afecta ninguna otra ni ningún
    // archivo real (el historial nunca guarda archivos, solo el string del
    // contenido).
    @Suppress("TooGenericExceptionCaught")
    fun remove(
        context: Context,
        id: String,
    ) {
        try {
            persist(context, loadAll(context).filterNot { it.id == id })
        } catch (e: Exception) {
            Timber.e(e, "Error eliminando entrada del historial de QR")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun clear(context: Context) {
        try {
            persist(context, emptyList())
        } catch (e: Exception) {
            Timber.e(e, "Error vaciando el historial de QR")
        }
    }

    private fun persist(
        context: Context,
        list: List<QrHistoryEntry>,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("content", entry.content)
                    put("type", entry.typeName)
                    put("source", entry.source.name)
                    put("createdAt", entry.createdAtMillis)
                },
            )
        }
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
    }
}
