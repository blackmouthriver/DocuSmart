package com.docsmart.features.security.presentation

import android.content.Context
import android.net.Uri
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.security.SecurityManager
import com.docsmart.features.security.domain.PdfPasswordMessages
import com.docsmart.features.security.domain.PdfPasswordResult
import com.docsmart.features.security.domain.PdfPasswordUseCase
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

enum class SecurityScreenState { LOCKED, SETUP_PIN, UNLOCKED }

// ── Resultado de operación PDF password ───────────────────────────────────────
enum class PdfPasswordMode { PROTECT, REMOVE }

data class SecurityUiState(
    val screenState          : SecurityScreenState = SecurityScreenState.LOCKED,
    val hasPin               : Boolean             = false,
    val isBiometricAvailable : Boolean             = false,
    val isBiometricEnabled   : Boolean             = false,
    val secureFiles          : List<File>          = emptyList(),
    val appFiles             : List<File>          = emptyList(),
    val error                : String?             = null,
    val successMessage       : String?             = null,
    // ── PDF Password ──────────────────────────────────────────────────────────
    val pdfPasswordMode      : PdfPasswordMode?    = null,
    val isPdfProcessing      : Boolean             = false,
    val pdfOutputFile        : File?               = null,
    val pdfPasswordError     : String?             = null
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val securityManager    : SecurityManager,
    private val pdfPasswordUseCase : PdfPasswordUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    init { loadInitialState() }

    private fun loadInitialState() {
        _uiState.update {
            it.copy(
                hasPin               = securityManager.hasPin(),
                isBiometricAvailable = securityManager.isBiometricAvailable(),
                isBiometricEnabled   = securityManager.isBiometricEnabled(),
                screenState          = SecurityScreenState.LOCKED
            )
        }
    }

    fun verifyPin(pin: String, incorrectPinMessage: String) {
        if (securityManager.verifyPin(pin)) {
            unlockAndLoadFiles()
        } else {
            _uiState.update { it.copy(error = incorrectPinMessage) }
        }
    }

    fun setupPin(pin: String) {
        val success = securityManager.setPin(pin)
        if (success) {
            _uiState.update { it.copy(hasPin = true) }
            unlockAndLoadFiles()
        }
    }

    fun authenticateWithBiometric(
        activity            : FragmentActivity,
        promptTitle         : String,
        promptSubtitle      : String,
        usePinLabel         : String,
        errorTemplate       : String, // formato: %1$s
        notRecognizedMessage: String
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Timber.d("Biometría exitosa")
                    unlockAndLoadFiles()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        _uiState.update { it.copy(error = String.format(errorTemplate, errString)) }
                    }
                }
                override fun onAuthenticationFailed() {
                    _uiState.update { it.copy(error = notRecognizedMessage) }
                }
            }
            val prompt     = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle(promptSubtitle)
                .setNegativeButtonText(usePinLabel)
                .build()
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Timber.e(e, "Error biometría: ${e.message}")
            _uiState.update { it.copy(error = String.format(errorTemplate, e.message ?: "")) }
        }
    }

    private fun unlockAndLoadFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val secureFiles = securityManager.getSecureFiles()
            val appFiles    = loadAppFiles()
            _uiState.update {
                it.copy(
                    screenState = SecurityScreenState.UNLOCKED,
                    secureFiles = secureFiles,
                    appFiles    = appFiles,
                    error       = null
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

    // RF-SEC-05: proteger un archivo debe copiarlo a la carpeta segura Y eliminar
    // el original de su ubicación — moveToSecure() ya hace ambas cosas. RNF-SEC-01:
    // si el original no se pudo eliminar, se avisa en vez de reportar éxito pleno
    // (mismo patrón que importFileToSecure() para Uris de SAF).
    fun importLocalFile(
        file: File, successMessage: String, errorMessage: String, originalKeptMessage: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = securityManager.moveToSecure(file)
            if (result.success) {
                val secureFiles = securityManager.getSecureFiles()
                val appFiles    = loadAppFiles()
                _uiState.update {
                    it.copy(
                        secureFiles    = secureFiles,
                        appFiles       = appFiles,
                        successMessage = if (result.originalDeleted) successMessage else originalKeptMessage
                    )
                }
            } else {
                _uiState.update { it.copy(error = errorMessage) }
            }
        }
    }

    // RNF-SEC-01: para un Uri de SAF el borrado del original solo es posible si el
    // proveedor de almacenamiento lo permite — se intenta y se avisa si no se pudo,
    // en vez de fallar en silencio o prometer un borrado que no ocurrió.
    fun importFileToSecure(
        context: Context, uri: Uri,
        successMessage: String, errorMessage: String, originalKeptMessage: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = resolveFileName(context, uri)
                val destFile = File(securityManager.secureFolder, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                val originalDeleted = try {
                    android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
                } catch (e: Exception) {
                    Timber.w(e, "No se pudo eliminar el archivo original tras protegerlo: $uri")
                    false
                }
                val files = securityManager.getSecureFiles()
                _uiState.update {
                    it.copy(
                        secureFiles    = files,
                        successMessage = if (originalDeleted) successMessage else originalKeptMessage
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error importando archivo: ${e.message}")
                _uiState.update { it.copy(error = errorMessage) }
            }
        }
    }

    // ── PDF Password: proteger ────────────────────────────────────────────────
    fun protectPdfWithPassword(
        context: Context, uri: Uri, password: String, fileName: String,
        messages: PdfPasswordMessages, wrongPasswordMessage: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPdfProcessing = true, pdfPasswordError = null, pdfOutputFile = null) }
            val result = pdfPasswordUseCase.protect(context, uri, password, fileName, messages)
            _uiState.update { state ->
                when (result) {
                    is PdfPasswordResult.Success -> state.copy(
                        isPdfProcessing = false,
                        pdfOutputFile   = result.outputFile,
                        successMessage  = result.message
                    )
                    is PdfPasswordResult.Error -> state.copy(
                        isPdfProcessing  = false,
                        pdfPasswordError = result.message
                    )
                    PdfPasswordResult.WrongPassword -> state.copy(
                        isPdfProcessing  = false,
                        pdfPasswordError = wrongPasswordMessage
                    )
                }
            }
        }
    }

    // ── PDF Password: quitar ──────────────────────────────────────────────────
    fun removePdfPassword(
        context: Context, uri: Uri, password: String, fileName: String,
        messages: PdfPasswordMessages, wrongPasswordMessage: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPdfProcessing = true, pdfPasswordError = null, pdfOutputFile = null) }
            val result = pdfPasswordUseCase.removePassword(context, uri, password, fileName, messages)
            _uiState.update { state ->
                when (result) {
                    is PdfPasswordResult.Success -> state.copy(
                        isPdfProcessing = false,
                        pdfOutputFile   = result.outputFile,
                        successMessage  = result.message
                    )
                    is PdfPasswordResult.Error -> state.copy(
                        isPdfProcessing  = false,
                        pdfPasswordError = result.message
                    )
                    PdfPasswordResult.WrongPassword -> state.copy(
                        isPdfProcessing  = false,
                        pdfPasswordError = wrongPasswordMessage
                    )
                }
            }
        }
    }

    fun setPdfPasswordMode(mode: PdfPasswordMode?) {
        _uiState.update { it.copy(pdfPasswordMode = mode, pdfPasswordError = null, pdfOutputFile = null) }
    }

    fun dismissPdfResult() {
        _uiState.update { it.copy(pdfOutputFile = null, pdfPasswordError = null) }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        return try {
            var name = "archivo_seguro_${System.currentTimeMillis()}"
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
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

    fun dismissSuccess() { _uiState.update { it.copy(successMessage = null) } }
    fun dismissError()   { _uiState.update { it.copy(error = null) } }
    fun goToSetupPin()   { _uiState.update { it.copy(screenState = SecurityScreenState.SETUP_PIN) } }
    fun goToLocked()     { _uiState.update { it.copy(screenState = SecurityScreenState.LOCKED, error = null) } }

    // RF-SEC-08: bloquear automáticamente la Carpeta Segura cuando la app pasa
    // a segundo plano. Solo actúa si está UNLOCKED a propósito -- si el
    // usuario está a mitad de configurar un PIN nuevo (SETUP_PIN) y recibe
    // una notificación, no queremos descartar ese flujo; los dígitos ya
    // tecleados viven en estado local del Composable, no acá, así que no
    // tocar screenState los preserva al volver.
    fun lockIfUnlocked() {
        if (_uiState.value.screenState == SecurityScreenState.UNLOCKED) {
            _uiState.update { it.copy(screenState = SecurityScreenState.LOCKED, error = null) }
        }
    }
    fun reloadFiles()    {
        viewModelScope.launch(Dispatchers.IO) {
            val files = securityManager.getSecureFiles()
            _uiState.update { it.copy(secureFiles = files) }
        }
    }
    // ── Guardar PDF resultado en Descargas ────────────────────────────────────
    fun savePdfToDownloads(
        context: Context, file: java.io.File,
        successTemplate: String, errorMessage: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, file.name)
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri      = resolver.insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                    )
                    uri?.let {
                        resolver.openOutputStream(it)?.use { output ->
                            file.inputStream().use { input -> input.copyTo(output) }
                        }
                        values.clear()
                        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(it, values, null, null)
                    }
                } else {
                    val dest = java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        ), file.name
                    )
                    file.copyTo(dest, overwrite = true)
                }
                _uiState.update { it.copy(successMessage = String.format(successTemplate, file.name)) }
            } catch (e: Exception) {
                Timber.e(e, "Error guardando en Descargas")
                _uiState.update { it.copy(error = errorMessage) }
            }
        }
    }
}