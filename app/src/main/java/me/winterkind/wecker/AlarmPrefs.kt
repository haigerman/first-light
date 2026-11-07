package me.winterkind.wecker

import android.content.Context
import android.net.Uri

/** Speichert die Wecker-Einstellungen dauerhaft. */
class AlarmPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("wecker", Context.MODE_PRIVATE)

    var hour: Int
        get() = prefs.getInt("hour", 7)
        set(v) = prefs.edit().putInt("hour", v).apply()

    var minute: Int
        get() = prefs.getInt("minute", 0)
        set(v) = prefs.edit().putInt("minute", v).apply()

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    /** Wenn true, klingelt der Wecker jeden Tag zur selben Zeit. */
    var daily: Boolean
        get() = prefs.getBoolean("daily", true)
        set(v) = prefs.edit().putBoolean("daily", v).apply()

    var musicUri: Uri?
        get() = prefs.getString("musicUri", null)?.let(Uri::parse)
        set(v) = prefs.edit().putString("musicUri", v?.toString()).apply()

    var musicName: String?
        get() = prefs.getString("musicName", null)
        set(v) = prefs.edit().putString("musicName", v).apply()

    /** Zeitpunkt (ms) des laufenden Schlummer-Weckers, 0 wenn keiner aktiv ist. */
    var snoozeUntil: Long
        get() = prefs.getLong("snoozeUntil", 0L)
        set(v) = prefs.edit().putLong("snoozeUntil", v).apply()

    var userName: String
        get() = prefs.getString("userName", "") ?: ""
        set(v) = prefs.edit().putString("userName", v).apply()

    /** Begrüßung beim Wecken – mit Namen, falls einer hinterlegt ist. */
    fun greeting(): String = greetingFor(userName)

    companion object {
        fun greetingFor(name: String): String =
            if (name.isBlank()) "Guten Morgen!" else "Guten Morgen, ${name.trim()} \u2600\uFE0F"
    }
}
