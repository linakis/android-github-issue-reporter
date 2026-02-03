package com.ghreporter.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ghreporter.GHReporter
import com.ghreporter.GHReporterConfig
import com.ghreporter.R

object GHReporterNotificationManager {
    private const val CHANNEL_ID = "ghreporter_report_channel"
    private const val NOTIFICATION_ID = 4107
    private const val ACTION_REPORT = "com.ghreporter.action.REPORT"

    fun showPersistentNotification(context: Context, config: GHReporterConfig) {
        if (!canPostNotifications(context)) return

        createChannelIfNeeded(context)

        val title =
            config.notificationTitle
                ?: context.getString(R.string.ghreporter_notification_title)
        val text =
            config.notificationText
                ?: context.getString(R.string.ghreporter_notification_text)

        val contentIntent = createActivityPendingIntent(context)
        val actionIntent = createActionPendingIntent(context)

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ghreporter_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(
                    NotificationCompat.Action(
                        R.drawable.ghreporter_notification,
                        context.getString(R.string.ghreporter_notification_action),
                        actionIntent
                    )
                )
                .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancelPersistentNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun isReportAction(intent: Intent?): Boolean {
        return intent?.action == ACTION_REPORT
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.ghreporter_notification_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.ghreporter_notification_text)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

        manager.createNotificationChannel(channel)
    }

    private fun createActivityPendingIntent(context: Context): PendingIntent {
        val intent = GHReporter.getStartReportingIntent(context)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )
    }

    private fun createActionPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReportNotificationActionReceiver::class.java).apply {
            action = ACTION_REPORT
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}
