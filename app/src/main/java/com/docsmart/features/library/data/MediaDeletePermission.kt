package com.docsmart.features.library.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resuelve el `IntentSender` que Android exige para borrar filas de
 * MediaStore que la app no creó (scoped storage, API 29+) -- extraída de
 * `DocumentRepository` para no superar el umbral `TooManyFunctions` de
 * detekt, mismo criterio ya usado antes para separar `TrashRepository` y
 * `FavoritesRepository`.
 *
 * Cada función queda aislada con `@RequiresApi` y solo se invoca tras un
 * chequeo `Build.VERSION.SDK_INT` explícito en el llamador -- referenciar
 * `RecoverableSecurityException`/`MediaStore.createDeleteRequest()` sin este
 * aislamiento puede fallar la verificación de ART en API < 29/30, donde esas
 * clases/métodos no existen en el framework.
 */
@Singleton
class MediaDeletePermission @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @RequiresApi(Build.VERSION_CODES.Q)
    fun recoverableIntentSenderOrNull(e: Exception): IntentSender? =
        (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender

    /**
     * Bug real (2026-09-12, verificado en dispositivo): un Uri traído desde
     * Carpeta Segura vía el picker genérico de Android puede venir con la
     * forma `content://media/external/file/<id>` (la tabla genérica "Files"
     * de MediaStore) en vez de la forma tipada `.../images/media/<id>` que
     * `MediaStore.createDeleteRequest()` exige para imagen/video/audio -- si
     * no es un Uri tipado, esa llamada lanza `IllegalArgumentException: All
     * requested items must be Media items`. Se reintenta con el Uri
     * reescrito a su tabla real antes de pedir el borrado.
     *
     * Límite real de Android descubierto al verificar este fix (no hay forma
     * de evitarlo reescribiendo el Uri): `MediaStore.createDeleteRequest()`
     * SOLO acepta filas cuyo `MEDIA_TYPE` sea imagen/video/audio -- un
     * documento genérico (PDF/Word, `MEDIA_TYPE_DOCUMENT`) lo rechaza pase lo
     * que pase, ya sea con el Uri genérico ("must be Media items") o con
     * cualquier Uri tipado que se le arme ("must be referenced by specific
     * ID"). Por eso el segundo intento también se protege: si igual falla,
     * se retorna null (el llamador ya trata `null` como fallo, no crash) en
     * vez de dejar escapar la excepción.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createBulkDeleteRequest(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "createDeleteRequest rechazó los Uri sin tipar, se reintenta tipados: $uris")
            retryWithTypedUris(uris)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun retryWithTypedUris(uris: List<Uri>): IntentSender? {
        val typedUris = uris.map { typedMediaUriOrSelf(it) }
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, typedUris).intentSender
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "createDeleteRequest también rechazó los Uri tipados (no son media): $typedUris")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun typedMediaUriOrSelf(uri: Uri): Uri {
        val id = idOrNull(uri)
        return if (uri.authority != MediaStore.AUTHORITY || id == null) uri else typedUriForId(uri, id)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun typedUriForId(uri: Uri, id: Long): Uri {
        val mediaType = context.contentResolver.query(
            uri, arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE), null, null, null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else null }
        val collection = when (mediaType) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> return uri
        }
        return ContentUris.withAppendedId(collection, id)
    }

    private fun idOrNull(uri: Uri): Long? = try {
        ContentUris.parseId(uri)
    } catch (e: UnsupportedOperationException) {
        Timber.w(e, "Uri de MediaStore sin id numérico, se usa sin tipar: $uri")
        null
    }
}
