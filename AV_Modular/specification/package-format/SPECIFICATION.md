# AVM Package Format 1.0

Ein `.avpkg` MUST ein deterministisches ZIP mit `META-INF/av-package.json` und den im Manifest aufgelisteten Dateien sein. Das Manifest MUST Paket-ID, Version, Erstellungszeit, Schlüssel-ID, je Datei Pfad, Größe und SHA-256 sowie eine Ed25519-Signatur enthalten. Nicht gelistete Dateien, doppelte ZIP-Pfade, absolute Pfade und `..`-Segmente MUST abgelehnt werden.

Die Signatur MUST über die kanonische Manifest-Nutzlast ohne Signaturfeld berechnet werden. Gesperrte oder unbekannte Schlüssel MUST zur Ablehnung führen. Referenz: `enterprise/platform-core`.
