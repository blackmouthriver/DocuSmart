package com.docsmart.features.settings.presentation
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.core.media.SoundEffectPlayer
import com.docsmart.core.security.SecurityManager
import com.docsmart.features.library.data.DownloadsAccessManager
import com.docsmart.features.library.data.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val adManager: AdManager,
    val soundEffectPlayer: SoundEffectPlayer,
    private val downloadsAccessManager: DownloadsAccessManager,
    private val trashRepository: TrashRepository,
    private val securityManager: SecurityManager
) : ViewModel() {

    // Fila 22 del backlog UX: permite ver y desvincular, desde Ajustes, la
    // carpeta de Descargas vinculada por SAF (ver DownloadsAccessManager).
    val linkedDownloadsFolderUri: StateFlow<Uri?> = downloadsAccessManager.linkedFolderUri

    fun downloadsFolderPickerInitialUri(): Uri? = downloadsAccessManager.initialUriHint()

    fun onDownloadsFolderPicked(uri: Uri) = downloadsAccessManager.onFolderPicked(uri)

    fun unlinkDownloadsFolder() = downloadsAccessManager.unlink()

    // Bug real corregido (2026-09-06): "Limpiar caché" borraba estos archivos
    // de forma definitiva con `File.delete()`, sin pasar por la Papelera --
    // a diferencia de cualquier otro borrado de documento en la app
    // (RF-VIS-07), sin período de gracia ni forma de recuperarlos. Ahora usa
    // el mismo `TrashRepository.moveToTrash()` que ya usan Biblioteca/Home/
    // Visor: el archivo real no se toca, solo se oculta y queda disponible
    // 30 días en la Papelera.
    fun moveConvertedFilesToTrash(documentIds: List<String>) {
        viewModelScope.launch {
            documentIds.forEach { trashRepository.moveToTrash(it) }
        }
    }

    // Hallazgo real de la auditoría general 2026-09-17 (séptima ronda,
    // Media -- S3): "Restablecer configuración" cambiaba el idioma ANTES
    // de esperar a que este traspaso terminara -- si el idioma del
    // dispositivo difiere del activo, MainActivity reinicia la Activity
    // casi de inmediato al detectar el cambio, cancelando
    // `viewModelScope` (y con él este `launch` de arriba) a mitad del
    // `forEach`. Variante awaitable para que el llamador pueda esperar a
    // que termine de verdad antes de disparar el cambio de idioma.
    suspend fun moveConvertedFilesToTrashAwait(documentIds: List<String>) {
        documentIds.forEach { trashRepository.moveToTrash(it) }
    }

    // Hallazgo real de la auditoría general 2026-09-17 (sexta ronda,
    // Media -- S3): a diferencia de los archivos de arriba, la copia
    // efímera de vista previa de Carpeta Segura (cacheDir/secure_preview/)
    // nunca pasa por Papelera -- es una copia sin cifrar que solo debe
    // existir mientras dura la vista previa, así que "Limpiar caché" debe
    // borrarla directo, igual que clearPreviewCache() ya hace al crear la
    // siguiente copia o al bloquear Carpeta Segura.
    fun clearSecurePreviewCache() {
        securityManager.clearPreviewCache()
    }
}
