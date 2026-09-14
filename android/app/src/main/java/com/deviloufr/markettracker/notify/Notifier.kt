package com.deviloufr.markettracker.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.deviloufr.markettracker.MainActivity
import com.deviloufr.markettracker.R
import com.deviloufr.markettracker.data.AlertEvent
import com.deviloufr.markettracker.data.reasonLabelFr
import java.util.Locale

object Notifier {
    const val CHANNEL_MONITOR = "monitor"
    const val CHANNEL_ALERTS = "alerts"

    private var idCounter = 1000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val monitor = NotificationChannel(
            CHANNEL_MONITOR, "Surveillance", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "État de la surveillance du marché en cours"
            setShowBadge(false)
        }
        val alerts = NotificationChannel(
            CHANNEL_ALERTS, "Alertes de marché", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertes d'anomalies de prix et de volume"
            enableVibration(true)
        }
        nm.createNotificationChannel(monitor)
        nm.createNotificationChannel(alerts)
    }

    fun buildMonitorNotification(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setContentTitle("MarketTracker")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_market)
            .setOngoing(true)
            .setContentIntent(contentIntent(context))
            .build()

    fun showAlert(context: Context, event: AlertEvent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val title = "${event.symbol} — ${reasonLabelFr(event.reason)}"
        val body = "${event.detail}\n" + String.format(Locale.US, "Prix : %.2f", event.price)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(event.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_stat_market)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        nm.notify(idCounter++, notification)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
