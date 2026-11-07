package io.github.haigerman.firstlight

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {

    const val SNOOZE_MINUTES = 7
    const val EXTRA_SNOOZE = "snooze"

    /** Nächster Zeitpunkt (in ms) für die eingestellte Uhrzeit – heute oder morgen. */
    fun nextTrigger(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** Stellt den Wecker gemäß den gespeicherten Einstellungen (oder löscht ihn). */
    fun apply(context: Context): Long? {
        val prefs = AlarmPrefs(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = alarmIntent(context)
        am.cancel(operation)
        if (!prefs.enabled || !canScheduleExact(context)) return null

        val triggerAt = nextTrigger(prefs.hour, prefs.minute)
        // setAlarmClock: exakt, weckt auch aus dem Doze-Modus und zeigt das Wecker-Symbol in der Statusleiste.
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent(context)), operation)
        return triggerAt
    }

    /** Schlummern: in [SNOOZE_MINUTES] Minuten erneut klingeln. */
    fun snooze(context: Context): Long {
        val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        AlarmPrefs(context).snoozeUntil = triggerAt
        armSnooze(context, triggerAt)
        return triggerAt
    }

    fun cancelSnooze(context: Context) {
        AlarmPrefs(context).snoozeUntil = 0L
        context.getSystemService(AlarmManager::class.java).cancel(snoozeIntent(context))
    }

    /** Nach Neustart einen noch offenen Schlummer-Wecker wiederherstellen. */
    fun restoreSnooze(context: Context) {
        val until = AlarmPrefs(context).snoozeUntil
        if (until == 0L) return
        // Während das Gerät aus war verpasst? Dann gleich klingeln.
        armSnooze(context, maxOf(until, System.currentTimeMillis() + 1_000L))
    }

    private fun armSnooze(context: Context, triggerAt: Long) {
        if (!canScheduleExact(context)) return
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent(context)), snoozeIntent(context))
    }

    private fun showIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun snoozeIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 2,
            Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_SNOOZE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
