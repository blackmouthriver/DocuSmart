package com.docsmart

import android.content.Intent
import com.docsmart.core.navegation.NavRoutes

// Lógica pura de MainActivity, extraída para poder testearla en JVM sin
// Robolectric (Intent/Uri/Bundle reales no funcionan en tests unitarios). Solo
// usa constantes de `Intent` (inlineadas por el compilador, no cargan la clase).

/**
 * Decide si el Intent con el que se creó la Activity debe procesarse.
 *
 * Hallazgo real de la ronda 16: `onCreate()` procesaba `intent` SIEMPRE, y tras
 * una rotación, un cambio de tema/idioma en caliente o la restauración por
 * proceso muerto Android re-entrega el MISMO Intent original -- el archivo
 * abierto desde otra app (o la notificación de Agenda/Notas) volvía a
 * navegar al Visor/Agenda/Nota, aunque el usuario ya hubiera salido de ahí.
 * Tampoco debe reprocesarse un Intent que Android reproduce al reabrir la app
 * desde "Recientes" (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`).
 */
internal fun shouldConsumeLaunchIntent(
    isRestoringState: Boolean,
    intentFlags: Int,
): Boolean = !isRestoringState && (intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

/**
 * Solo un `ACTION_VIEW` con esquema `content://` es un origen válido para
 * abrir un archivo desde OTRA app (ver el hallazgo de seguridad de 2026-09-16
 * en MainActivity.resolveExternalIntent: un `file://` externo podía leer
 * `filesDir`, incluida la Carpeta Segura).
 */
internal fun isAllowedExternalViewIntent(
    action: String?,
    scheme: String?,
): Boolean = action == Intent.ACTION_VIEW && scheme == "content"

/**
 * `takePersistableUriPermission()` lanza `SecurityException` si el emisor no
 * ofreció un permiso persistible (Drive/WhatsApp/Gmail casi nunca lo hacen).
 * Solo se intenta cuando el Intent trae el flag, en vez de provocar y
 * atrapar la excepción en cada apertura.
 */
internal fun canPersistUriGrant(intentFlags: Int): Boolean {
    return (intentFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
}

/** Rutas en las que todavía no se puede redirigir a un deep link (arranque en frío). */
internal fun isSplashOrOnboardingRoute(route: String?): Boolean =
    route == null ||
        route == NavRoutes.SplashMouthBlack.route ||
        route == NavRoutes.SplashDocuSmart.route ||
        route == NavRoutes.Onboarding.route
