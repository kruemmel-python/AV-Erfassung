# AV-Schichtreport 1.1.0 (Windows Desktop)

Die C++/Qt-Desktop-App importiert einen oder mehrere CSV-Schichtexporte der Android-App **AV-Erfassung** und stellt sie verständlich als Mitarbeiter- und Teamauswertung dar.

## Funktionen

- gleichzeitiger Import vieler CSV-Dateien oder Drag-and-drop
- automatische Gruppierung nach Personalnummer
- Deduplizierung erneut importierter Schichten; der zuletzt importierte Datensatz ist maßgeblich
- Kennzahlen für einzelne Mitarbeiter und das Gesamtteam
- Diagramme nach Kistenart sowie zur durchschnittlichen Nettozeit
- chronologische Schichtansicht
- eigener Prüfbereich für manuelle Änderungen und gelöschte Kisten
- gelöschte Kisten werden aus regulären Leistungskennzahlen ausgeschlossen
- automatisch formulierter QS-Bericht
- Export des QS-Berichts als PDF oder HTML

## Bedienung

1. `AV-Schichtreport.exe` starten.
2. **CSV-DATEIEN IMPORTIEREN** wählen oder CSV-Dateien auf das Fenster ziehen.
3. Unter **AUSWERTUNG** „Gesamtteam“ oder eine Personalnummer auswählen.
4. Zwischen **ÜBERSICHT**, **SCHICHTDATEN**, **ÄNDERUNGEN** und **QS-BERICHT** wechseln.
5. Im QS-Reiter den Bericht bei Bedarf als PDF oder HTML speichern.

Die Quelldateien werden nur gelesen und niemals verändert. Es erfolgt keine automatische Datenübertragung.

## Build

Voraussetzungen: Qt 5.15 mit MinGW unter `C:\msys64\mingw64`.

```powershell
.\build-portable.ps1
```

Das Buildskript kompiliert und startet zuerst die Importtests, baut anschließend die Anwendung mit `-Wall -Wextra -Werror` und erzeugt das portable Paket unter `dist\AV-Schichtreport-portable.zip`.

## Bebildertes Handbuch

Das ausführliche Benutzerhandbuch erklärt Installation, Mehrfachimport, Mitarbeiter- und Teamauswertung, Änderungsprüfung sowie den QS-Export anhand anonymisierter Bildschirmabbildungen:

- [Benutzerhandbuch als PDF](../Handbuch/Benutzerhandbuch_AV-Schichtreporter.pdf)
- [Bearbeitbare Word-Fassung](../Handbuch/Benutzerhandbuch_AV-Schichtreporter.docx)
