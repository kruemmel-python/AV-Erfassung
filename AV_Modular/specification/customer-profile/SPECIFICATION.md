# AVM Customer Profile 1.0

Ein Kundenprofil MUST Mandant, Profil-ID, Umgebung, Zeitzone, Branding, freigeschaltete Module, Rollen, Lizenz und optionale Zielzeit-Overrides festlegen. Produktionsprofile MUST mit einem vertrauenswürdigen, nicht gesperrten Ed25519-Schlüssel signiert sein. Development-Profile MAY unsigniert sein und MUST sichtbar als Entwicklungskonfiguration behandelt werden.

Ein Runtime-Prozess MUST Lizenzablauf, Modullizenz und Berechtigungen vor fachlicher Verarbeitung prüfen. Referenz: `enterprise/platform-core`.
