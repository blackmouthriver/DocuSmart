package com.docsmart.features.onboarding.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.docsmart.features.library.data.DownloadsAccessManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// Permite vincular una carpeta (fila 22 del backlog UX) desde el propio
// onboarding, no solo desde el banner de Biblioteca -- pedido explícito
// del usuario 2026-09-03 para reducir la fricción de descubrir la función.
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val downloadsAccessManager: DownloadsAccessManager
) : ViewModel() {

    val linkedFolderUri: StateFlow<Uri?> = downloadsAccessManager.linkedFolderUri

    fun downloadsFolderPickerInitialUri(): Uri = downloadsAccessManager.initialUriHint()

    fun linkedFolderDisplayName(uri: Uri): String? = downloadsAccessManager.folderDisplayName(uri)

    fun onDownloadsFolderPicked(uri: Uri) = downloadsAccessManager.onFolderPicked(uri)
}
