# Implementierungs- und Abnahmestand

## Stufe 1 – Externalisierung

- [x] Vorgangs-, Ziel-, Schicht- und Unterbrechungsarten
- [x] Branding, Rollen, Lizenzen, Berichtstitel und Metriken

## Stufe 2 – Modulmanifest

- [x] versioniertes Manifest und Referenzmodul Postbearbeitung
- [x] strikte Konfigurationsprüfung und Schema-Verträge
- [x] Entwicklungs- und signierter Produktionsmodus

## Stufe 3 – Generische Erfassung

- [x] Android-App mit Room-Entitäten und exportiertem DB-Schema
- [x] dynamische Compose-Oberfläche aus Moduldefinitionen
- [x] Schichten, Vorgänge, Unterbrechungen und Pflichtnotizen
- [x] Importadapter für vorhandene AV-Erfassungs-CSV
- [x] atomare Teamleiter-Vollkorrektur und Soft-Löschung mit Audit

## Stufe 4 – Generischer Reporter

- [x] Manifest- und Berichtsdefinitionen werden dynamisch geladen
- [x] Dimensionen, Kennzahlen und begrenzte sichere Formeln
- [x] revisionsfähiger CSV-v2-Vertrag mit Digest, deterministischer Deduplizierung und Konfliktquarantäne
- [x] QS-Narrativ sowie sichtbare Änderungs- und Löschspur

## Stufe 5 – Signierte Kundenprofile

- [x] separates Schlüssel-, Paket- und Prüfwerkzeug
- [x] Schlüsselrotation und Sperrliste
- [x] `.avpkg` mit Ed25519-Signatur und SHA-256 pro Asset
- [x] MDM-App-Restrictions und dokumentierter kontrollierter Rollout
- [x] signiertes vollständiges Evidenzbackup getrennt vom Berichtsexport

## Stufe 6 – Erweiterungs-SDK

- [x] C-ABI v1, Beispiel-DLL und definierte Speichergrenze
- [x] Host-Loader mit ABI-/Modul-ID-Prüfung
- [x] Lifecycle-, Fehler- und Kompatibilitätstests
- [x] Signaturgrenze auf Paketebene vor dem Laden nativer Inhalte

## Stufe 7 – AV Designer

- [x] Desktop-Projektoberfläche für Vorgänge/Formulare
- [x] Regel- und Berichtseditor mit gemeinsamer Modellvalidierung
- [x] Kundenprofil-, Rollen-, Lizenz- und Brandingeditor
- [x] Profilprüfung, Formatierung, Speichern und signierter Paketexport

## Enterprise-Querschnitt

- [x] Mandanten-/Standortgrenzen und RBAC
- [x] manipulationsdetektierbare Auditkette
- [x] sichere Pfad-, ZIP-, Schema- und Signaturprüfung
- [x] reproduzierbarer JVM-/Android-/C++-Build
- [x] Betriebs-, Sicherheits-, Deployment- und Datenvertragsdokumentation

Nicht Teil dieser lokalen Referenzimplementierung sind ein zentraler Identity Provider, HSM, MDM-Server, Lizenzserver oder Hochverfügbarkeits-Backend. Dafür sind geprüfte Integrationsgrenzen vorhanden; die jeweiligen Unternehmensdienste werden im Zielbetrieb angebunden.
