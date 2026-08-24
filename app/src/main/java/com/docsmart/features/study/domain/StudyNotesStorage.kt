package com.docsmart.features.study.domain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

data class SavedNote(
    val id: String,
    val title: String,
    val text: String,
    val dateTime: String
)

/**
 * Persiste las notas de Modo Estudio en SharedPreferences como JSON real
 * (`org.json`, ya parte del SDK de Android, sin dependencia nueva).
 *
 * Antes se armaba el JSON a mano con `.replace()` y se parseaba con un split
 * sobre el literal `"},{"` — eso reemplazaba en silencio cualquier comilla
 * doble que el usuario escribiera en su nota por una comilla simple en cada
 * guardado (corrupción de datos), y aplicaba un `.reversed()` de más en la
 * carga que invertía el orden real de las notas (quedaban más viejo primero
 * en vez de más nuevo primero, aunque en la misma sesión sí se veían bien).
 */
object StudyNotesStorage {
    private const val PREFS_NAME = "study_notes"
    private const val KEY_NOTES_LIST = "notes_list"

    // Un JSON corrupto o inesperado en las preferencias debe verse igual para
    // quien llama: lista vacía, no un crash de toda la pantalla de Estudio.
    @Suppress("TooGenericExceptionCaught")
    fun loadNotes(context: Context): List<SavedNote> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_NOTES_LIST, "[]") ?: "[]"
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                SavedNote(
                    id = obj.getString("id"),
                    title = obj.optString("title", ""),
                    text = obj.getString("text"),
                    dateTime = obj.optString("date", "")
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cargando notas de estudio")
            emptyList()
        }
    }

    // Un fallo guardando no debe crashear la pantalla — la nota simplemente
    // no queda persistida y se registra en el log.
    @Suppress("TooGenericExceptionCaught")
    fun saveNotes(context: Context, notes: List<SavedNote>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            notes.forEach { note ->
                array.put(
                    JSONObject().apply {
                        put("id", note.id)
                        put("title", note.title)
                        put("text", note.text)
                        put("date", note.dateTime)
                    }
                )
            }
            prefs.edit().putString(KEY_NOTES_LIST, array.toString()).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error guardando notas de estudio")
        }
    }
}
