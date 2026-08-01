# AVM Event Envelope 1.0

Jedes zwischen Komponenten ausgetauschte Ereignis MUST `contract`, `event_id`, `event_type`, `occurred_at_utc`, `source_component` und `payload` enthalten. `event_id` MUST dauerhaft eindeutig sein. Konsumenten MUST unbekannte Hauptversionen ablehnen und MAY unbekannte optionale Payloadfelder einer kompatiblen Minor-Version ignorieren. Wiederholte identische `event_id` MUST idempotent behandelt werden.

Schema und Vektoren sind Teil dieses Verzeichnisses. Referenz: `specification/avm-specification`.
