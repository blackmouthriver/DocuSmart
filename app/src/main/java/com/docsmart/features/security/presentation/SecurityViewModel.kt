package com.docsmart.features.security.presentation

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.security.SecurityManager
import com.docsmart.features.library.data.MediaDeletePermission
import com.docsmart.features.security.domain.PdfPasswordMessages
import com.docsmart.features.security.domain.PdfPasswordResult
import com.docsmart.features.security.domain.PdfPasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Pedido explícito del usuario 2026-09-12, tras verificar en dispositivo real
 * que `DocumentsContract.deleteDocument()` no está implementado para ciertos
 * proveedores (confirmado: `UnsupportedOperationException: Unsupported call:
 * android:deleteDocument` con un archivo de WhatsApp) -- el mismo problema
 * que ya se resolvió en `DocumentRepository`/`TrashViewModel` para Biblioteca:
 * `MediaStore.createDeleteRequest()` (API 30+) sí puede borrar esa fila, pero
 * exige mostrarle al usuario un diálogo de confirmación del sistema. La
 * Screen debe lanzar [intentSender] y avisar de vuelta con
 * [SecurityViewModel.onOriginalDeleteConfirmed].
 */
data class PendingOriginalDeleteRequest(val intentSender: IntentSender, val uri: Uri)

enum class SecurityScreenState { LOCKED, SETUP_PIN, UNLOCKED }

// ── Resultado de operación PDF password ───────────────────────────────────────
enum class PdfPasswordMode { PROTECT, REMOVE }

data class SecurityUiState(
    val screenState          : SecurityScreenState = SecurityScreenState.LOCKED,
    val hasPin               : Boolean             = false,
    val isBiometricAvailable : Boolean             = false,
    val isBiometricEnabled   : Boolean             = false,
    val secureFiles          : List<File>          = emptyList(),
    val error                : String?             = null,
    val successMessage       : String?             = null,
    // Pedido explícito del usuario 2026-09-12 (feedback de testers): antes
    // este aviso salía por el mismo Snackbar de éxito genérico -- pasaba
    // desapercibido, así que el usuario creía que "Carpeta Segura" no
    // funciona cuando en realidad Android le negó el borrado del original
    // (algunos proveedores de almacenamiento, ej. Google Fotos, no lo
    // permiten vía SAF). Se separa a un diálogo que exige confirmación
    // explícita en vez de un aviso que se puede perder.
    val originalNotDeletedWarning: String?         = null,
    // ── PDF Password ──────────────────────────────────────────────────────────
    val pdfPasswordMode      : PdfPasswordMode?    = null,
    val isPdfProcessing      : Boolean             = false,
    val pdfOutputFile        : File?               = null,
    val pdfPasswordError     : String?             = null
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val securityManager       : SecurityManager,
    private val pdfPasswordUseCase    : PdfPasswordUseCase,
    private val mediaDeletePermission : MediaDeletePermission
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    private val _pendingOriginalDelete = MutableSharedFlow<PendingOriginalDeleteRequest>(extraBufferCapacity = 1)
    val pendingOriginalDelete: SharedFlow<PendingOriginalDeleteRequest> = _pendingOriginalDelete.asSharedFlow()

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

    // Bug real encontrado 2026-09-14 (repaso general): el PIN no tenía
    // límite de intentos -- ahora SecurityManager bloquea tras varios
    // fallos seguidos (backoff creciente, ver pinLockoutRemainingMillis) y
    // acá se revisa ese bloqueo antes de intentar verificar, en vez de
    // dejar que verifyPin() lo rechace en silencio como "PIN incorrecto".
    fun verifyPin(pin: String, incorrectPinMessage: String, lockedOutMessageFormat: String) {
        val lockoutMs = securityManager.pinLockoutRemainingMillis()
        if (lockoutMs > 0) {
            val remainingSeconds = ((lockoutMs + 999) / 1000).toInt()
            _uiState.update { it.copy(error = String.format(lockedOutMessageFormat, remainingSeconds)) }
            return
        }
        if (securityManager.verifyPin(pin)) {
            unlockAndLoadFiles()
        } else {
            _uiState.update { it.copy(error = incorrectPinMessage) }
        }
    }

    fun setupPin(pin: String, errorMessage: String) {
        val success = securityManager.setPin(pin)
        if (success) {
            _uiState.update { it.copy(hasPin = true, error = null) }
            unlockAndLoadFiles()
        } else {
            // Hallazgo real (2026-08-26, ver security.md §10): antes esto no
            // hacía nada -- el usuario se quedaba mirando el teclado sin
            // ninguna indicación de que su PIN no se guardó.
            _uiState.update { it.copy(error = errorMessage) }
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
            // Revisado (hallazgo SonarCloud kotlin:S6293): sin CryptoObject a
            // propósito -- "Carpeta Segura" no cifra los archivos, solo los
            // mueve a una carpeta privada de la app y exige PIN o biometría
            // como puerta de acceso (ver SecurityManager, que jamás cifra/
            // descifra nada). Un CryptoObject solo aporta seguridad real
            // cuando protege una operación criptográfica de verdad -- acá
            // ligarlo a un cifrado ficticio sería más código y más riesgo de
            // dejar a un usuario fuera de sus archivos sin ganar protección
            // real. Si en el futuro se cifra el contenido de los archivos,
            // este es el lugar para agregar el CryptoObject correspondiente.
            prompt.authenticate(promptInfo) // NOSONAR
        } catch (e: Exception) {
            Timber.e(e, "Error biometría: ${e.message}")
            _uiState.update { it.copy(error = String.format(errorTemplate, e.message ?: "")) }
        }
    }

    private fun unlockAndLoadFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val secureFiles = securityManager.getSecureFiles()
            _uiState.update {
                it.copy(
                    screenState = SecurityScreenState.UNLOCKED,
                    secureFiles = secureFiles,
                    error       = null
                )
            }
        }
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
                _uiState.update {
                    it.copy(
                        secureFiles    = secureFiles,
                        successMessage = if (result.originalDeleted) successMessage else null,
                        originalNotDeletedWarning = if (result.originalDeleted) null else originalKeptMessage
                    )
                }
            } else {
                _uiState.update { it.copy(error = errorMessage) }
            }
        }
    }

    // Mensajes localizados capturados en el momento de la acción -- ver
    // PremiumViewModel.purchase() para el mismo patrón: onOriginalDeleteConfirmed()
    // corre en respuesta a un IntentSender que la Screen lanza de forma
    // asíncrona (el diálogo de confirmación del sistema), momento en el que
    // ya no hay stringResource() disponible directamente.
    private var pendingSuccessMessage = ""

    // RNF-SEC-01: para un Uri de SAF el borrado del original solo es posible si el
    // proveedor de almacenamiento lo permite — se intenta y se avisa si no se pudo,
    // en vez de fallar en silencio o prometer un borrado que no ocurrió.
    //
    // Hallazgo real en dispositivo 2026-09-12: DocumentsContract.deleteDocument()
    // no está implementado para todos los proveedores (confirmado con un archivo
    // de WhatsApp: "Unsupported call: android:deleteDocument"). Mismo problema ya
    // resuelto para Biblioteca en DocumentRepository/TrashViewModel -- si el Uri es
    // realmente de MediaStore, MediaStore.createDeleteRequest() (API 30+) sí puede
    // borrarlo, pero exige mostrarle al usuario un diálogo de confirmación del
    // sistema en vez de borrar en silencio.
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

                val deleteResult = try {
                    val deleted = android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
                    if (deleted) OriginalDeleteResult.Deleted else OriginalDeleteResult.Failed
                } catch (e: Exception) {
                    Timber.w(e, "No se pudo eliminar directamente el archivo original: $uri")
                    intentSenderForFailedDelete(e, uri)
                        ?.let { OriginalDeleteResult.NeedsPermission(it) }
                        ?: OriginalDeleteResult.Failed
                }

                val files = securityManager.getSecureFiles()
                when (deleteResult) {
                    OriginalDeleteResult.Deleted -> _uiState.update {
                        it.copy(secureFiles = files, successMessage = successMessage)
                    }
                    OriginalDeleteResult.Failed -> _uiState.update {
                        it.copy(secureFiles = files, originalNotDeletedWarning = originalKeptMessage)
                    }
                    is OriginalDeleteResult.NeedsPermission -> {
                        pendingSuccessMessage = successMessage
                        _uiState.update { it.copy(secureFiles = files) }
                        _pendingOriginalDelete.emit(PendingOriginalDeleteRequest(deleteResult.intentSender, uri))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error importando archivo: ${e.message}")
                _uiState.update { it.copy(error = errorMessage) }
            }
        }
    }

    // La Screen llama a esto tras lanzar el IntentSender de un
    // PendingOriginalDeleteRequest y recibir RESULT_OK -- Android ya borró la
    // fila de MediaStore, solo falta mostrar el mensaje de éxito normal (el
    // archivo ya estaba copiado a la carpeta segura desde importFileToSecure()).
    fun onOriginalDeleteConfirmed() {
        _uiState.update { it.copy(successMessage = pendingSuccessMessage) }
    }

    private fun intentSenderForFailedDelete(e: Exception, uri: Uri): IntentSender? {
        val recoverable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaDeletePermission.recoverableIntentSenderOrNull(e)
        } else {
            null
        }
        val canUseBulkDeleteRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            uri.authority == MediaStore.AUTHORITY
        return recoverable ?: if (canUseBulkDeleteRequest) {
            mediaDeletePermission.createBulkDeleteRequest(listOf(uri))
        } else {
            null
        }
    }

    private sealed interface OriginalDeleteResult {
        data object Deleted : OriginalDeleteResult
        data object Failed : OriginalDeleteResult
        data class NeedsPermission(val intentSender: IntentSender) : OriginalDeleteResult
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

    // RF-SEC-09/HU-SEC-06: restablecer PIN desde la pantalla bloqueada para
    // quien lo olvidó -- borra permanentemente todos los archivos de la
    // Carpeta Segura y lleva al flujo de "Crear PIN" (HU-SEC-01). La
    // confirmación/advertencia vive en la UI, aquí solo se ejecuta.
    fun resetPin() {
        viewModelScope.launch(Dispatchers.IO) {
            securityManager.resetPinAndWipeFiles()
            _uiState.update {
                it.copy(
                    screenState = SecurityScreenState.SETUP_PIN,
                    hasPin      = false,
                    secureFiles = emptyList(),
                    error       = null
                )
            }
        }
    }

    fun dismissSuccess() { _uiState.update { it.copy(successMessage = null) } }
    fun dismissOriginalNotDeletedWarning() { _uiState.update { it.copy(originalNotDeletedWarning = null) } }
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
            val saved = com.docsmart.core.util.DownloadsSaver.saveFile(context, file, "application/pdf")
            _uiState.update { state ->
                if (saved) state.copy(successMessage = String.format(successTemplate, file.name))
                else state.copy(error = errorMessage)
            }
        }
    }
}