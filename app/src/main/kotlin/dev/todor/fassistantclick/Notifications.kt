package dev.todor.fassistantclick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build

/**
 * One ongoing notification while a run is going, carrying a Stop action.
 *
 * It exists for the case the panel cannot cover: an endless run whose panel has been dragged half
 * off the edge of the screen. There is no foreground service behind it — the accessibility
 * service is already a long-lived system-bound process, so a second one would buy nothing.
 */
object Notifications {
    private const val CHANNEL = "runs"
    private const val RUNNING = 1

    fun showRunning(context: Context, scriptName: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }

        val stop = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, StopReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL)
        } else {
            Notification.Builder(context)
        }

        manager.notify(
            RUNNING,
            builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notif_running, scriptName))
                .setContentText(context.getString(R.string.notif_running_sub))
                .setOngoing(true)
                .addAction(
                    Notification.Action.Builder(
                        // Android 7 will not render an action without an icon.
                        Icon.createWithResource(context, R.drawable.ic_notification),
                        context.getString(R.string.notif_stop),
                        stop,
                    ).build()
                )
                .build(),
        )
    }

    fun clearRunning(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(RUNNING)
    }
}
