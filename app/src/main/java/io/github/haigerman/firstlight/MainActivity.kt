package io.github.haigerman.firstlight

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkThemeCompat()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { WeckerScreen() }
            }
        }
    }

    private fun isSystemInDarkThemeCompat(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeckerScreen() {
    val context = LocalContext.current
    val prefs = remember { AlarmPrefs(context) }

    var enabled by remember { mutableStateOf(prefs.enabled) }
    var daily by remember { mutableStateOf(prefs.daily) }
    var nextAlarm by remember { mutableStateOf<Long?>(null) }
    var snoozeUntil by remember { mutableStateOf(prefs.snoozeUntil) }
    val timeState = rememberTimePickerState(prefs.hour, prefs.minute, is24Hour = true)
    val playing by AlarmService.volumePercent.collectAsState()

    // Berechtigungen, die beim Zurückkehren aus den Einstellungen neu geprüft werden
    var canExact by remember { mutableStateOf(AlarmScheduler.canScheduleExact(context)) }
    var canFullScreen by remember { mutableStateOf(canUseFullScreen(context)) }
    var hasNotif by remember { mutableStateOf(hasNotificationPermission(context)) }

    fun reschedule() {
        prefs.hour = timeState.hour
        prefs.minute = timeState.minute
        prefs.enabled = enabled
        prefs.daily = daily
        nextAlarm = AlarmScheduler.apply(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                canExact = AlarmScheduler.canScheduleExact(context)
                canFullScreen = canUseFullScreen(context)
                hasNotif = hasNotificationPermission(context)
                snoozeUntil = prefs.snoozeUntil
                enabled = prefs.enabled // kann sich durch einmaligen Alarm geändert haben
                nextAlarm = AlarmScheduler.apply(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Bei jeder Änderung der Uhrzeit neu stellen
    LaunchedEffect(timeState.hour, timeState.minute) { reschedule() }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasNotif = it
    }

    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing) // nicht unter Status- und Navigationsleiste
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("First Light", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
            }
        }
        Spacer(Modifier.height(24.dp))

        TimePicker(state = timeState)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Wecker aktiv", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                if (it && !hasNotif && Build.VERSION.SDK_INT >= 33) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                reschedule()
            })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Täglich wiederholen", Modifier.weight(1f))
            Switch(checked = daily, onCheckedChange = { daily = it; reschedule() })
        }

        Text(
            when {
                !enabled -> "Kein Wecker gestellt"
                nextAlarm != null -> "Klingelt am " +
                    SimpleDateFormat("EEEE, dd.MM. 'um' HH:mm 'Uhr'", Locale.GERMANY).format(Date(nextAlarm!!))
                else -> "Wecker kann nicht gestellt werden – Berechtigung fehlt"
            },
            Modifier.padding(vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )

        if (snoozeUntil > System.currentTimeMillis()) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Schlummert – klingelt wieder um " +
                            SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(snoozeUntil)) + " Uhr",
                        Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        AlarmScheduler.cancelSnooze(context)
                        snoozeUntil = 0L
                    }) { Text("Abbrechen") }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text(
            "Die Lautstärke steigt während des ersten Durchlaufs des Musikstücks gleichmäßig von 0 % auf 100 % " +
                "und bleibt danach bei 100 %.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))
        if (playing == null) {
            Button(onClick = {
                ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
            }) { Text("Jetzt testen") }
        } else {
            Button(onClick = { AlarmService.stop(context) }) { Text("Test stoppen ($playing %)") }
        }

        // Hinweise auf fehlende Berechtigungen
        if (!canExact) PermissionHint("Exakte Wecker sind nicht erlaubt.", "Erlauben") {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
        if (!hasNotif && Build.VERSION.SDK_INT >= 33) PermissionHint("Benachrichtigungen sind deaktiviert.", "Erlauben") {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!canFullScreen) PermissionHint("Der Weckbildschirm darf nicht im Vollbild erscheinen.", "Erlauben") {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
        }
    }
}

@Composable
private fun PermissionHint(text: String, button: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f))
            TextButton(onClick = onClick) { Text(button) }
        }
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun canUseFullScreen(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

