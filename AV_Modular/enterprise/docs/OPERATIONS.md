# Betriebshandbuch

## Betriebsrollen

- Plattformbetrieb: Release, Trust Store, MDM und Monitoring
- Fachadministration: Module, Ziele, Regeln und Reports im AV Designer
- Teamleitung: begründete Korrekturen/Löschungen und QS-Prüfung
- Revision: lesender Audit- und Exportzugriff

## Freigabeprozess

1. Konfiguration im Designer bearbeiten und ohne Fehler validieren.
2. automatisierte Tests und vollständigen Build ausführen.
3. Paket in isolierter Build-Umgebung mit aktivem Produktionsschlüssel signieren.
4. Paket gegen den veröffentlichten Trust Store prüfen.
5. Pilotgruppe über MDM ausrollen und Modul-/Profil-/Standort-ID kontrollieren.
6. QS-Smoke-Test, Export und Wiederanlauf nach Prozessabbruch prüfen.
7. gestuft ausrollen; Paketversion, Appversion und Prüfsumme protokollieren.

## Berichtsexport

`av-work-record-v2` dient ausschließlich QS, Reporting, Nachweis und kontrollierter Datenweitergabe. Der CSV-Import kann fachliche Arbeitsdatensätze zusammenführen, einschließlich Record-ID, Revision und Löschkennzeichen. Er ist kein vollständiges Datenbankbackup und stellt weder Unterbrechungsdetails noch die ursprüngliche Auditverkettung wieder her.

## Vollständiges Betriebsbackup

Eine beweiswerterhaltende Sicherung verwendet ausschließlich `av-evidence-backup-v1`. Das signierte Format muss Schichten, Vorgänge, Unterbrechungen, Korrekturen, Löschmarkierungen, vollständige Auditereignisse, Audit-Head-Hash sowie Konfigurations-, Paket-, Core-, Modul- und Room-Schemaversion enthalten. Fehlt eine vorgeschriebene logische Datei, ist die Sicherung technisch ungültig.

Vor Wiederherstellung werden Ed25519-Signatur, Trust Store, Sperrliste, alle SHA-256-Prüfsummen, Mandant, Gerät, Paketformat und Kompatibilitätsmatrix geprüft. Erst danach dürfen Daten in eine leere, kompatible Datenbank eingespielt werden. Der erzeugte Audit-Head muss anschließend exakt dem Manifest entsprechen. CSV-/Legacy-Import darf niemals als beweiswerterhaltende Wiederherstellung bezeichnet oder verwendet werden.

Android-Systembackup bleibt deaktiviert. Berichtsexporte und signierte Betriebsbackups besitzen getrennte Ablage-, Zugriffs- und Aufbewahrungsrichtlinien.

## Monitoring

Zu überwachen sind fehlgeschlagene Paketprüfungen, unbekannte Schlüssel, Audit-Sequenzlücken, ungewöhnlich viele Korrekturen/Löschungen, aktive Vorgänge zum Schichtende, Importwarnungen und Zielzeitabweichungen. Technische Logs dürfen keine Personalnummern oder fachlichen Inhalte enthalten.

## Störungen

- Paket abgelehnt: Signatur, Prüfsumme, Schlüssel-ID und Sperrliste prüfen; niemals Prüfung umgehen.
- Konfiguration ungültig: Designerbefunde korrigieren und neue Paketversion erstellen.
- Aktiver Vorgang nach Absturz: App erneut öffnen; Room stellt den aktiven Zustand wieder her.
- Falsche Erfassung: Teamleiter nutzt Vollkorrektur/Soft-Löschung mit nachvollziehbarem Grund.
- Auditabweichung: Export stoppen, Gerät isolieren, Daten sichern und Revision informieren.

## Aufbewahrung

Aufbewahrungs- und Löschfristen sind mandantenspezifisch festzulegen. Soft-gelöschte Vorgänge bleiben bis zum Ablauf der Revisionsfrist sichtbar. Danach löscht ausschließlich ein dokumentierter administrativer Retention-Prozess den gesamten abgeschlossenen Aufbewahrungsbestand.
