package com.docsmart.features.study.domain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.docsmart.MainActivity
import com.docsmart.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

// Hallazgo real de la auditoría general 2026-09-18 (Alta -- fin de sesión
// totalmente silencioso): el canal de la notificación en curso (CHANNEL_ID
// más abajo) es IMPORTANCE_LOW a propósito, para no interrumpir en cada
// tick -- pero eso significa que tampoco suena/vibra cuando un bloque
// TERMINA, momento en el que el usuario sí necesita enterarse aunque no
// esté mirando la pantalla. Se agrega un segundo canal (CHANNEL_ID_ALERT)
// de IMPORTANCE_DEFAULT, con su propia notificación (NOTIFICATION_ID_ALERT,
// distinto de NOTIFICATION_ID) para que publicarla no cancele ni sea
// cancelada por la notificación de progreso en curso.

/**
 * RF-STU-10: mantiene [PomodoroEngine] con vida (y visible en una
 * notificación) cuando la app pasa completamente a segundo plano -- sin
 * esto, Android puede matar el proceso y perder el conteo aunque
 * `PomodoroEngine` en sí no dependa de ninguna pantalla. Es un servicio
 * "tonto": no tiene su propio timer, solo observa el `StateFlow` de
 * `PomodoroEngine` y refleja su estado en la notificación; arrancar/pausar/
 * reiniciar el Pomodoro sigue siendo responsabilidad exclusiva del motor.
 */
class PomodoroTimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()
        createAlertChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification(PomodoroEngine.state.value))

        PomodoroEngine.state
            .onEach { state ->
                if (state.isRunning) {
                    updateNotification(state)
                } else {
                    stopSelf()
                }
            }
            .launchIn(serviceScope)

        // Hallazgo real de la auditoría general 2026-09-18 (Alta): a
        // diferencia del collector de arriba (progreso, silencioso a
        // propósito), este avisa con sonido/vibración cuando un bloque
        // TERMINA -- ver postCompletionAlert().
        PomodoroEngine.completionEvents
            .onEach { wasBreak ->
                postCompletionAlert(wasBreak)
                // Revisión adversarial de correctitud (ronda 11): consume el
                // evento tras procesarlo -- el replay=1 de completionEvents
                // existe para que este collector no se pierda un evento
                // emitido justo antes de que onCreate() terminara de
                // suscribirse, no para re-notificar el mismo bloque
                // terminado a una futura recreación del servicio.
                PomodoroEngine.consumeCompletionEvent()
            }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateNotification(state: PomodoroState) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: PomodoroState): Notification {
        val title = getString(
            if (state.isBreak) R.string.study_break_label else R.string.study_study_label
        )
        val time = "${state.minutes.toString().padStart(2, '0')}:" +
            state.seconds.toString().padStart(2, '0')

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pomodoro)
            .setContentTitle(title)
            .setContentText(getString(R.string.study_pomodoro_notification_text, time))
            .setContentIntent(openAppPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // Hallazgo real de la auditoría general 2026-09-18 (Alta -- fin de
    // sesión totalmente silencioso): notificación aparte de la de progreso
    // (buildNotification), publicada solo cuando un bloque TERMINA. Usa
    // CHANNEL_ID_ALERT (IMPORTANCE_DEFAULT, con vibración) para que suene
    // por defecto, y NOTIFICATION_ID_ALERT (distinto de NOTIFICATION_ID)
    // para no ser cancelada cuando el collector de progreso llama
    // stopSelf() al terminar el bloque.
    private fun postCompletionAlert(wasBreak: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val titleRes = if (wasBreak) {
            R.string.study_pomodoro_break_complete_title
        } else {
            R.string.study_pomodoro_study_complete_title
        }
        val bodyRes = if (wasBreak) {
            R.string.study_pomodoro_break_complete_body
        } else {
            R.string.study_pomodoro_study_complete_body
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERT)
            .setSmallIcon(R.drawable.ic_notification_pomodoro)
            .setContentTitle(getString(titleRes))
            .setContentText(getString(bodyRes))
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_ID_ALERT, notification)
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.study_pomodoro_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun createAlertChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager != null && manager.getNotificationChannel(CHANNEL_ID_ALERT) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID_ALERT,
                getString(R.string.study_pomodoro_alert_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.enableVibration(true)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "pomodoro_timer"
        private const val CHANNEL_ID_ALERT = "pomodoro_complete"
        private const val NOTIFICATION_ID = 4821
        private const val NOTIFICATION_ID_ALERT = 4822
    }
}
