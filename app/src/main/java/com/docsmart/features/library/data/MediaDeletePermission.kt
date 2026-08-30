package com.docsmart.features.library.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @RequiresApi(Build.VERSION_CODES.R)
    fun createBulkDeleteRequest(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
    }
}
