package com.docsmart.features.settings.presentation
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.features.library.data.DownloadsAccessManager
import com.docsmart.features.library.data.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val adManager: AdManager,
    private val downloadsAccessManager: DownloadsAccessManager,
    private val trashRepository: TrashRepository
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
}
