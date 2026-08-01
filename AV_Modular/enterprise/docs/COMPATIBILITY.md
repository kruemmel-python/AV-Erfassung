# Kompatibilitätsmatrix

Diese Matrix ist Teil der technischen Freigabe. Nicht aufgeführte Kombinationen sind nicht freigegeben.

| Komponente | Freigegebene Version | Kompatibilitätsaussage |
| --- | --- | --- |
| Platform Core | 1.x | verarbeitet Modul-/Profilverträge Schema 1 |
| Konfigurationspaket | `.avpkg` Format 1 | Ed25519, SHA-256, aktiver Trust-Store-Key |
| Evidenzbackup | `av-evidence-backup-v1` | Room Schema 2, Package Format 1, Core 1.x |
| Berichtsexport | `av-work-record-v2` | Capture 1.x und Reporter 1.x |
| Legacy-Bericht | `av-work-record-v1` | nur Import mit Warnung; kein revisionsfähiger Export |
| Plugin ABI | C-ABI v1 | Host 1.x; Paketprüfung muss vor DLL-Laden erfolgen |
| Room | Schema 2 | Migration 1→2 vorhanden; kein destruktiver Downgrade |
| AV Designer | 1.x | Module/Profile für Core 1.x |
| AV Capture | 1.x | Android API 26–36, Modulmanifest Schema 1 |
| AV Reporter | 1.x | CSV v2; Konflikte führen zum Abbruch |
| Diagnose | `av-support-diagnostic-v1` | zusätzliche Felder verboten |

## Freigaberegeln

- Major-Versionen dürfen nur nach dokumentierter Migration gemischt werden.
- Ein höheres Room-Schema darf nicht mit einer älteren Capture-Version geöffnet werden.
- Backupwiederherstellung benötigt exakte Evidenzbackup-, Room- und Paketformatkompatibilität.
- Ein neuer CSV-Vertrag benötigt parallele Importtests für mindestens die unmittelbar vorherige Version.
- ABI-v1-Plugins bleiben innerhalb Core 1.x binär kompatibel; ABI-Wechsel erfordern neuen Exportnamen und parallelen Hosttest.
