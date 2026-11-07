package io.github.haigerman.firstlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Nach Neustart, App-Update oder Zeit-/Zeitzonenänderung den Wecker neu stellen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.apply(context)
        AlarmScheduler.restoreSnooze(context)
    }
}
