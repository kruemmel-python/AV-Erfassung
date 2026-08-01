# Sicherheitskonzept

## Schutzobjekte und Vertrauensgrenzen

Geschützt werden Personalnummern, Schicht- und Prozesszeiten, Korrekturgründe, Kundenkonfigurationen, Schlüsselmaterial und Auditdaten. Eingangsgrenzen sind JSON-Konfigurationen, CSV-Dateien, `.avpkg`-Archive, Android-Managed-Configurations und native Plugins.

## Kontrollen

- `AuthorizationService` erzwingt Mandant, Standort und Rollenberechtigung. Unbekannte Rollen erhalten keinen Zugriff.
- Mitarbeiter dürfen erfassen/exportieren; Teamleiter benötigen explizit `work_item.correct` beziehungsweise `work_item.delete`.
- Korrektur und Löschung verlangen einen Grund und laufen atomar in einer Room-Transaktion.
- Löschung ist fachlich eine Soft-Löschung. Der Datensatz bleibt mit `deleted_for_audit=true` erhalten.
- Die Auditkette enthält Sequenz, Mandant, Ereignis, Akteur, Subjekt, Zeit, Payload-Digest und Vorgänger-Hash. Eine Veränderung ist damit erkennbar.
- Produktionspakete verwenden Ed25519; jede enthaltene Datei ist per SHA-256 gebunden. Zusätzliche, fehlende, doppelte oder unsichere ZIP-Pfade werden abgelehnt.
- Schlüssel-IDs müssen im Trust Store aktiv sein. Ein widerrufener Schlüssel blockiert auch kryptografisch korrekte Pakete und Backups.
- JSON wird strikt typisiert; unbekannte Felder, ungültige Referenzen und Pfadüberschreitungen werden abgelehnt.
- Native Module werden erst nach Paketprüfung geladen; der Host prüft ABI-Version, Modul-ID und vollständige Funktionstabelle.

## Schlüsselbetrieb

Private Schlüssel werden offline in HSM/Enterprise Secret Store erzeugt oder importiert. Nur öffentliche Schlüssel und Sperrstatus werden verteilt. Rotation: neuen Schlüssel hinzufügen, Pakete neu signieren und ausrollen, Aktivierung prüfen, alten Schlüssel sperren. Verlustverdacht führt sofort zur Sperre und Neuverteilung.

## Datenschutz

Die Android-Erfassung arbeitet lokal und besitzt keine Netzwerkberechtigung. Personalnummern dürfen in Supportdiagnosen nicht enthalten sein. Exporte sind personenbezogen und müssen über freigegebene Speicher- und Löschprozesse behandelt werden. Auf verwalteten Geräten sind Gerätesperre, Verschlüsselung, Remote Wipe und App-Daten-Backupverbot verbindlich.

Supportdiagnosen entsprechen ausschließlich `av-support-diagnostic-v1`. Das JSON-Schema lehnt zusätzliche Felder ab; zugelassen sind nur Versionen, IDs ohne Personenbezug, Zeitzone und fest definierte technische Zähler.

Der verbindliche Ablauf bei Schlüsselkompromittierung steht in [INCIDENT-RESPONSE.md](INCIDENT-RESPONSE.md).

## Bekannte Integrationspflichten

Enterprise SSO, zentrale Aufbewahrung, SIEM-Weiterleitung und serverseitige Unveränderbarkeit benötigen Zielsysteme des Betreibers. Die lokale Hashkette ersetzt keine WORM-Ablage; sie ermöglicht deren Integritätsprüfung.
