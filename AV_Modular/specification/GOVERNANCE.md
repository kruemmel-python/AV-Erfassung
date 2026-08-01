# AVM Governance 1.0

Normative Änderungen MUST als AVM-EP unter `enhancement-proposals/` eingereicht werden. Ein Proposal MUST Motivation, Bedrohungsanalyse, Kompatibilitätswirkung, Migration, Testvektoren und Rücknahmeplan enthalten. Zwei Maintainer müssen eine Vertragsänderung prüfen; sicherheitsrelevante Änderungen benötigen zusätzlich eine unabhängige Security-Freigabe.

Patch-Releases dürfen Fehler eindeutig korrigieren, aber keine gültige Eingabe ungültig machen. Minor-Releases dürfen ausschließlich rückwärtskompatibel erweitern. Major-Releases dürfen inkompatibel sein und MUST einen deterministischen Migrationspfad bereitstellen. Veröffentlichte Golden-Vektoren werden unverändert archiviert.

Vertrauliche Schwachstellen werden nicht öffentlich diskutiert. Meldungen gehen an den in `SECURITY.md` genannten Kontakt. Schlüsselkompromittierung löst Sperrung, Ermittlung betroffener Paket-IDs, Schlüsselrotation, Neusignierung, MDM-Neuverteilung, Installationsnachweis und Incident-Bericht aus.

## RC1 Feature Freeze

AVM 1.0.0-RC1 ist unveränderlich veröffentlicht. Im RC1-Zweig sind ausschließlich Sicherheitskorrekturen, nachgewiesene Fehlerkorrekturen, semantikneutrale Spezifikationsklarstellungen, zusätzliche Tests sowie Reproduzierbarkeits- und Dokumentationsverbesserungen zulässig. Neue Produktfunktionen, Pflichtfelder, Regeloperatoren, Kanonisierungsregeln, Digestsemantik oder inkompatible Datenverträge sind verboten. Jede zulässige Korrektur MUST im RC-Fix-Register dokumentiert werden; normative Erweiterungen benötigen ein AVM-EP und mindestens den nächsten zulässigen Versionsstand.

## Supply-Chain-Governance

Alle externen GitHub Actions MUST auf vollständige 40-stellige Commit-SHAs gepinnt sein. Bewegliche Major-, Minor- oder Branch-Referenzen sind unzulässig. Der Task `verifyPinnedActions` prüft diese Regel maschinell und ist Bestandteil des Clean-Build-Gates.

## Final-Freigabe und Vier-Augen-Prinzip

AVM 1.0.0 Final MUST eine Interoperability Evidence nach `avm-interoperability-evidence-1.0` bestehen. Sie MUST mindestens zwei Organisationen, zwei getrennte Codebasen, übereinstimmende Entscheidungen und zwei unterschiedliche unabhängig bestätigte Freigabeidentitäten enthalten. Eigentümergeführte Selbstfreigabe genügt für Final nicht. Fehlende externe Teilnehmer oder Freigaben MUST als nicht erfülltes Final-Gate ausgewiesen werden und dürfen nicht durch Platzhalteridentitäten ersetzt werden.
