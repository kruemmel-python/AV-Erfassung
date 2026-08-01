# AV-Erfassung

Offline-Android-App zur Erfassung der Netto-Bearbeitungszeit von Postkisten. Die Umsetzung folgt der Datei `blueprint.md`.

## Enthalten

- automatische Kistennummern im Format `JJJJ-MM-TT-NNN`
- Auswahl der Kistenart: Tagespost, Sachbearbeitung, Rückläufer, Routing, Ablage oder HR-Akte
- verpflichtende numerische Personalnummer vor dem Start jeder Kiste
- exakt eine aktive Kiste und eine aktive Unterbrechung
- Kategorien Pause, Registrierung, Image und Diverse
- verpflichtender Kurz-Hinweis bei Unterbrechungen der Kategorie Diverse
- sofortige lokale Speicherung mit Room/SQLite
- robuste Laufzeitmessung über UTC plus `elapsedRealtime` und Boot-Zähler
- Wiederherstellung, Abschluss oder Löschung eines Fehleintrags nach Neustart
- permanente Statusmeldung während einer laufenden Kiste
- Verlauf, Detailansicht und Tagesstatistik
- UTF-8-CSV-Export einschließlich Kistenart und Personalnummer über die Android-Dateiauswahl
- Auswertung der bearbeiteten Kisten nach Kistenart
- große, kontrastreiche Bedienelemente für kleine Displays
- geschützte Teamleiter-Korrektur abgeschlossener Kisten mit neun Stunden gültigen Schlüsseln
- verpflichtender Änderungsgrund und sichtbares Änderungsprotokoll einschließlich CSV-Export

## Bauen

Voraussetzungen: JDK 17 und Android SDK (Compile SDK 35). Das Ziel-SDK ist Android 14 (API 34), die Mindestversion Android 8 (API 26).

```powershell
.\gradlew.bat testDebugUnitTest assembleRelease
```

Der in diesem Projekt erzeugte Release-Pilotbuild nutzt absichtlich den Android-Debugkeystore, damit die APK direkt installierbar ist. Vor einer öffentlichen Verteilung muss in `app/build.gradle.kts` ein eigener Release-Keystore konfiguriert werden.

## Installieren

```powershell
adb install -r .\AV-Erfassung-release.apk
```

Alternativ kann die APK auf das Gerät kopiert und nach Freigabe von „Unbekannte Apps installieren“ im Dateimanager geöffnet werden.

## Teamleiter-Korrektur

Abgeschlossene Kisten können in der Detailansicht über **TEAMLEITER-MODUS FREISCHALTEN** vollständig korrigiert werden. Nach Eingabe eines gültigen Schlüssels sind Kistenart, Personalnummer, Start und Ende bearbeitbar. Zusätzlich können Pause, Registrierung, Image und Diverse hinzugefügt, geändert oder gelöscht werden. Diverse benötigt weiterhin eine kurze Information. Ein Änderungs- beziehungsweise Löschgrund ist immer Pflicht; die App zeigt das Änderungsprotokoll in der Kistendetailansicht und exportiert es in der CSV-Datei. Zeiten außerhalb der Kistenlaufzeit und sich überschneidende Unterbrechungen werden verhindert.

Der separate Android-Keygenerator wird als `Teamleiter-Keygenerator-release.apk` erzeugt. Jeder Schlüssel läuft exakt neun Stunden nach seiner Erstellung ab. Die Generator-APK darf ausschließlich auf geschützten Teamleiter-Geräten installiert und nicht an Mitarbeiter weitergegeben werden. Der bisherige Windows-Keygenerator unter `Keygenerator/` bleibt optional verfügbar.

## AV-Schichtreport für Windows

Der Ordner `DesktopReport/` enthält eine portable C++/Qt-Anwendung zur Auswertung eines oder mehrerer CSV-Schichtexporte. Sie gruppiert die Daten nach Personalnummer, zeigt Mitarbeiter- und Teamkennzahlen als Diagramme, macht manuelle Änderungen und gelöschte Kisten sichtbar und erstellt QS-Berichte als PDF oder HTML.

Das startfertige Windows-Paket liegt als `AV-Schichtreport-portable.zip` im Projektstamm. Installation und Bedienung sind im bebilderten [Benutzerhandbuch für den AV-Schichtreport](Handbuch/Benutzerhandbuch_AV-Schichtreporter.pdf) beschrieben. Alle dort verwendeten Bildschirmabbildungen enthalten ausschließlich anonymisierte Beispieldaten.

![Teamübersicht des AV-Schichtreports](Handbuch/Bilder_AV-Schichtreport/00_team_uebersicht.png)
