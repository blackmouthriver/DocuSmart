package com.docsmart.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Hallazgo #52 (revisión general 2026-09-16): Home y Biblioteca solo
 * cargaban sus datos una vez, al componer por primera vez -- un documento
 * movido/borrado desde otra pantalla (Visor, Carpeta Segura, etc.) seguía
 * apareciendo como "fantasma" al volver atrás. LocalLifecycleOwner aquí es
 * el Lifecycle del NavBackStackEntry (Navigation-Compose lo provee así
 * dentro de cada `composable {}`), que pasa por ON_RESUME cada vez que se
 * vuelve a esta pantalla -- no solo cuando la app entera vuelve de segundo
 * plano. Se omite el primer ON_RESUME porque la carga inicial ya la hace
 * el llamador (por ejemplo, un `LaunchedEffect(Unit)`).
 *
 * Hallazgo real de la revisión de correctitud adversarial de este mismo
 * lote (2026-09-16): `enabled`/`onResume` se leían directamente dentro del
 * `DisposableEffect(lifecycleOwner)`, cuya clave (`lifecycleOwner`) no
 * cambia entre recomposiciones de la misma pantalla -- el observer quedaba
 * cerrado sobre el valor de `enabled` de la PRIMERA composición para
 * siempre. Caso real: `LibraryScreen` llama a esto con
 * `enabled = hasPermission`, que empieza en `false` (permiso aún no
 * otorgado) y pasa a `true` recién al concederlo -- el observer, instalado
 * con `enabled=false`, nunca volvía a evaluar el valor real, así que el
 * "fantasma" que este mismo fix debía resolver seguía apareciendo para
 * cualquier usuario que no tuviera el permiso ya concedido en su primera
 * visita. `rememberUpdatedState` (mismo patrón ya usado en
 * `ViewerScreen.kt` para el gesto de resaltado) deja que el observer lea
 * siempre el valor más reciente sin necesidad de reinstalarse.
 */
@Composable
fun ReloadOnScreenResume(enabled: Boolean = true, onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentEnabled = rememberUpdatedState(enabled)
    val currentOnResume = rememberUpdatedState(onResume)
    DisposableEffect(lifecycleOwner) {
        var isFirstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isFirstResume) {
                    isFirstResume = false
                } else if (currentEnabled.value) {
                    currentOnResume.value()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
