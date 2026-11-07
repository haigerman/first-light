<img src="docs/icon.png" width="128" alt="App-Icon: Sonnenaufgang über dem Wasser">

# Ocean Breeze Clock

Android-Wecker-App, die zur eingestellten Zeit ein Musikstück abspielt und die Lautstärke ganz langsam steigert – für ein sanftes Aufwachen.

## Funktionen

- **Weckzeit** im 24-Stunden-Format, einmalig oder täglich wiederholt
- **Sanfter Lautstärkeanstieg:** gleichmäßig (linear) von 0 % auf 100 % über die Länge des Musikstücks (Standardstück: 2:32), danach in Schleife bei 100 %
- **Eigenes Musikstück** (beliebige Audiodatei), sonst das mitgelieferte Stück „First Light on the Water“
- **Persönliche Begrüßung:** „Guten Morgen, ‹Name› ☀️“ bzw. „Guten Morgen!“ ohne Namen
- **Weckbildschirm über dem Sperrbildschirm** mit
  - **Schlummern (+7 Min.)** – klingelt nach 7 Minuten erneut, wieder ab 0 %
  - **Wecker ausschalten**
- Beide Aktionen auch direkt in der Benachrichtigung
- Klingelt auch, wenn das Handy lautlos ist (eigener Wecker-Audiokanal)
- Wecker und Schlummern überstehen Neustart, App-Update und Zeitumstellung
- Einstellungen (Zahnrad oben rechts): Name und Musikstück
- „Jetzt testen“ zum Ausprobieren des Weckablaufs

Mindestens Android 8.0 (API 26), Ziel-API 35.

## Bauen

Voraussetzungen: JDK 17+ und das Android SDK (Platform 35).

```bash
./gradlew assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.
Alternativ das Projekt einfach in Android Studio öffnen.

Ohne Android Studio muss der SDK-Pfad in `local.properties` stehen (wird nicht eingecheckt):

```
sdk.dir=/pfad/zum/android-sdk
```

## Einrichtung am Handy

Beim ersten Start **Benachrichtigungen erlauben**. Fehlen weitere Berechtigungen (exakte Wecker, Vollbild-Anzeige), zeigt die App einen Hinweis mit „Erlauben“-Knopf.

### Energiesparen

Auf normalem Android (z. B. Pixel) ist keine Ausnahme nötig – die App nutzt `setAlarmClock()`, das auch im Doze-Modus pünktlich auslöst.
Manche Hersteller beenden Apps aber aggressiv, wodurch ihre Wecker gelöscht werden. Dort empfiehlt sich:

| Hersteller | Einstellung |
|---|---|
| Samsung | Akku → Hintergrundnutzungslimits: App unter „Nie schlafende Apps“ |
| Xiaomi / Redmi / POCO | Autostart an, Akkusparmodus „Keine Einschränkungen“ |
| Huawei / Honor | Akku → App-Start: „Manuell verwalten“, alle Schalter an |
| Oppo / Realme / OnePlus | App-Info → Akku: Hintergrundaktivität erlauben |

Details je Modell: https://dontkillmyapp.com

### „Nicht stören“

Wecker sind im „Nicht stören“-Modus standardmäßig erlaubt. Sind Wecker dort ausdrücklich als Ausnahme gesperrt, bleibt auch diese App still.

## Aufbau

| Datei | Aufgabe |
|---|---|
| `MainActivity.kt` | Hauptbildschirm: Weckzeit, An/Aus, Wiederholung, Test |
| `SettingsActivity.kt` | Einstellungen: Name, Musikstück |
| `AlarmActivity.kt` | Weckbildschirm mit Schlummern / Ausschalten |
| `AlarmService.kt` | Wiedergabe und Lautstärkeanstieg (Vordergrunddienst) |
| `AlarmScheduler.kt` | Stellt Wecker und Schlummer-Wecker über den `AlarmManager` |
| `AlarmReceiver.kt` | Wird zur Weckzeit ausgelöst |
| `BootReceiver.kt` | Stellt Wecker nach Neustart/Zeitänderung neu |
| `AlarmPrefs.kt` | Gespeicherte Einstellungen |

Die Lautstärke-Parameter stehen als Konstanten in `AlarmService.kt` (`FALLBACK_RAMP_MS` für Stücke ohne verwertbare Länge), die Schlummerdauer in `AlarmScheduler.kt` (`SNOOZE_MINUTES`).
