# Architektur von AV Modular

## Leitprinzipien

1. Der Plattformkern ist fachneutral.
2. Konfiguration ist der bevorzugte Erweiterungsweg.
3. Regeln sind begrenzt, deterministisch und validierbar.
4. Binärmodule werden nur eingesetzt, wenn Konfiguration und Regeln nicht ausreichen.
5. Kundenprofile enthalten Unterschiede, aber keinen kundenspezifischen Fork des Kerns.
6. Jede externe Grenze besitzt eine Version und eine explizite Validierung.

## Komponenten

### Platform Core

Der Kotlin/JVM-Kern definiert Manifest-, Profil-, Datensatz-, Ereignis-, Regel- und Berichtsverträge. Er kann später als gemeinsame Bibliothek in Android-Komponenten und serverseitigen Werkzeugen verwendet werden.

Der Kern enthält keine Postbegriffe. Das wird durch die Paketgrenze unterstützt: Fachwerte existieren ausschließlich als externe JSON-Daten.

### Module Package

Ein Fachmodul besteht in Version 1 aus:

```text
module.json
processes.json
rules.json
reports.json
strings_de.json
```

`module.json` ist der Einstiegspunkt. Alle referenzierten Pfade werden normalisiert und müssen innerhalb des Modulordners bleiben.

### Customer Profile

Ein Profil aktiviert Module und legt Mandant, Zeitzone, Branding, Rollen, Lizenzen und gezielte Overrides fest. Die Moduldefinition bleibt unverändert. Produktionsprofile müssen signiert sein.

### Rule Engine

Die Engine ist keine allgemeine Skriptsprache. Version 1 unterstützt:

| Operator | Bedeutung |
| --- | --- |
| `exists` | Attribut oder Messwert ist vorhanden |
| `equals` | Wert entspricht dem konfigurierten Wert |
| `greater_than` | Messwert überschreitet einen festen Wert |
| `greater_than_target_factor` | Messwert überschreitet Zielzeit × Faktor |

Unterstützte Aktionen sind `require_reason`, `create_qs_flag` und `create_warning`.

### Event Bus und Audit

Der synchrone In-Process-Bus entkoppelt Regeln und Adapter. Dauerhafte Fachänderungen werden zusätzlich in einer mandantenbezogenen SHA-256-Auditkette gespeichert. Der Bus verspricht bewusst keine verteilte Zustellung; eine spätere Serverintegration verwendet Outbox/Idempotenz an der Adaptergrenze.

### Native Module ABI

Windows-Plugins verwenden eine C-ABI. `free_result` stellt sicher, dass Speicher durch dieselbe Laufzeitbibliothek freigegeben wird, die ihn reserviert hat. Vor dem Aufruf muss der Host `abi_version` prüfen.

## Generisches Datenmodell

`WorkItemRecord` enthält nur fachneutrale Kernfelder:

```text
id, module_id, process_type, schema_version
tenant_id, location_id, employee_id, shift_id
start_timestamp, end_timestamp, status, custom_data
```

`custom_data` wird nicht ungeprüft gespeichert. Zulässige Felder und Typen stammen aus der jeweiligen `WorkItemDefinition`.

## Signaturkanonisierung

Die Referenzimplementierung signiert die kompakte JSON-Serialisierung des typisierten `CustomerProfile` mit `encodeDefaults=true` und `explicitNulls=false`. Ein produktives Signierwerkzeug muss exakt dieselbe Kanonisierung verwenden und die öffentliche Schlüssel-ID kontrolliert verteilen.

## Ausgeführte Anwendungen

- AV Capture rendert Modulprozesse dynamisch in Compose und persistiert offline mit Room.
- AV Reporter importiert versionierte Mehrmitarbeiter-CSV und erzeugt moduldefinierte QS-Auswertungen.
- AV Designer bearbeitet und validiert Modul/Profil als gemeinsame typisierte Projektstruktur.
- AV Profile Tool verwaltet Ed25519-Pakete, öffentliche Vertrauensanker und Widerruf.
- Der native Host lädt C-ABI-v1-Module nach vorgelagerter Paketprüfung.

Eine allgemeine Skriptsprache ist absichtlich nicht enthalten. Formeln und Regelaktionen bleiben als Whitelist begrenzt, damit Konfigurationen deterministisch validierbar sind. Zentrale Cloud-Synchronisierung und ein Lizenzserver sind optionale Unternehmensadapter, keine Voraussetzung für den Offlinebetrieb.
