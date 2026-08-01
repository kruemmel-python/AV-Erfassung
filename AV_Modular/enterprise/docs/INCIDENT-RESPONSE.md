# Incident Response für Signaturschlüssel

## Auslöser

Verlust, unberechtigter Zugriff, unerwartete Signaturen, abweichende Paket-/Backup-Digests oder ein nicht erklärbarer Trust-Store-Eintrag lösen den Ablauf sofort aus.

## Verbindlicher Ablauf

1. **Erkennen und eindämmen:** Ausrollung stoppen, Signierpipeline sperren, Beweise und Zeitpunkte sichern.
2. **Schlüssel sperren:** betroffene Key-ID in die zentrale Sperrliste aufnehmen und Sperrstatus über MDM verteilen.
3. **Betroffenheit bestimmen:** alle Paket-, Backup-, Profil-, Mandanten-, Geräte- und Export-IDs seit dem letzten sicher bestätigten Signaturzeitpunkt ermitteln.
4. **Neue Schlüsselgeneration:** neues Ed25519-Schlüsselpaar im HSM/Secret Store erzeugen; privaten Schlüssel niemals exportieren.
5. **Neu signieren:** ausschließlich aus verifiziertem Quellstand neue, höher versionierte Pakete erstellen.
6. **MDM-Neuverteilung:** Trust Store, Sperrliste und neu signierte Pakete gestuft verteilen.
7. **Installation bestätigen:** pro Gerät Paket-ID, Version, neue Key-ID und Prüfergebnis technisch bestätigen.
8. **Daten prüfen:** Auditketten, Backupmanifest, Payload-Digests und Revisionskonflikte seit dem Verdachtszeitpunkt auswerten.
9. **Incident-Bericht:** Ursache, Zeitraum, betroffene Assets, Maßnahmen, Restrestrisiko und Freigabeentscheidung dokumentieren.
10. **Nachbereitung:** Zugang, Rotation, Monitoring und Vier-Augen-Freigabe korrigieren; Wiederholungstest protokollieren.

## Abschlusskriterien

Der Incident darf erst geschlossen werden, wenn der alte Schlüssel überall gesperrt, die neue Vertrauenskette bestätigt, alle betroffenen Paket-IDs bewertet und Audit-/Incident-Bericht durch Betrieb und Revision freigegeben sind.
