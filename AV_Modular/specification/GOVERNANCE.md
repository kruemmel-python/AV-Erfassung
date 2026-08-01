# AVM Governance 1.0

Normative Änderungen MUST als AVM-EP unter `enhancement-proposals/` eingereicht werden. Ein Proposal MUST Motivation, Bedrohungsanalyse, Kompatibilitätswirkung, Migration, Testvektoren und Rücknahmeplan enthalten. Zwei Maintainer müssen eine Vertragsänderung prüfen; sicherheitsrelevante Änderungen benötigen zusätzlich eine unabhängige Security-Freigabe.

Patch-Releases dürfen Fehler eindeutig korrigieren, aber keine gültige Eingabe ungültig machen. Minor-Releases dürfen ausschließlich rückwärtskompatibel erweitern. Major-Releases dürfen inkompatibel sein und MUST einen deterministischen Migrationspfad bereitstellen. Veröffentlichte Golden-Vektoren werden unverändert archiviert.

Vertrauliche Schwachstellen werden nicht öffentlich diskutiert. Meldungen gehen an den in `SECURITY.md` genannten Kontakt. Schlüsselkompromittierung löst Sperrung, Ermittlung betroffener Paket-IDs, Schlüsselrotation, Neusignierung, MDM-Neuverteilung, Installationsnachweis und Incident-Bericht aus.
