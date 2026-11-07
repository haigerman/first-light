package io.github.haigerman.firstlight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Vollbild-Weckbildschirm, erscheint auch über dem Sperrbildschirm. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val volume by AlarmService.volumePercent.collectAsState()
                // Alarm wurde anderweitig beendet (z. B. über die Benachrichtigung)
                LaunchedEffect(volume) { if (volume == null) finish() }

                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val time = remember { SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date()) }
                        Text(time, fontSize = 72.sp)
                        Spacer(Modifier.height(16.dp))
                        val greeting = remember { AlarmPrefs(this@AlarmActivity).greeting() }
                        Text(greeting, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(32.dp))
                        Text("Lautstärke: ${volume ?: 0} %")
                        LinearProgressIndicator(
                            progress = { (volume ?: 0) / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = { AlarmService.snooze(this@AlarmActivity); finish() },
                            modifier = Modifier.fillMaxWidth().height(96.dp)
                        ) { Text("Schlummern  +${AlarmScheduler.SNOOZE_MINUTES} Min.", fontSize = 24.sp) }
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = { AlarmService.stop(this@AlarmActivity); finish() },
                            modifier = Modifier.fillMaxWidth().height(72.dp)
                        ) { Text("Wecker ausschalten", fontSize = 20.sp) }
                    }
                }
            }
        }
    }
}
