package io.github.haigerman.firstlight

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AlarmPrefs(context) }

    var name by remember { mutableStateOf(prefs.userName) }
    var musicName by remember { mutableStateOf(prefs.musicName) }
    var sound by remember { mutableStateOf(prefs.sound) }

    val pickMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // Berechtigung für die vorherige Datei freigeben
            prefs.musicUri?.takeIf { it != uri }?.let { old ->
                try {
                    context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {}
            }
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.musicUri = uri
            prefs.musicName = displayName(context, uri)
            musicName = prefs.musicName
            prefs.sound = AlarmPrefs.Sound.CUSTOM
            sound = AlarmPrefs.Sound.CUSTOM
        }
    }

    fun choose(s: AlarmPrefs.Sound) {
        if (s == AlarmPrefs.Sound.CUSTOM && prefs.musicUri == null) {
            pickMusic.launch(arrayOf("audio/*")) // erst nach Auswahl einer Datei aktiv
        } else {
            prefs.sound = s
            sound = s
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            // --- Name ---
            Text("Dein Name", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; prefs.userName = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "So wirst du geweckt: „${AlarmPrefs.greetingFor(name)}“",
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider(Modifier.padding(vertical = 24.dp))

            // --- Wecksound ---
            Text("Dein Sound zum Start in den Tag", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                SoundOption("First Light (Standard)", null, sound == AlarmPrefs.Sound.FIRST_LIGHT) {
                    choose(AlarmPrefs.Sound.FIRST_LIGHT)
                }
                SoundOption("Vogelgesang", null, sound == AlarmPrefs.Sound.BIRDSONG) {
                    choose(AlarmPrefs.Sound.BIRDSONG)
                }
                SoundOption(
                    "Eigene Musik",
                    musicName ?: "Noch keine Datei gewählt",
                    sound == AlarmPrefs.Sound.CUSTOM
                ) { choose(AlarmPrefs.Sound.CUSTOM) }
            }
            OutlinedButton(
                onClick = { pickMusic.launch(arrayOf("audio/*")) },
                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
            ) { Text(if (musicName == null) "Datei auswählen" else "Andere Datei wählen") }
        }
    }
}

@Composable
private fun SoundOption(title: String, subtitle: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
