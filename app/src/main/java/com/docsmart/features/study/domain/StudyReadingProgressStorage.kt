package com.docsmart.features.study.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

data class ReadingProgress(
    val uri             : String,
    val documentName    : String,
    val paragraphIndex  : Int,
    val totalParagraphs : Int,
    val currentPage     : Int,
    val totalPages      : Int,
    val lastReadAtMillis: Long
)

/**
 * Recuerda, por PDF, en qué párrafo/página se quedó la lectura en voz alta de
 * Modo Estudio -- pedido explícito del usuario 2026-09-08: antes, parar y
 * volver a "Leer todo" (en la misma sesión o en una sesión nueva) siempre
 * empezaba desde el principio, sin forma de retomar. Máximo [MAX_ENTRIES]
 * documentos -- el más antiguo se descarta al llegar al límite, liberando
 * también su permiso persistente sobre el URI (`takePersistableUriPermission`
 * en `StudyScreen.kt`, necesario para poder reabrir el archivo en una sesión
 * futura -- Android revoca el permiso temporal del selector de documentos en
 * cuanto termina el proceso de la app).
 */
object StudyReadingProgressStorage {
    private const val PREFS_NAME = "study_reading_progress"
    private const val KEY_LIST = "progress_list"
    const val MAX_ENTRIES = 10

    // Un JSON corrupto o inesperado debe verse como lista vacía, no como un
    // crash de toda la pantalla de Estudio.
    @Suppress("TooGenericExceptionCaught")
    fun loadAll(context: Context): List<ReadingProgress> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_LIST, "[]") ?: "[]"
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ReadingProgress(
                    uri              = obj.getString("uri"),
                    documentName     = obj.optString("name", ""),
                    paragraphIndex   = obj.optInt("paragraphIndex", 0),
                    totalParagraphs  = obj.optInt("totalParagraphs", 0),
                    currentPage      = obj.optInt("currentPage", 1),
                    totalPages       = obj.optInt("totalPages", 1),
                    lastReadAtMillis = obj.optLong("lastReadAt", 0L)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cargando progreso de lectura")
            emptyList()
        }
    }

    fun findFor(context: Context, uri: String): ReadingProgress? =
        loadAll(context).find { it.uri == uri }

    // Guarda/actualiza el progreso de un documento -- si ya existía, se
    // reemplaza y sube al principio (más reciente primero). Lo que se cae
    // del tope libera su permiso persistente sobre el archivo.
    @Suppress("TooGenericExceptionCaught")
    fun save(context: Context, progress: ReadingProgress) {
        try {
            val combined = listOf(progress) + loadAll(context).filterNot { it.uri == progress.uri }
            combined.drop(MAX_ENTRIES).forEach { releasePermission(context, it.uri) }
            persist(context, combined.take(MAX_ENTRIES))
        } catch (e: Exception) {
            Timber.e(e, "Error guardando progreso de lectura")
        }
    }

    // Se llama cuando la lectura llega al final del documento -- ya no hay
    // nada que retomar.
    @Suppress("TooGenericExceptionCaught")
    fun remove(context: Context, uri: String) {
        try {
            releasePermission(context, uri)
            persist(context, loadAll(context).filterNot { it.uri == uri })
        } catch (e: Exception) {
            Timber.e(e, "Error quitando progreso de lectura")
        }
    }

    private fun persist(context: Context, list: List<ReadingProgress>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        list.forEach { p ->
            array.put(
                JSONObject().apply {
                    put("uri", p.uri)
                    put("name", p.documentName)
                    put("paragraphIndex", p.paragraphIndex)
                    put("totalParagraphs", p.totalParagraphs)
                    put("currentPage", p.currentPage)
                    put("totalPages", p.totalPages)
                    put("lastReadAt", p.lastReadAtMillis)
                }
            )
        }
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun releasePermission(context: Context, uriString: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Timber.w(e, "No se pudo liberar el permiso persistente de $uriString")
        }
    }
}

/** Página (1-based) a la que pertenece el párrafo [paragraphIndex], según los
 *  límites acumulados por página que arma `extractPdfText`. Sin límites
 *  (extracción falló, o documento de una sola página sin cortes) devuelve 1. */
internal fun pageForParagraph(paragraphIndex: Int, pageBoundaries: List<Int>): Int {
    if (pageBoundaries.isEmpty()) return 1
    val page = pageBoundaries.indexOfFirst { paragraphIndex < it }
    return if (page == -1) pageBoundaries.size else page + 1
}
