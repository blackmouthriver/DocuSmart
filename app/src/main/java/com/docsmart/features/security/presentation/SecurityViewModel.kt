package com.docsmart.features.security.presentation

import android.content.Context
import android.net.Uri
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.security.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

enum class SecurityScreenState {
    LOCKED, SETUP_PIN, UNLOCKED
}

data class SecurityUiState(
    val screenState: SecurityScreenState = SecurityScreenState.LOCKED,
    val hasPin: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val secureFiles: List<File> = emptyList(),
    val appFiles: List<File> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    init { loadInitialState() }

    private fun loadInitialState() {
        _uiState.update {
            it.copy(
                hasPin = securityManager.hasPin(),
                isBiometricAvailable = securityManager.isBiometricAvailable(),
                isBiometricEnabled = securityManager.isBiometricEnabled(),
                screenState = SecurityScreenState.LOCKED
            )
        }
    }

    fun verifyPin(pin: String) {
        if (securityManager.verifyPin(pin)) {
            unlockAndLoadFiles()
        } else {
            _uiState.update { it.copy(error = "PIN incorrecto") }
        }
    }

    fun setupPin(pin: String) {
        val success = securityManager.setPin(pin)
        if (success) {
            _uiState.update { it.copy(hasPin = true) }
            unlockAndLoadFiles()
        }
    }

    fun authenticateWithBiometric(activity: FragmentActivity) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    Timber.d("Biometría exitosa")
                    unlockAndLoadFiles()
                }
                override fun onAuthenticationError(
                    errorCode: Int, errString: CharSequence
                ) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        _uiState.update { it.copy(error = "Error: $errString") }
                    }
                }
                override fun onAuthenticationFailed() {
                    _uiState.update { it.copy(error = "No se reconoció la biometría") }
                }
            }
            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("DocuSmart — Carpeta Segura")
                .setSubtitle("Usa tu huella o cara para acceder")
                .setNegativeButtonText("Usar PIN")
                .build()
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Timber.e(e, "Error biometría: ${e.message}")
            _uiState.update { it.copy(error = "Error: ${e.message}") }
        }
    }

    private fun unlockAndLoadFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val secureFiles = securityManager.getSecureFiles()
            val appFiles = loadAppFiles()
            _uiState.update {
                it.copy(
                    screenState = SecurityScreenState.UNLOCKED,
                    secureFiles = secureFiles,
                    appFiles = appFiles,
                    error = null
                )
            }
        }
    }

    private fun loadAppFiles(): List<File> {
        val dirs = listOf(
            File(securityManager.secureFolder.parentFile, "converted"),
            File(securityManager.secureFolder.parentFile, "pdftools")
        )
        return dirs.flatMap { dir ->
            dir.listFiles()?.filter { it.exists() && it.length() > 0 } ?: emptyList()
        }.sortedByDescending { it.lastModified() }
    }

    fun importLocalFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dest = File(securityManager.secureFolder, file.name)
                file.copyTo(dest, overwrite = true)
                Timber.d("Archivo local importado: ${file.name}")
                val secureFiles = securityManager.getSecureFiles()
                val appFiles = loadAppFiles()
                _uiState.update {
                    it.copy(
                        secureFiles = secureFiles,
                        appFiles = appFiles,
                        successMessage = "Archivo protegido correctamente"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error importando archivo local")
                _uiState.update { it.copy(error = "No se pudo proteger el archivo") }
            }
        }
    }
    // ── Importar archivo desde URI a carpeta segura ───
    fun importFileToSecure(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Obtener nombre del archivo
                val fileName = resolveFileName(context, uri)
                val destFile = File(securityManager.secureFolder, fileName)

                // Copiar al almacenamiento seguro
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                Timber.d("Archivo importado a carpeta segura: $fileName")
                val files = securityManager.getSecureFiles()
                _uiState.update {
                    it.copy(
                        secureFiles = files,
                        successMessage = "Archivo protegido correctamente"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error importando archivo: ${e.message}")
                _uiState.update { it.copy(error = "No se pudo proteger el archivo") }
            }
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        return try {
            var name = "archivo_seguro_${System.currentTimeMillis()}"
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                }
            }
            name
        } catch (e: Exception) {
            "archivo_seguro_${System.currentTimeMillis()}"
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            securityManager.deleteSecureFile(file)
            val files = securityManager.getSecureFiles()
            _uiState.update { it.copy(secureFiles = files) }
        }
    }

    fun restoreFile(file: File, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val destDir = File(context.filesDir, "converted")
            securityManager.moveFromSecure(file, destDir)
            val files = securityManager.getSecureFiles()
            _uiState.update { it.copy(secureFiles = files) }
        }
    }

    fun toggleBiometric() {
        val newValue = !_uiState.value.isBiometricEnabled
        securityManager.setBiometricEnabled(newValue)
        _uiState.update { it.copy(isBiometricEnabled = newValue) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun goToSetupPin() {
        _uiState.update { it.copy(screenState = SecurityScreenState.SETUP_PIN) }
    }

    fun goToLocked() {
        _uiState.update { it.copy(screenState = SecurityScreenState.LOCKED, error = null) }
    }

    fun reloadFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = securityManager.getSecureFiles()
            _uiState.update { it.copy(secureFiles = files) }
        }
    }
}