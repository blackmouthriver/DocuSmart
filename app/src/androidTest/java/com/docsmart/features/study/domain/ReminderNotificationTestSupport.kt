package com.docsmart.features.study.domain

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

// Utilidades de las pruebas de los receivers de recordatorio (Notas y Agenda): la notificación se
// publica de forma asíncrona, así que se espera un poco a verla en las notificaciones activas.

/** Regla que concede POST_NOTIFICATIONS (Android 13+) para que el receiver pueda publicar. */
internal fun grantNotificationsRule(): GrantPermissionRule =
    GrantPermissionRule.grant(
        *if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        },
    )

/** Busca (hasta [timeoutMillis]) la notificación activa con ese tag e id; null si nunca aparece. */
internal fun awaitActiveNotification(
    manager: NotificationManager,
    tag: String,
    id: Int,
    timeoutMillis: Long = 5_000,
): StatusBarNotification? =
    runBlocking {
        withTimeoutOrNull(timeoutMillis) {
            var found: StatusBarNotification? = null
            while (found == null) {
                found = manager.activeNotifications.firstOrNull { it.tag == tag && it.id == id }
                if (found == null) delay(100)
            }
            found
        }
    }
