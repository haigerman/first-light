package io.github.haigerman.firstlight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dauerhafte, stille Benachrichtigung mit dem nächsten Weckruf, z. B. „Dienstag, 06:30“.
 * Sie ist nur sichtbar, solange ein Wecker (oder Schlummer-Wecker) gestellt ist, und
 * erscheint dadurch auch auf Sperrbildschirm und Always-on-Display.
 */
object NextAlarmNotification {
    private const val CHANNEL_ID = "next_alarm"
    private const val NOTIFICATION_ID = 43

    /** Zeigt den nächsten Weckruf an oder entfernt die Benachrichtigung, wenn keiner gestellt ist. */
    fun update(context: Context, nextAlarm: Long?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val snooze = AlarmPrefs(context).snoozeUntil.takeIf { it > System.currentTimeMillis() }
        val next = listOfNotNull(nextAlarm, snooze).minOrNull()
        if (next == null || !nm.areNotificationsEnabled()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }

        // „Standard“-Wichtigkeit, damit Sperrbildschirm und Always-on-Display sie zeigen –
        // aber ohne Ton und Vibration.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Nächster Wecker", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Zeigt den nächsten Weckruf, solange der Wecker aktiv ist"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        val text = SimpleDateFormat("EEEE, HH:mm", Locale.GERMANY).format(Date(next))
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    /** Neu berechnen, ohne den Wecker selbst neu zu stellen (z. B. nach Schlummern). */
    fun refresh(context: Context) {
        val prefs = AlarmPrefs(context)
        val next = if (prefs.enabled && AlarmScheduler.canScheduleExact(context))
            AlarmScheduler.nextTrigger(prefs.hour, prefs.minute) else null
        update(context, next)
    }
}
