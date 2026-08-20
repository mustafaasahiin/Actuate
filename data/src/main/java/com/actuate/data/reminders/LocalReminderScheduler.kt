package com.actuate.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.util.UUID

/**
 * Schedules a local reminder at [dueAt] via AlarmManager. This is the
 * Android equivalent of Apple EventKit reminders: the device fires a
 * notification at the due time, no external service involved.
 */
class LocalReminderScheduler(private val context: Context) {

    fun schedule(title: String, dueAt: Instant, priority: String? = null) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = UUID.randomUUID().hashCode()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_FIRED
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_PRIORITY, priority)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAt = dueAt.toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) {
            return
        }

        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }.onFailure {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } else {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                EXACT_WINDOW_MS,
                pendingIntent,
            )
        }
    }

    companion object {
        const val ACTION_REMINDER_FIRED = "com.actuate.app.action.REMINDER_FIRED"
        const val EXTRA_TITLE = "com.actuate.app.extra.REMINDER_TITLE"
        const val EXTRA_PRIORITY = "com.actuate.app.extra.REMINDER_PRIORITY"
        const val EXTRA_REQUEST_CODE = "com.actuate.app.extra.REMINDER_REQUEST_CODE"
        private const val EXACT_WINDOW_MS = 15L * 60L * 1000L
    }
}