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
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val title = getString(
            if (state.isBreak) R.string.study_break_label else R.string.study_study_label
        )
        val time = "${state.minutes.toString().padStart(2, '0')}:" +
            state.seconds.toString().padStart(2, '0')

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_pomodoro)
            .setContentTitle(title)
            .setContentText(getString(R.string.study_pomodoro_notification_text, time))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

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

    companion object {
        private const val CHANNEL_ID = "pomodoro_timer"
        private const val NOTIFICATION_ID = 4821
    }
}
