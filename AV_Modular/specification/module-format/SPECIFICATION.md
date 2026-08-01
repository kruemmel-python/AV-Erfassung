# AVM Module Format 1.0

Ein Modulverzeichnis MUST `module.json` und die dort referenzierten Prozess-, Regel-, Bericht- und Sprachdateien enthalten. Referenzen MUST relativ bleiben und dürfen das Modulverzeichnis nicht verlassen. Modul-, Vorgangs-, Unterbrechungs- und Schicht-IDs MUST stabil sein. Erweiterungsfelder SHOULD einen mindestens dreiteiligen, kleingeschriebenen Namensraum verwenden, beispielsweise `avm.document.page_count`.

Parser MUST unbekannte Felder ablehnen. Definitionen MUST eindeutige IDs, positive Zielzeiten, bekannte Unterbrechungen und unterstützte Feldtypen besitzen. Referenz: `enterprise/platform-core`; Vektoren: `modules/` und `conformance/fixtures`.
