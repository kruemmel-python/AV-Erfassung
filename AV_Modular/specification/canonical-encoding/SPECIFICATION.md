# AVM Canonical Encoding 1.0

Text MUST als UTF-8 ohne BOM, Unicode NFC und mit LF-Zeilenenden kodiert werden. Zeitstempel MUST gültige RFC-3339-UTC-Instants sein und in der normalisierten `Instant.toString()`-Form enden. JSON-Objektschlüssel MUST lexikografisch sortiert, Texte NFC-normalisiert und Zahlen ohne bedeutungslose Nachkommastellen ausgegeben werden. NaN und Infinity sind unzulässig.

Der Work-Record-Payload wird in der in Work Record 2.0 festgelegten Feldreihenfolge mit U+001F getrennt und als SHA-256 in kleingeschriebenem Hex kodiert. Signaturen MUST die kanonischen Bytes signieren, nicht eine plattformspezifische Darstellung.

Referenz: `conformance/avm-canonical`. Golden-Vektor: `golden-work-record.json`.
