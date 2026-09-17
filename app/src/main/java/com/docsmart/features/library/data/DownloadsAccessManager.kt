package com.docsmart.features.library.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vínculo persistente a la carpeta Descargas vía Storage Access Framework
 * (fila 22 del backlog UX, `backlog-mejoras-ux-2026-08-30.md` §16-17):
 * en Android 13+ no existe ningún permiso equivalente a `READ_MEDIA_IMAGES`
 * para ver documentos (PDF/Word/Excel/PowerPoint/Texto) que otras apps
 * dejaron en Descargas -- alternativa recomendada por Google frente a
 * `MANAGE_EXTERNAL_STORAGE` (acceso a todos los archivos, con revisión
 * especial de Play Console y mayor fricción/desconfianza del usuario):
 * que el usuario vincule la carpeta una vez con el selector nativo de
 * Android (`ACTION_OPEN_DOCUMENT_TREE`) y la app recuerde ese permiso.
 */
@Singleton
class DownloadsAccessManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(
        "docusmart_downloads_access", Context.MODE_PRIVATE
    )

    private val _linkedFolderUri = MutableStateFlow(loadLinkedFolderUri())
    val linkedFolderUri: StateFlow<Uri?> = _linkedFolderUri.asStateFlow()

    /** Hint de carpeta inicial para el selector -- intenta abrir directo en
     *  Descargas del almacenamiento principal. No es una API oficial
     *  documentada, pero es el mecanismo estándar usado para esto; si el
     *  proveedor de almacenamiento del dispositivo no lo soporta, Android
     *  simplemente ignora el hint y abre su carpeta por defecto. */
    fun initialUriHint(): Uri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:Download"
    )

    // Hallazgo real de la auditoría general 2026-09-17 (M3): si
    // takePersistableUriPermission() lanza (proveedor SAF que no lo
    // soporta, revocación concurrente), antes solo se logueaba -- el
    // usuario tocaba "Vincular carpeta", elegía una carpeta, y no pasaba
    // nada visible, sin ningún aviso de que falló. Ahora se propaga el
    // resultado para que el llamador pueda avisar, mismo criterio que el
    // resto de operaciones de Carpeta Segura (moveToSecure/moveFromSecure).
    fun onFolderPicked(uri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            prefs.edit().putString(KEY_URI, uri.toString()).apply()
            _linkedFolderUri.value = uri
            Timber.d("DownloadsAccessManager: carpeta vinculada -> $uri")
            true
        } catch (e: SecurityException) {
            Timber.e(e, "DownloadsAccessManager: no se pudo persistir el permiso de $uri")
            false
        }
    }

    // Nombre real de la carpeta que el usuario eligió (ej. "DMSS"), para
    // mostrarlo en el atajo de Biblioteca en vez de un genérico "Carpeta" --
    // pedido explícito del usuario 2026-09-03.
    fun folderDisplayName(uri: Uri): String? = DocumentFile.fromTreeUri(context, uri)?.name

    fun unlink() {
        val uri = _linkedFolderUri.value ?: return
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Timber.w(e, "DownloadsAccessManager: no se pudo liberar el permiso de $uri")
        }
        prefs.edit().remove(KEY_URI).apply()
        _linkedFolderUri.value = null
    }

    // El usuario puede revocar el permiso desde Ajustes del sistema sin que
    // la app se entere -- se valida contra la lista real de permisos
    // persistidos, no solo lo que quedó guardado en SharedPreferences.
    private fun loadLinkedFolderUri(): Uri? {
        val saved = prefs.getString(KEY_URI, null) ?: return null
        val savedUri = Uri.parse(saved)
        val stillGranted = context.contentResolver.persistedUriPermissions
            .any { it.uri == savedUri && it.isReadPermission }
        if (!stillGranted) prefs.edit().remove(KEY_URI).apply()
        return if (stillGranted) savedUri else null
    }

    private companion object {
        const val KEY_URI = "linked_folder_uri"
    }
}
