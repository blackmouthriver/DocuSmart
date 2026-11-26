package com.docsmart.features.utilities.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.features.utilities.domain.HashAlgorithm
import com.docsmart.features.utilities.domain.HashResult
import com.docsmart.features.utilities.domain.HashTool
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

data class HashUiState(
    val files: List<PickedFile> = emptyList(),
    val algorithm: HashAlgorithm = HashAlgorithm.SHA256,
    val results: List<HashResult> = emptyList(),
    val running: Boolean = false,
    /** Archivo que se está calculando (1 a total). */
    val current: Int = 0,
    val unreadable: Boolean = false,
    val expected: String = "",
)

@HiltViewModel
class HashViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HashUiState())
        val state: StateFlow<HashUiState> = _state.asStateFlow()
        private var job: Job? = null

        fun onFilesPicked(uris: List<Uri>) {
            if (uris.isEmpty()) return
            _state.update { it.copy(files = uris.map { uri -> queryPickedFile(context, uri) }) }
            compute()
        }

        fun setAlgorithm(algorithm: HashAlgorithm) {
            if (algorithm == _state.value.algorithm) return
            _state.update { it.copy(algorithm = algorithm) }
            compute()
        }

        /** Lo pegado se compara tal cual; si por su largo se reconoce otro algoritmo, se cambia a ese. */
        fun setExpected(text: String) {
            _state.update { it.copy(expected = text) }
            HashTool.detectAlgorithm(text)?.let { setAlgorithm(it) }
        }

        fun clear() {
            job?.cancel()
            _state.value = HashUiState()
        }

        private fun compute() {
            job?.cancel()
            val files = _state.value.files
            val algorithm = _state.value.algorithm
            if (files.isEmpty()) return
            job =
                viewModelScope.launch {
                    _state.update { it.copy(running = true, unreadable = false, results = emptyList(), current = 0) }
                    val results = ArrayList<HashResult>()
                    var failed = false
                    for ((index, file) in files.withIndex()) {
                        _state.update { it.copy(current = index + 1) }
                        val hash = hashOf(file, algorithm)
                        if (hash == null) failed = true else results.add(HashResult(file.name, file.size, hash))
                    }
                    _state.update { it.copy(running = false, results = results, unreadable = failed) }
                }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun hashOf(
            file: PickedFile,
            algorithm: HashAlgorithm,
        ): String? =
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { HashTool.digest(it, algorithm) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Timber.w("HashViewModel: ${e.javaClass.simpleName} al leer un archivo")
                    null
                } catch (e: SecurityException) {
                    Timber.w("HashViewModel: ${e.javaClass.simpleName} al leer un archivo")
                    null
                }
            }
    }
