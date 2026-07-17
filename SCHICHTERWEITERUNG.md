# Schichtbasierte AV-Erfassung – technische Übergabe

## Architektur und Migration

Die frühere Erfassung war kisten- und kalendertagbezogen. Die neue Version trennt vier Bereiche:

- `ShiftResolver`: zentrale Schichtfenster für `Europe/Berlin`, einschließlich Montag, Überlappungen und Sommer-/Winterzeit.
- Room-Persistenz: `ShiftEntity`, echte `BoxEntity`-Datensätze und eigenständige `WorkProcessEntity`-Datensätze.
- `ShiftStatisticsService`: schichtbezogene Berechnung ohne Doppelzählung.
- `CsvExportService`: semikolongetrennte UTF-8-Detaildaten und Schichtzusammenfassungen.

Die Datenbankmigration 4→5 legt vor dem Öffnen eine lokale Sicherung der Datenbank samt vorhandener WAL-/SHM-Dateien an. Alte Kisten behalten ihre bisherige Nummer in `legacy_box_id`. Eindeutige Datensätze werden einer Schicht zugeordnet; mehrdeutige Zuordnungen werden markiert und bleiben über die Teamleiter-Bearbeitung korrigierbar.

Der bekannte Datensatz `2026-07-16-003` wird nicht als echte Kiste gezählt. Er wird der Nachtschicht vom 16.07.2026 zugeordnet und als historischer Registrierungsprozess mit `02:44:10` erhalten.

## Bedien- und Prozesslogik

- Vor der ersten Tätigkeit werden Schicht und numerische Personalnummer bestätigt.
- Registrierung, Pause, Image, Diverse, Vorbereitung und Abschluss sind eigenständige Prozesse.
- Diverse verlangt einen kurzen Hinweis.
- Registrierung während einer Kiste unterbricht die Kiste nach Bestätigung; nach dem Ende kann die Kiste fortgesetzt, unterbrochen gelassen oder beendet werden.
- Nach dem Ende jeder echten Kiste startet exakt zum Kistenende automatisch ein Kistenwechsel.
- Der Kistenwechsel endet erst beim tatsächlichen Start der Folgetätigkeit. Das bloße Öffnen oder Abbrechen der Kistenauswahl beendet ihn nicht.
- Ab 20 Minuten wird ein laufender Kistenwechsel sichtbar hervorgehoben, aber nicht automatisch umklassifiziert.
- Verlauf, Kennzahlen und Export verwenden Schichten statt Kalendertage.

## Teamleiter

Der neun Stunden gültige Schlüssel schaltet die manuelle Bearbeitung frei. Bearbeitbar sind Kistenart, Personalnummer, Kistenzeiten, Pausen und weitere Unterbrechungen, Schichtzuordnung sowie sämtliche Arbeitsprozesse. Ganze abgeschlossene Kisten können nach Eingabe eines Löschgrunds gelöscht werden. Sie bleiben zur Nachvollziehbarkeit als rot markierte, nicht mehr mitgezählte Datensätze im Verlauf und Schichtbericht erhalten. Prozesse können hinzugefügt, zeitlich geändert, umklassifiziert, einer anderen Schicht zugeordnet, mit Hinweisen oder Kistenwechsel-Referenzen versehen und storniert werden. Änderungen werden protokolliert und fließen unmittelbar in Statistik und CSV ein.

## Tests und Build

Automatisiert geprüft werden unter anderem reguläre und montagsbedingte Überlappungen, Nacht-/Datumswechsel, DST, Zeitberechnung, Teamleiter-Schlüssel, manuelle Intervalle, die Referenz-Nachtschicht, Kistenwechselstatistik sowie CSV-Spalten, Null-Kisten und Maskierung. Ergebnis: 22 Tests, 0 Fehler.

Release: AV-Erfassung `2.0.2` (`versionCode 10`). Beide APKs sind mit APK Signature Scheme v2 signiert.

## Manuelle Nachprüfung nach Update

In Überlappungsbereichen oder außerhalb eines Schichtfensters kann eine Altdatenzuordnung als mehrdeutig markiert sein. Der Teamleiter öffnet die betreffende Kiste im schichtgruppierten Verlauf und verwendet **SCHICHTZUORDNUNG ÄNDERN**. Die Originalnummer bleibt im Export als „Alte Kisten-ID“ erhalten.
