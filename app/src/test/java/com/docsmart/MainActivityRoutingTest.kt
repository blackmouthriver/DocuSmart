package com.docsmart

import android.content.Intent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ronda 16: decisiones de arranque de MainActivity extraidas como funciones
 * puras (Intent/Uri reales no corren en JVM sin Robolectric).
 */
class MainActivityRoutingTest {
    @Test
    fun `un arranque real sin flags procesa el Intent`() {
        assertTrue(shouldConsumeLaunchIntent(isRestoringState = false, intentFlags = 0))
    }

    @Test
    fun `tras rotacion o proceso muerto no se reprocesa el Intent original`() {
        assertFalse(shouldConsumeLaunchIntent(isRestoringState = true, intentFlags = 0))
    }

    @Test
    fun `un Intent reproducido desde Recientes no se reprocesa`() {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

        assertFalse(shouldConsumeLaunchIntent(isRestoringState = false, intentFlags = flags))
    }

    @Test
    fun `otros flags no bloquean el procesamiento`() {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

        assertTrue(shouldConsumeLaunchIntent(isRestoringState = false, intentFlags = flags))
    }

    @Test
    fun `solo VIEW con content acepta un archivo externo`() {
        assertTrue(isAllowedExternalViewIntent(Intent.ACTION_VIEW, "content"))
    }

    @Test
    fun `un file externo se rechaza porque podria leer la Carpeta Segura`() {
        assertFalse(isAllowedExternalViewIntent(Intent.ACTION_VIEW, "file"))
    }

    @Test
    fun `http y esquema nulo se rechazan`() {
        assertFalse(isAllowedExternalViewIntent(Intent.ACTION_VIEW, "https"))
        assertFalse(isAllowedExternalViewIntent(Intent.ACTION_VIEW, null))
    }

    @Test
    fun `una accion distinta de VIEW se rechaza aunque el esquema sea content`() {
        assertFalse(isAllowedExternalViewIntent(Intent.ACTION_SEND, "content"))
        assertFalse(isAllowedExternalViewIntent(null, "content"))
    }

    @Test
    fun `el permiso persistible solo se intenta si el emisor lo ofrecio`() {
        assertFalse(canPersistUriGrant(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertTrue(
            canPersistUriGrant(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            ),
        )
    }

    @Test
    fun `splash onboarding y ruta nula esperan antes de redirigir un deep link`() {
        assertTrue(isSplashOrOnboardingRoute(null))
        assertTrue(isSplashOrOnboardingRoute("splash_mouthblack"))
        assertTrue(isSplashOrOnboardingRoute("splash_docusmart"))
        assertTrue(isSplashOrOnboardingRoute("onboarding"))
    }

    @Test
    fun `home y el resto de pantallas permiten redirigir de inmediato`() {
        assertFalse(isSplashOrOnboardingRoute("home"))
        assertFalse(isSplashOrOnboardingRoute("settings"))
        assertFalse(isSplashOrOnboardingRoute("viewer/{documentId}"))
    }
}
