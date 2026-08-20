package com.actuate.data.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.actuate.data.R

/**
 * Shows the reminder notification when the alarm fires.
 * Declared in the manifest with `android:exported="false"`.
 */
class ReminderReceiver : BroadcastReceiver() {

    /**
     * Lint cannot trace the runtime `checkSelfPermission(POST_NOTIFICATIONS)` guard below,
     * so the `notify()` call is suppressed: the permission is verified before posting
     * (and `notify` is additionally wrapped in `runCatching` for revocable-permission races).
     */
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LocalReminderScheduler.ACTION_REMINDER_FIRED) return
        val title = intent.getStringExtra(LocalReminderScheduler.EXTRA_TITLE) ?: "Reminder"
        val priority = intent.getStringExtra(LocalReminderScheduler.EXTRA_PRIORITY)

        createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(priority?.let { "Priority: $it" } ?: "Actuate reminder")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        runCatching { notificationManager.notify(intent.hashCode(), notification) }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Voice-set reminders from Actuate"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "actuate_reminders"
    }
}