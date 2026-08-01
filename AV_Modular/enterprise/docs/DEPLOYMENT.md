# Deployment und MDM

## Android

Für Produktion wird `capture-android` als Release-AAB/APK mit organisationsverwaltetem Keystore gebaut. Debugsignaturen sind nicht freigabefähig. Die App verlangt keine Netzwerkberechtigung und deaktiviert Android-Backup.

Unterstützte Managed Configurations:

| Schlüssel | Beispiel | Wirkung |
| --- | --- | --- |
| `av_module_id` | `mail_processing` | eingebettetes, freigegebenes Modul |
| `av_profile_id` | `demo_dhl` | Kunden-/Mandantenprofil |
| `av_location_id` | `berlin_01` | Standortbindung neuer Schichten |
| `av_device_id` | `device_berlin_01` | stabile Export- und Backup-Provenienz |
| `av_allow_legacy_import` | `false` | kontrolliert Altimport |

IDs werden gegen ein restriktives Muster geprüft; ungültige Werte fallen auf sichere Defaults zurück. In Produktion werden ausschließlich signierte, vor dem Build beziehungsweise kontrollierten Paketimport verifizierte Profile bereitgestellt.

## Windows-Werkzeuge

`installDist` erzeugt portable Laufzeitverzeichnisse für Reporter, Profile Tool und Designer. Für Verteilung werden diese Verzeichnisse versioniert, signiert, mit SBOM/Prüfsumme versehen und über Softwareverteilung installiert. Schreibrechte auf Programmverzeichnisse erhalten Standardbenutzer nicht.

## Native Plugins

Plugins liegen nur in einem administrativ beschreibbaren Verzeichnis. Vor Extraktion prüft der Paketdienst Ed25519 und SHA-256; danach prüft der Host ABI und Modul-ID. Ein Pluginfehler führt zum Abbruch des Ladevorgangs, nicht zu einem Fallback auf ungeprüften Code.

Windows-Builds mit MinGW binden `libstdc++`, `libgcc` und `libwinpthread` statisch ein. Dadurch darf die Auslieferung keine gleichnamigen GCC-Runtime-DLLs benötigen und kann nicht versehentlich eine inkompatible Version aus `PATH` oder einer Fremdinstallation wie GTK laden. Die PE-Importtabelle ist vor Freigabe zu prüfen; zulässig sind für das Referenzmodul ausschließlich Windows-Systembibliotheken.

## Rollback

App und Paket werden unabhängig versioniert. Rollback bedeutet: vorherige freigegebene Appversion plus weiterhin gültiges, nicht gesperrtes Paket erneut zuweisen. Datenbankschemata dürfen nur abwärtskompatibel zurückgerollt werden; destruktive Downgrades sind für produktive Daten nicht zulässig.
