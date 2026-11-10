package io.github.haigerman.firstlight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.exp

/**
 * Spielt das Musikstück ab und steigert die Lautstärke:
 * Exponentieller Anstieg (Basis e) von 0 % auf 100 % über genau eine Länge des Musikstücks:
 *
 *     L(x) = (e^(k·x) − 1) / (e^k − 1),   x = vergangene Zeit / Länge des Stücks ∈ [0, 1]
 *
 * Die „− 1“ sorgt dafür, dass genau bei 0 % begonnen und genau bei 100 % geendet wird.
 * Mit k = [RAMP_STEEPNESS] = 3 ist es bei einem 2:32-Stück nach 1 Minute erst ca. 12 %,
 * danach steigt die Lautstärke immer schneller.
 * Danach läuft das Stück in Schleife weiter und bleibt bei 100 %.
 *
 * Sonderfall Vogelgesang: Sobald er 100 % erreicht, wird zusätzlich „First Light“
 * eingeblendet – linear in [LAYER_FADE_MS] (1 Minute) von 0 % auf 100 %, ebenfalls in Schleife.
 *
 * Nach [MAX_RING_MS] (4 Minuten) verstummt der Wecker von selbst, wie bei „Ausschalten“.
 * Die Lautstärke wird alle [TICK_MS] nachgeführt, damit keine Stufen hörbar sind.
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_STOP = "io.github.haigerman.firstlight.STOP"
        const val ACTION_SNOOZE = "io.github.haigerman.firstlight.SNOOZE"
        /** Ersatzdauer, falls das Stück keine (sinnvolle) Länge meldet, z. B. sehr kurze System-Töne. */
        const val FALLBACK_RAMP_MS = 150_000L
        private const val MIN_RAMP_MS = 30_000L
        /** Steilheit k der Exponentialkurve: größer = länger leise, am Ende steiler. */
        const val RAMP_STEEPNESS = 3.0
        /** Dauer der linearen Einblendung von „First Light“ beim Vogelgesang. */
        const val LAYER_FADE_MS = 60_000L
        /** Nach dieser Zeit schaltet sich der klingelnde Wecker selbst aus. */
        const val MAX_RING_MS = 240_000L
        private const val TICK_MS = 100L
        private const val CHANNEL_ID = "alarm"
        private const val NOTIFICATION_ID = 42
        private const val TAG = "AlarmService"

        private val _volumePercent = MutableStateFlow<Int?>(null)
        /** Aktuelle Lautstärke in Prozent, null wenn kein Alarm läuft. */
        val volumePercent: StateFlow<Int?> = _volumePercent

        fun snooze(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    /** Beim Vogelgesang: zusätzlich eingeblendetes „First Light“. */
    private var layerPlayer: MediaPlayer? = null
    private var layerStart = 0L
    private var isBirdsong = false
    private lateinit var audioManager: AudioManager
    private var originalStreamVolume: Int? = null
    private var rampStart = 0L
    private var rampDuration = FALLBACK_RAMP_MS

    private val rampStep = object : Runnable {
        override fun run() {
            val mainDone = applyVolume() >= 1f
            if (mainDone && isBirdsong && layerPlayer == null) startLayer()
            val layerDone = applyLayerVolume()
            val waitingForLayer = isBirdsong && layerPlayer == null // Einblendung konnte nicht starten
            if (!mainDone || (isBirdsong && !layerDone && !waitingForLayer)) {
                handler.postDelayed(this, TICK_MS)
            }
        }
    }

    /** Automatisches Ende nach [MAX_RING_MS]. */
    private val autoStop = Runnable {
        Log.i(TAG, "Wecker nach ${MAX_RING_MS / 60_000} Minuten automatisch beendet")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SNOOZE) {
            if (player != null) AlarmScheduler.snooze(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (player != null) return START_NOT_STICKY // läuft bereits

        startInForeground()
        startPlayback()
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Wecker", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // Ton kommt vom MediaPlayer
                description = "Benachrichtigung, wenn der Wecker klingelt"
            }
        )
        val fullScreen = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getService(
            this, 2,
            Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(AlarmPrefs(this).greeting())
            .setContentText("Die Musik wird langsam lauter.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(Notification.Action.Builder(null, "Schlummern (+${AlarmScheduler.SNOOZE_MINUTES} Min.)", snooze).build())
            .addAction(Notification.Action.Builder(null, "Ausschalten", stop).build())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startPlayback() {
        audioManager = getSystemService(AudioManager::class.java)
        // Wecker-Lautstärke des Geräts für die Dauer des Alarms auf Maximum,
        // die eigentliche Lautstärke (0 % … 100 %) wird über den Player geregelt.
        // So sind feine 1-%-Schritte möglich, obwohl der Systemregler nur ~7–15 Stufen hat.
        originalStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        setAlarmStreamVolume(audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM))

        val prefs = AlarmPrefs(this)
        val chosen = when (prefs.sound) {
            AlarmPrefs.Sound.FIRST_LIGHT -> rawUri(R.raw.first_light)
            AlarmPrefs.Sound.BIRDSONG -> rawUri(R.raw.birdsong)
            AlarmPrefs.Sound.CUSTOM -> prefs.musicUri
        }
        player = createPlayer(chosen)
        isBirdsong = player != null && prefs.sound == AlarmPrefs.Sound.BIRDSONG
        if (player == null) player = createPlayer(rawUri(R.raw.first_light)) // z. B. eigene Datei gelöscht
        if (player == null) player = createPlayer(fallbackUri())             // Notfall: System-Weckton
        if (player == null) {
            Log.e(TAG, "Keine abspielbare Audiodatei gefunden")
            stopSelf()
            return
        }
        val trackLength = player?.duration?.toLong() ?: -1L
        rampDuration = if (trackLength >= MIN_RAMP_MS) trackLength else FALLBACK_RAMP_MS
        rampStart = SystemClock.elapsedRealtime()
        applyVolume()
        player?.start()
        handler.postDelayed(rampStep, TICK_MS)
        handler.postDelayed(autoStop, MAX_RING_MS)
    }

    /** Vogelgesang hat 100 % erreicht: „First Light“ dazu einblenden (beginnt bei 0 %). */
    private fun startLayer() {
        layerPlayer = createPlayer(rawUri(R.raw.first_light))?.apply {
            setVolume(0f, 0f)
            start()
        }
        layerStart = SystemClock.elapsedRealtime()
    }

    /** Lineare Einblendung von „First Light“; gibt true zurück, wenn 100 % erreicht sind. */
    private fun applyLayerVolume(): Boolean {
        val layer = layerPlayer ?: return false
        val v = ((SystemClock.elapsedRealtime() - layerStart).toFloat() / LAYER_FADE_MS).coerceIn(0f, 1f)
        layer.setVolume(v, v)
        return v >= 1f
    }

    private fun createPlayer(uri: Uri?): MediaPlayer? {
        if (uri == null) return null
        return try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kann $uri nicht abspielen", e)
            null
        }
    }

    /** Manche Geräte verweigern Lautstärkeänderungen im „Nicht stören“-Modus – dann nicht abstürzen. */
    private fun setAlarmStreamVolume(index: Int) {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, index, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Wecker-Lautstärke konnte nicht gesetzt werden", e)
        }
    }

    private fun rawUri(resId: Int): Uri =
        Uri.parse("android.resource://$packageName/$resId")

    private fun fallbackUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /** Setzt die Lautstärke passend zur vergangenen Zeit und gibt den Zeitanteil x (0…1) zurück. */
    private fun applyVolume(): Float {
        val elapsed = SystemClock.elapsedRealtime() - rampStart
        val x = (elapsed.toDouble() / rampDuration).coerceIn(0.0, 1.0)
        val v = ((exp(RAMP_STEEPNESS * x) - 1.0) / (exp(RAMP_STEEPNESS) - 1.0)).toFloat()
        player?.setVolume(v, v)
        _volumePercent.value = (v * 100).toInt()
        return x.toFloat()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.run {
            try { stop() } catch (_: IllegalStateException) {}
            release()
        }
        player = null
        layerPlayer?.run {
            try { stop() } catch (_: IllegalStateException) {}
            release()
        }
        layerPlayer = null
        originalStreamVolume?.let {
            setAlarmStreamVolume(it)
        }
        _volumePercent.value = null
        super.onDestroy()
    }
}
