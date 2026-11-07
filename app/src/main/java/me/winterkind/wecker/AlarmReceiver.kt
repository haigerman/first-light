package me.winterkind.wecker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Wird vom AlarmManager zur Weckzeit aufgerufen. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AlarmPrefs(context)
        if (intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)) {
            // Schlummerzeit abgelaufen: erneut klingeln, regulären Wecker nicht anfassen
            prefs.snoozeUntil = 0L
            context.startForegroundService(Intent(context, AlarmService::class.java))
            return
        }
        if (!prefs.daily) prefs.enabled = false
        AlarmScheduler.apply(context) // bei "täglich": nächsten Tag stellen

        context.startForegroundService(Intent(context, AlarmService::class.java))
    }
}
