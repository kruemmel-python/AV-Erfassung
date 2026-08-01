# AV Work Record CSV v2

`av-work-record-v2` ist der revisionsfähige Berichtsexportvertrag. Kodierung ist UTF-8, Trennzeichen Semikolon, Kopfzeile verpflichtend. Felder mit Semikolon oder Anführungszeichen verwenden doppelte Anführungszeichen.

## Identität und Provenienz

| Feld | Bedeutung |
| --- | --- |
| `record_id` | stabile, vom Erfassungssystem vergebene Vorgangs-ID |
| `revision_number` | positive, bei jeder autorisierten Änderung steigende Revision |
| `source_device_id` | über MDM zugewiesene Geräte-ID |
| `export_id` | eindeutige ID eines Exportlaufs |
| `payload_digest` | SHA-256 des kanonischen fachlichen Datensatzinhalts |

Der Digest bindet Mandant, Modul, Schema, Record-ID, Schicht, Personalnummer, Vorgangsart, Zeiten, Status, Zusatzdaten, Änderungs-/Löschkennzeichen sowie Ist- und Zielzeit. Revision und Export-ID sind bewusst nicht Bestandteil: Ein unveränderter Datensatz bleibt über mehrere Exporte als echtes Duplikat erkennbar.

## Deterministische Zusammenführung

```text
gleiche record_id + gleicher payload_digest
    → echtes Duplikat; wird ignoriert

gleiche record_id + höherer revision_number + anderer payload_digest
    → neuere Revision ersetzt die ältere

gleiche record_id + niedrigerer revision_number
    → ältere Revision wird ignoriert

gleiche record_id + gleicher revision_number + anderer payload_digest
    → Konflikt/Manipulationsverdacht; Record wird quarantänisiert

verschiedene record_id
    → getrennte Vorgänge, auch bei identischen übrigen Feldern
```

Der Reporter erzeugt bei einem Revisionskonflikt keinen QS-Bericht. Die Dateien müssen fachlich beziehungsweise revisionsseitig geklärt werden. Eine Dateireihenfolge verändert das Ergebnis nicht.

## Pflichtfelder

Neben den fünf Identitätsfeldern sind verpflichtend: `contract_version`, `tenant_id`, `module_id`, `schema_version`, `shift_id`, `employee_id`, `process_type`, `start_timestamp`, `status`, `custom_data`, `manually_modified`, `deleted_for_audit`, `net_duration_seconds` und `target_duration_seconds`.

Zeitstempel sind ISO-8601/UTC. `end_timestamp` darf nur für aktive Datensätze leer sein. `custom_data` enthält ein JSON-Objekt gemäß Moduldefinition.

## Legacy v1

`av-work-record-v1` besitzt keine belastbare Revisionsidentität und ist nur als Legacy-Eingang zulässig. Der Import erzeugt eine deterministische synthetische Record-ID und eine Warnung. V1 darf nicht für neue Exporte, revisionsfähige Zusammenführung oder vollständige Wiederherstellung verwendet werden.

## Sicherheitsgrenze

Der Payload-Digest erkennt unbeabsichtigte oder nachträgliche Inhaltsabweichungen, ist aber keine digitale Signatur. Eine beweiswerterhaltende Gesamtsicherung verwendet `av-evidence-backup-v1` mit Ed25519-Signatur und vollständiger Auditkette.

Die normative Referenzdatei liegt unter `specification/work-record-v2/examples/valid-multi-employee.csv`.
