package com.docsmart.features.library.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.docsmart.core.data.db.DocumentHistoryDao
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.FileNotFoundException

/** Resultado crudo de consultar el Uri de un documento del historial. */
internal sealed interface HistoryQueryOutcome {
    data object Row : HistoryQueryOutcome

    data object EmptyCursor : HistoryQueryOutcome

    data object NullCursor : HistoryQueryOutcome

    data class Failed(
        val error: Throwable,
    ) : HistoryQueryOutcome
}

/**
 * Qué se concluye de una consulta fallida al historial.
 *
 * - [ALIVE]: el documento existe.
 * - [GONE]: ya no existe (o ya no hay forma de volver a leerlo) y su fila del
 *   historial puede borrarse.
 * - [UNKNOWN]: no se puede afirmar nada (proveedor ausente, error transitorio,
 *   sin conexión...). Nunca se borra nada.
 */
internal enum class HistoryProbe { ALIVE, GONE, UNKNOWN }

/**
 * Limpieza del historial de documentos que ya no existen (backlog #25, 1.1.6).
 *
 * Un documento agregado con el selector de archivos/fotos o abierto desde otra
 * app vive en `document_history` y la Biblioteca lo lista consultando su Uri. Si
 * el usuario lo borra o lo mueve desde otra app, esa consulta falla y la
 * Biblioteca simplemente lo omite -- pero la fila quedaba para siempre y se
 * volvía a consultar en cada carga. Medido en un Edge 30 Neo (Android 14): el
 * proveedor de medios responde con `SecurityException` y Android ya revoca
 * por su cuenta el permiso persistente de una fila de medios borrada.
 *
 * El criterio es deliberadamente conservador, porque borrar una fila de más
 * hace perder un documento que el usuario sí tiene (p. ej. de Drive sin red):
 *
 * - `FileNotFoundException`: el proveedor dice que no existe -> GONE.
 * - Cursor vacío con permiso persistente nuestro: no existe -> GONE.
 * - `SecurityException` SIN permiso persistente: hoy no hay forma de leerlo
 *   (el permiso temporal de un "Abrir con" ya venció o se revocó) -> GONE.
 * - `SecurityException` CON permiso persistente, cursor nulo (proveedor
 *   ausente) o cualquier otro error -> UNKNOWN, no se toca.
 *
 * Solo aplica a Uri `content://`: una ruta de archivo puede parecer inexistente
 * por restricciones de almacenamiento sin que el archivo falte.
 */
internal object HistoryLiveness {
    fun classify(
        outcome: HistoryQueryOutcome,
        hasPersistedGrant: Boolean,
    ): HistoryProbe =
        when (outcome) {
            HistoryQueryOutcome.Row -> HistoryProbe.ALIVE
            HistoryQueryOutcome.NullCursor -> HistoryProbe.UNKNOWN
            HistoryQueryOutcome.EmptyCursor -> if (hasPersistedGrant) HistoryProbe.GONE else HistoryProbe.UNKNOWN
            is HistoryQueryOutcome.Failed -> classifyFailure(outcome.error, hasPersistedGrant)
        }

    private fun classifyFailure(
        error: Throwable,
        hasPersistedGrant: Boolean,
    ): HistoryProbe =
        when {
            error is FileNotFoundException -> HistoryProbe.GONE
            error is SecurityException && !hasPersistedGrant -> HistoryProbe.GONE
            else -> HistoryProbe.UNKNOWN
        }

    /**
     * Si el documento del historial [documentId] ya no existe, quita su fila y
     * libera el permiso persistente que aun conserve. Devuelve `true` si lo
     * limpio. Una ruta de archivo (no `content://`) nunca se toca.
     */
    suspend fun pruneIfGone(
        context: Context,
        dao: DocumentHistoryDao,
        documentId: String,
    ): Boolean {
        if (!documentId.startsWith("content://")) return false
        val resolver = context.contentResolver
        val uri = Uri.parse(documentId)
        val grant = resolver.persistedUriPermissions.firstOrNull { it.uri.toString() == documentId }
        val gone = probe(resolver, uri, hasPersistedGrant = grant != null) == HistoryProbe.GONE
        if (gone) {
            if (grant != null) release(resolver, uri, grant.isReadPermission, grant.isWritePermission)
            dao.remove(documentId)
            Timber.d("Historial: se quito un documento que ya no existe")
        }
        return gone
    }

    @Suppress("TooGenericExceptionCaught")
    private fun probe(
        resolver: ContentResolver,
        uri: Uri,
        hasPersistedGrant: Boolean,
    ): HistoryProbe =
        try {
            classify(queryOutcome(resolver, uri), hasPersistedGrant)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("probe: no se pudo clasificar un documento (${e.javaClass.simpleName})")
            HistoryProbe.UNKNOWN
        }

    @Suppress("TooGenericExceptionCaught")
    private fun queryOutcome(
        resolver: ContentResolver,
        uri: Uri,
    ): HistoryQueryOutcome =
        try {
            cursorOutcome(resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null))
        } catch (e: Exception) {
            HistoryQueryOutcome.Failed(e)
        }

    private fun cursorOutcome(cursor: Cursor?): HistoryQueryOutcome =
        when {
            cursor == null -> HistoryQueryOutcome.NullCursor
            else -> cursor.use { if (it.moveToFirst()) HistoryQueryOutcome.Row else HistoryQueryOutcome.EmptyCursor }
        }

    private fun release(
        resolver: ContentResolver,
        uri: Uri,
        read: Boolean,
        write: Boolean,
    ) {
        val flags =
            (if (read) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        if (flags == 0) return
        try {
            resolver.releasePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            Timber.w("release: sin permiso que liberar (${e.javaClass.simpleName})")
        }
    }
}
