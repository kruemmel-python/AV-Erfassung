# AVM Core Model 1.0

Eine Installation MUST Mandant, Modul, Profil, Schicht, Vorgang, Unterbrechung, Revision, Löschmarkierung und Auditereignis als getrennte fachliche Konzepte behandeln. Identitäten MUST innerhalb ihres Namensraums stabil sein. Zeiten MUST als UTC-Instant gespeichert und über eine explizite IANA-Zeitzone dargestellt werden. Änderungen MUST neue Revisionen erzeugen; eine fachliche Löschung MUST als Löschmarkierung auditierbar bleiben.

Eine Implementierung MUST maximal eine aktive Unterbrechung je Vorgang erzwingen und MUST Überlappungen ablehnen. Personenkennungen sind Fachnutzdaten und MUST aus Supportdiagnosen ausgeschlossen werden.

Schema: `core-model.schema.json`. Positive und negative Vektoren: `examples/`. Referenz: `enterprise/platform-core`.
