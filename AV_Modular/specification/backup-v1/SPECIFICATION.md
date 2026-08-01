# AVM Evidence Backup 1.0

Ein Evidence Backup ist kein Berichtsexport. Es MUST Schichten, Vorgänge, Unterbrechungen, Revisionen, Löschmarkierungen, vollständige Auditkette, Paket- und Schema-Versionen sowie alle für die beweiswerterhaltende Wiederherstellung erforderlichen Metadaten enthalten. Das Archiv MUST ein Manifest mit Datei-Digests, Audit-Head-Hash und Ed25519-Signatur besitzen.

Wiederherstellung MUST vor jeder Mutation Signatur, Schlüsselstatus, Dateivollständigkeit, Digests, Schemaunterstützung und Auditverkettung prüfen. Eine Prüfung mit unbekanntem oder gesperrtem Schlüssel MUST fehlschlagen. Referenz: `conformance/avm-backup`.
