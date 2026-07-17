Ja, **genau dafür eignet sich der PECSU-Player gut**. Da Android 14 darauf läuft, kannst du eine selbst entwickelte APK installieren – entweder direkt über eine APK-Datei oder über ADB. Voraussetzung ist lediglich, dass der Hersteller die Installation unbekannter Apps beziehungsweise die Entwickleroptionen nicht gesperrt hat. Bei einem normalen offenen Android-System ist das problemlos möglich.

Deine Arbeits-App lässt sich vollständig **offline und ohne Cloud** umsetzen. Sie braucht keine Kamera, keine SIM-Karte und grundsätzlich auch keine Internetverbindung.

# Grundidee der App

Die App verwaltet jede Postkiste als eigenen Arbeitsvorgang:

```text
Kiste starten
    ↓
Arbeitszeit läuft
    ↓
Unterbrechung starten
    ├── Pause
    ├── Registrierung
    ├── Image
    └── Diverse
    ↓
Unterbrechung beenden
    ↓
Arbeitszeit läuft weiter
    ↓
Kiste beenden
    ↓
Netto-Bearbeitungszeit wird berechnet
```

Die Berechnung lautet:

```text
Bruttozeit = Endzeit − Startzeit

Unterbrechungszeit =
    Pause
  + Registrierung
  + Image
  + Diverse

Nettozeit = Bruttozeit − Unterbrechungszeit
```

## Beispiel

```text
Kiste gestartet:       08:10 Uhr
Registrierung:          08:35–08:47 Uhr = 12 Minuten
Image:                  09:02–09:10 Uhr =  8 Minuten
Kiste beendet:          09:30 Uhr

Bruttozeit:             80 Minuten
Unterbrechungen:        20 Minuten
Netto-Bearbeitungszeit: 60 Minuten
```

# Bedienung auf dem kleinen Display

Da der Player nur ein 4-Zoll-Display hat, sollte die App mit sehr großen Schaltflächen und möglichst wenigen Menüs arbeiten.

## Startbildschirm ohne aktive Kiste

```text
┌─────────────────────────────┐
│      POSTKISTEN-TRACKER     │
│                             │
│     Keine aktive Kiste      │
│                             │
│  ┌───────────────────────┐  │
│  │   NEUE KISTE STARTEN  │  │
│  └───────────────────────┘  │
│                             │
│  Heute: 18 Kisten           │
│  Nettozeit: 5:42 Stunden    │
│                             │
│  Verlauf       Tagesbericht │
└─────────────────────────────┘
```

Beim Start könnte optional eine Nummer eingegeben werden:

```text
Kistennummer: 4711
```

Die Nummer kann aber auch automatisch erzeugt werden:

```text
Kiste 2026-07-15-001
Kiste 2026-07-15-002
Kiste 2026-07-15-003
```

Damit musst du während der Arbeit nichts eintippen.

# Bildschirm während der Bearbeitung

```text
┌─────────────────────────────┐
│ KISTE 2026-07-15-007        │
│                             │
│ Gestartet: 13:42:18         │
│                             │
│ Aktive Nettozeit            │
│        00:18:37             │
│                             │
│ ┌───────────┐ ┌───────────┐ │
│ │   PAUSE   │ │REGISTRIER.│ │
│ └───────────┘ └───────────┘ │
│                             │
│ ┌───────────┐ ┌───────────┐ │
│ │   IMAGE   │ │  DIVERSE  │ │
│ └───────────┘ └───────────┘ │
│                             │
│ ┌─────────────────────────┐ │
│ │      KISTE BEENDEN      │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘
```

Die Zeit wird automatisch erfasst. Es muss keine Uhrzeit manuell eingegeben werden.

# Bildschirm während einer Unterbrechung

Wird beispielsweise „Registrierung“ gedrückt:

```text
┌─────────────────────────────┐
│ KISTE 2026-07-15-007        │
│                             │
│      UNTERBRECHUNG          │
│                             │
│      REGISTRIERUNG          │
│                             │
│ Gestartet: 14:00:55         │
│ Dauer:     00:04:31         │
│                             │
│ ┌─────────────────────────┐ │
│ │    ARBEIT FORTSETZEN    │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘
```

Beim Drücken von **„Arbeit fortsetzen“** wird die Unterbrechung automatisch beendet und gespeichert.

Danach läuft die normale Bearbeitungszeit weiter.

# Kiste beenden

Beim Beenden erscheint zunächst eine Bestätigung:

```text
Kiste wirklich beenden?

Start:                 13:42:18
Ende:                  14:31:44
Bruttozeit:            49:26
Unterbrechungen:       09:12
Nettozeit:             40:14

[ABBRECHEN]       [BEENDEN]
```

Falls beim Beenden noch eine Unterbrechung aktiv ist, sollte die App diese automatisch beenden:

```text
Die aktive Unterbrechung „Image“
wird zusammen mit der Kiste beendet.
```

So kann keine offene Unterbrechung übrig bleiben.

# Unterbrechungen detailliert speichern

Jede einzelne Unterbrechung wird getrennt gespeichert:

```text
Kiste 2026-07-15-007

Start: 13:42:18
Ende:  14:31:44

Unterbrechungen:

Registrierung
14:00:55–14:05:26
Dauer: 4:31

Image
14:17:02–14:21:43
Dauer: 4:41

Gesamte Unterbrechungszeit: 9:12
Netto-Bearbeitungszeit:    40:14
```

Dadurch können später nicht nur die Gesamtzeiten, sondern auch die Ursachen ausgewertet werden.

# Tagesübersicht

Eine Tagesansicht wäre für die Arbeit besonders nützlich:

```text
15. Juli 2026

Bearbeitete Kisten:       23

Gesamte Bruttozeit:     07:12:45
Pause:                  00:32:00
Registrierung:          00:27:18
Image:                  00:41:09
Diverse:                00:13:44

Unterbrechungen gesamt: 01:54:11
Netto-Arbeitszeit:      05:18:34

Durchschnitt je Kiste:     13:50
Schnellste Kiste:          06:21
Langsamste Kiste:          29:44
```

Damit kannst du nachvollziehen:

* wie viele Kisten du bearbeitet hast,
* wie lange du effektiv gebraucht hast,
* wie viel Zeit Registrierung beansprucht,
* wie viel Zeit für Image-Vorgänge anfällt,
* wie viel Zeit durch sonstige Unterbrechungen entsteht,
* welche Kisten außergewöhnlich lange dauerten.

# Wichtige technische Regeln

## Nur eine aktive Kiste

Für die erste Version würde ich immer nur eine aktive Kiste erlauben.

```text
Es läuft bereits eine Kiste.
Bitte beende die aktuelle Kiste zuerst.
```

Das verhindert versehentliche parallele Zeitmessungen.

## Nur eine aktive Unterbrechung

Innerhalb einer Kiste darf ebenfalls nur eine Unterbrechung gleichzeitig aktiv sein.

Wird während „Registrierung“ auf „Image“ gedrückt, kann die App fragen:

```text
Registrierung beenden und Image starten?
```

Bei Bestätigung passiert automatisch:

```text
Registrierung Ende: aktuelle Uhrzeit
Image Start:        gleiche aktuelle Uhrzeit
```

So entstehen keine überlappenden Zeiten.

## Speicherung bei jedem Tastendruck

Jeder Zustand wird sofort lokal gespeichert:

* Kiste gestartet
* Unterbrechung gestartet
* Unterbrechung beendet
* Kiste beendet

Die Daten dürfen nicht erst beim Schließen der Kiste gespeichert werden. Sonst könnten sie bei einem Absturz oder leeren Akku verloren gehen.

## Wiederherstellung nach Neustart

Wird der Player während einer laufenden Kiste ausgeschaltet, muss die App nach dem Neustart erkennen:

```text
Kiste 2026-07-15-007 läuft seit 13:42 Uhr.

[WEITERFÜHREN]
[KISTE JETZT BEENDEN]
[FEHLEINTRAG KORRIGIEREN]
```

Die Zeit läuft anhand der gespeicherten Zeitstempel weiter. Die App muss daher nicht permanent im Vordergrund geöffnet bleiben.

# Empfohlene Datenstruktur

Technisch reichen zwei zentrale Datentabellen.

## Tabelle `boxes`

```text
id
display_number
started_at
ended_at
status
created_at
updated_at
```

Beispiel:

```text
id:             17
display_number: 2026-07-15-007
started_at:     2026-07-15T13:42:18+02:00
ended_at:       2026-07-15T14:31:44+02:00
status:         FINISHED
```

## Tabelle `interruptions`

```text
id
box_id
type
started_at
ended_at
optional_note
```

Beispiel:

```text
id:            39
box_id:        17
type:          REGISTRATION
started_at:    2026-07-15T14:00:55+02:00
ended_at:      2026-07-15T14:05:26+02:00
optional_note: null
```

Die Nettozeit wird aus den Rohdaten berechnet. Sie sollte nicht ausschließlich als festes Ergebnis gespeichert werden, weil sie sonst nach einer Korrektur möglicherweise nicht mehr zu den Zeitstempeln passt.

# Zustandsmodell

Intern sollte die App mit eindeutigen Zuständen arbeiten:

```text
IDLE
    Keine aktive Kiste

WORKING
    Kiste läuft, keine Unterbrechung

INTERRUPTED
    Kiste läuft, Unterbrechung aktiv

FINISHED
    Kiste abgeschlossen
```

Die Übergänge:

```text
IDLE
  └── Kiste starten
        ↓
WORKING
  ├── Unterbrechung starten
  │       ↓
  │   INTERRUPTED
  │       └── Fortsetzen
  │              ↓
  │          WORKING
  │
  └── Kiste beenden
          ↓
      FINISHED
          ↓
         IDLE
```

Ein solches Zustandsmodell verhindert viele typische Fehler:

* zwei gleichzeitig aktive Kisten,
* zwei gleichzeitig aktive Unterbrechungen,
* Endzeit vor Startzeit,
* beendete Unterbrechung ohne Start,
* abgeschlossene Kiste mit offener Unterbrechung.

# Sinnvolle Zusatzfunktionen

## CSV-Export

Die App sollte Tages- oder Monatsdaten als CSV exportieren können:

```csv
Kiste,Start,Ende,Brutto,Pause,Registrierung,Image,Diverse,Netto
2026-07-15-001,06:12:04,06:25:31,00:13:27,00:00:00,00:02:14,00:00:00,00:00:00,00:11:13
2026-07-15-002,06:26:05,06:39:44,00:13:39,00:00:00,00:00:00,00:03:02,00:00:00,00:10:37
```

Diese Datei lässt sich später mit Excel öffnen.

## Korrekturmodus

Fehleingaben sollten korrigierbar sein, aber nicht zu leicht versehentlich verändert werden.

Beispielsweise:

```text
Eintrag bearbeiten
→ Grund für Änderung eingeben
→ Zeit korrigieren
→ ursprüngliche Zeit im Änderungsprotokoll behalten
```

Das ist besonders sinnvoll, falls die Daten später als Arbeitsnachweis verwendet werden.

## Große Bedienelemente

Für den Arbeitsalltag:

* große Schaltflächen,
* keine kleinen Symbole ohne Beschriftung,
* hoher Kontrast,
* Bedienung mit einer Hand,
* keine verschachtelten Menüs,
* Display bleibt während aktiver Kiste optional eingeschaltet,
* kurzer Vibrationsimpuls bei Start und Ende.

## Automatische Sicherung

Die Datenbank kann regelmäßig lokal gesichert werden:

```text
Interner Speicher/
PostkistenTracker/
    backup_2026-07-15.db
    export_2026-07-15.csv
```

Eine Cloud ist dafür nicht erforderlich.

# Technische Umsetzung

Für dieses Gerät würde ich verwenden:

```text
Sprache:              Kotlin
Oberfläche:           Jetpack Compose
Datenbank:            Room / SQLite
Zeitdarstellung:      java.time
Architektur:          ViewModel + Repository
Hintergrundstatus:    Foreground Service
Export:               CSV über Android Storage Access Framework
Mindestversion:       Android 8
Zielversion:          Android 14
```

Die App selbst wäre sehr klein. Selbst mit Datenbank, Tagesstatistik und CSV-Export dürfte sie deutlich unter 20 MB bleiben.

## Warum ein Foreground Service sinnvoll ist

Während eine Kiste läuft, kann Android eine permanente Statusmeldung anzeigen:

```text
Postkiste läuft
Start: 13:42 Uhr
Aktuelle Nettozeit: 00:18:37
```

Dadurch beendet Android die App nicht unnötig im Hintergrund. Trotzdem sind die gespeicherten Zeitstempel die eigentliche Wahrheit – nicht ein sekündlich hochgezählter Zähler.

# Präzise Zeitmessung

Für die Anzeige und Dokumentation werden normale Zeitstempel gespeichert:

```text
2026-07-15T13:42:18.427+02:00
```

Für die laufende Dauer sollte zusätzlich eine monotone Android-Zeit verwendet werden. Das schützt vor Problemen, wenn:

* die Systemuhr automatisch korrigiert wird,
* die Zeitzone geändert wird,
* jemand die Uhrzeit manuell verstellt.

Eine robuste Speicherung könnte deshalb enthalten:

```text
started_at_utc
started_elapsed_realtime
boot_identifier
```

Solange das Gerät nicht neu gestartet wurde, wird die Dauer über die monotone Uhr berechnet. Nach einem Neustart erfolgt die Wiederherstellung anhand der UTC-Zeitstempel.

# Installation deiner eigenen App

Nach Fertigstellung wird eine APK erzeugt:

```text
PostkistenTracker-release.apk
```

Installation direkt auf dem Player:

```text
Einstellungen
→ Apps
→ Spezieller App-Zugriff
→ Unbekannte Apps installieren
→ Dateimanager erlauben
```

Danach:

```text
APK auf den Player kopieren
→ Datei antippen
→ Installieren
```

Alternativ über ADB:

```powershell
adb devices
adb install .\PostkistenTracker-release.apk
```

Die App ist technisch klar abgrenzbar und für eine erste produktive Version überschaubar. Ich würde die Kategorien **Pause, Registrierung, Image und Diverse** fest integrieren, jede Unterbrechung einzeln protokollieren und die Nettozeit automatisch aus den unveränderten Rohzeitstempeln berechnen.
