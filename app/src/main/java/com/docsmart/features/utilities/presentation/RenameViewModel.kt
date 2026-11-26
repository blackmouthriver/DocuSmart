package com.docsmart.features.utilities.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.R
import com.docsmart.core.util.DownloadsSaver
import com.docsmart.features.utilities.domain.RenameBatch
import com.docsmart.features.utilities.domain.RenameOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class RenameUiState(
    val files: List<PickedFile> = emptyList(),
    val options: RenameOptions = RenameOptions(),
    val working: Boolean = false,
    /** Mensaje de una sola vez (guardado o error) para mostrar como aviso. */
    val messageRes: Int? = null,
) {
    val newNames: List<String> get() = RenameBatch.renameAll(files.map { it.name }, options)
}

@HiltViewModel
class RenameViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RenameUiState())
        val state: StateFlow<RenameUiState> = _state.asStateFlow()

        fun onFilesPicked(uris: List<Uri>) {
            if (uris.isEmpty()) return
            _state.update { it.copy(files = uris.map { uri -> queryPickedFile(context, uri) }) }
        }

        fun setOptions(options: RenameOptions) = _state.update { it.copy(options = options) }

        fun consumeMessage() = _state.update { it.copy(messageRes = null) }

        fun clear() {
            _state.value = RenameUiState()
        }

        /** Copia los archivos con los nombres nuevos a un ZIP en Descargas; los originales no se tocan. */
        fun saveZip() {
            val current = _state.value
            if (current.working || current.files.isEmpty()) return
            _state.update { it.copy(working = true) }
            viewModelScope.launch {
                val saved = writeZip(current.files, current.newNames)
                val message = if (saved) R.string.util_saved_downloads else R.string.util_save_failed
                _state.update { it.copy(working = false, messageRes = message) }
            }
        }

        private suspend fun writeZip(
            files: List<PickedFile>,
            names: List<String>,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val zip = File(context.cacheDir, "rename_${System.currentTimeMillis()}.zip")
                try {
                    ZipOutputStream(zip.outputStream().buffered()).use { out ->
                        files.zip(names).forEach { (file, name) ->
                            out.putNextEntry(ZipEntry(name))
                            val input =
                                context.contentResolver.openInputStream(file.uri) ?: throw IOException("sin flujo")
                            input.use { it.copyTo(out) }
                            out.closeEntry()
                        }
                    }
                    DownloadsSaver.saveFile(context, zip, "application/zip", ZIP_NAME)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Timber.w("RenameViewModel: ${e.javaClass.simpleName} al crear el ZIP")
                    false
                } catch (e: SecurityException) {
                    Timber.w("RenameViewModel: ${e.javaClass.simpleName} al crear el ZIP")
                    false
                } finally {
                    zip.delete()
                }
            }

        private companion object {
            const val ZIP_NAME = "archivos-renombrados.zip"
        }
    }
