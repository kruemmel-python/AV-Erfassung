# AVM Work Record 2.0

CSV MUST UTF-8, semikolongetrennt und mit der normativen Kopfzeile aus `work-record-v2.schema.json` exportiert werden. `record_id` bezeichnet die dauerhafte fachliche Identität; `revision_number` MUST bei jeder autorisierten Änderung strikt steigen. `source_device_id` bezeichnet die Quelle, `export_id` den Exportlauf und `payload_digest` den SHA-256 der kanonischen fachlichen Nutzlast.

Gleiche `record_id` und gleicher `payload_digest` sind ein echtes Duplikat und MUST idempotent ignoriert werden. Bei gleicher `record_id` gewinnt ausschließlich eine höhere `revision_number`. Gleiche ID und Revision mit unterschiedlichem Digest MUST als Konflikt oder Manipulationsverdacht quarantänisiert werden. Verschiedene `record_id` MUST getrennt bleiben. Dateireihenfolge darf das Ergebnis nicht verändern.

Legacy-v1-Datensätze MAY importiert werden, müssen aber deterministisch auf eine synthetische Record-ID und Revision 1 migriert und sichtbar als Legacy gemeldet werden. Referenz: `enterprise/reporting-core` und `conformance/avm-canonical`.
