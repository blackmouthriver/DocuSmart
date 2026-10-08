package com.docsmart.core.ui.util

import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.docsmart.R

// Marca de DocuSmart en las notificaciones de recordatorio. El ícono pequeño
// (barra de estado) solo admite una silueta blanca, así que el logo a color va
// como ícono grande y el azul de la marca tiñe el ícono pequeño al desplegar
// la notificación.
private const val BRAND_COLOR = 0xFF2563FF.toInt()
private const val LARGE_ICON_SIZE_PX = 192

/** Aplica el logo de DocuSmart (ícono pequeño, color de marca e ícono grande) al [builder]. */
fun NotificationCompat.Builder.applyDocuSmartBranding(context: Context): NotificationCompat.Builder =
    setSmallIcon(R.drawable.ic_notification_docusmart)
        .setColor(BRAND_COLOR)
        .setLargeIcon(docuSmartLargeIcon(context))

private fun docuSmartLargeIcon(context: Context): Bitmap? =
    ContextCompat
        .getDrawable(context, R.mipmap.ic_launcher_docusmart_round)
        ?.toBitmap(LARGE_ICON_SIZE_PX, LARGE_ICON_SIZE_PX)
