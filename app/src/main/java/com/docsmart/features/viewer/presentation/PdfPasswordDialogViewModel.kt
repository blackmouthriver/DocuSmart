package com.docsmart.features.viewer.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Hallazgo real de la auditoría general 2026-09-17 (B12): PdfPasswordDialog
// guardaba la contraseña tecleada en `rememberSaveable`, que Compose
// persiste en el Bundle de `onSaveInstanceState()` de la Activity -- un
// lugar pensado para restaurar UI, no para secretos, y con un modelo de
// exposición distinto al de la memoria del proceso (backups, extracción
// con root, restauración tras kill del sistema). El fix anterior a
// `rememberSaveable` (revisión general 2026-09-16) era deliberado y
// corregía un bug real (la contraseña se perdía al rotar, ver el propio
// comentario en `PdfPasswordDialog`) -- simplemente volver a `remember`
// reintroduciría ese bug. Un ViewModel sobrevive cambios de configuración
// simples (rotación) por el mecanismo de `NonConfigurationInstance` de
// Android, completamente aparte del Bundle -- la contraseña nunca toca
// `onSaveInstanceState()`. Scopeado al NavBackStackEntry del Visor (mismo
// mecanismo que `hiltViewModel()` usa en toda la app): se recrea limpio
// cada vez que se entra de nuevo a la pantalla del Visor (documento
// nuevo), y el diálogo en sí solo vive mientras `uiState.requiresPassword`
// es true en esa misma visita a la pantalla.
@HiltViewModel
class PdfPasswordDialogViewModel @Inject constructor() : ViewModel() {

    var password by mutableStateOf("")
        private set

    var showPassword by mutableStateOf(false)
        private set

    fun onPasswordChange(value: String) {
        password = value
    }

    fun onToggleShowPassword() {
        showPassword = !showPassword
    }

    // Se llama tras un desbloqueo exitoso (o al cancelar) para no dejar la
    // contraseña ya usada viva en memoria más de lo necesario.
    fun clear() {
        password = ""
        showPassword = false
    }
}
