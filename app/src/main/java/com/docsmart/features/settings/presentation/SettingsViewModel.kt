package com.docsmart.features.settings.presentation
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.docsmart.core.ads.AdManager
import com.docsmart.features.library.data.DownloadsAccessManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val adManager: AdManager,
    private val downloadsAccessManager: DownloadsAccessManager
) : ViewModel() {

    // Fila 22 del backlog UX: permite ver y desvincular, desde Ajustes, la
    // carpeta de Descargas vinculada por SAF (ver DownloadsAccessManager).
    val linkedDownloadsFolderUri: StateFlow<Uri?> = downloadsAccessManager.linkedFolderUri

    fun downloadsFolderPickerInitialUri(): Uri? = downloadsAccessManager.initialUriHint()

    fun onDownloadsFolderPicked(uri: Uri) = downloadsAccessManager.onFolderPicked(uri)

    fun unlinkDownloadsFolder() = downloadsAccessManager.unlink()
}
