package com.docsmart.features.scanner.presentation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docsmart.core.ads.AdManager
import com.docsmart.core.ads.DailyLimitManager
import com.docsmart.core.ui.components.DocumentUiModel
import com.docsmart.features.scanner.domain.ScanSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Estado del límite diario de "escaneos guardados" (backlog UX 2026-09-06,
 * pedido explícito del usuario): contador propio de 8/día, independiente
 * del de conversiones que ya usa el Convertidor -- agotar uno no afecta
 * al otro.
 */
data class ScanSaveLimitUiState(
    val savedCount: Int = 0,
    val savedLimit: Int = DailyLimitManager.LIMIT_SCANS_SAVED,
    val showLimitDialog: Boolean = false
)

/**
 * Envuelve [ScanSessionManager] (un `@Singleton`, no un ViewModel) solo
 * para poder obtenerlo con `hiltViewModel()` desde `ScanResultScreen`,
 * mismo patrón que [ScanImageEditorViewModel]. Recrear esta envoltura en
 * cada navegación no pierde nada -- el estado real vive en el singleton.
 */
@HiltViewModel
class ScanSessionViewModel @Inject constructor(
    private val sessionManager: ScanSessionManager,
    private val dailyLimitManager: DailyLimitManager,
    val adManager: AdManager
) : ViewModel() {

    val scannedFiles: StateFlow<List<DocumentUiModel>> = sessionManager.scannedFiles

    private val _saveLimitState = MutableStateFlow(ScanSaveLimitUiState())
    val saveLimitState: StateFlow<ScanSaveLimitUiState> = _saveLimitState.asStateFlow()

    init {
        refreshSaveLimitState()
    }

    private fun refreshSaveLimitState() {
        _saveLimitState.update {
            it.copy(
                savedCount = dailyLimitManager.getScanSavedCount(),
                savedLimit = dailyLimitManager.getScanSavedLimit()
            )
        }
    }

    fun addFile(file: File) = sessionManager.addFile(file)

    fun clearSession() = sessionManager.clear()

    fun toggleFavorite(documentId: String) {
        viewModelScope.launch { sessionManager.toggleFavorite(documentId) }
    }

    fun renameDocument(documentId: String, newName: String) {
        viewModelScope.launch { sessionManager.renameDocument(documentId, newName) }
    }

    fun deleteDocument(documentId: String) {
        viewModelScope.launch { sessionManager.deleteDocument(documentId) }
    }

    // ── Límite diario de escaneos guardados (8/día) ───────────────────────────
    // Se consulta justo antes de "Guardar en Descargas"/"Compartir" en
    // ScanResultActions -- devuelve `false` y muestra el diálogo de límite
    // si ya no quedan usos disponibles hoy (Premium nunca se bloquea).
    fun requestScanSaveSlot(): Boolean {
        if (adManager.isPremium.value || dailyLimitManager.canSaveScan()) return true
        _saveLimitState.update { it.copy(showLimitDialog = true) }
        return false
    }

    fun registerScanSaved() {
        dailyLimitManager.registerScanSaved()
        refreshSaveLimitState()
    }

    fun dismissScanLimitDialog() {
        _saveLimitState.update { it.copy(showLimitDialog = false) }
    }

    fun watchAdForScanSave(activity: Activity) {
        _saveLimitState.update { it.copy(showLimitDialog = false) }
        adManager.showRewardedAd(
            activity = activity,
            onRewarded = {
                dailyLimitManager.addRewardedScanSave()
                refreshSaveLimitState()
                Timber.d("ScanSessionViewModel: +1 escaneo guardado por Rewarded Ad")
            },
            onFailed = {
                Timber.w("ScanSessionViewModel: anuncio recompensado no disponible")
            }
        )
    }
}
