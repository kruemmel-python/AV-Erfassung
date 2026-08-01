# AVM 1.0.0-RC1 Fix-Register

Seit dem Feature Freeze sind keine neuen Produktfunktionen zulässig. Korrekturen müssen Konformität, Sicherheit, Reproduzierbarkeit, Testvektoren, Release-Metadaten, Lizenzen oder normative Eindeutigkeit betreffen. Eine Änderung des normativen Verhaltens erfordert einen neuen Candidate-Level.

## RC1-FIX-001 – Eindeutige Conformance-Evidence

- Problem: Doppelte Test-IDs, nicht beweisende Negativtests und absolute lokale Subjects.
- Auswirkung: Berichte waren maschinell nicht eindeutig und nicht vollständig reproduzierbar.
- Verträge: Conformance Report 1.0.
- Kompatibilität: Nur additive Evidence-Metadaten; keine fachlichen Eingaben ändern sich.
- Teständerung: Eindeutigkeit, relative Subjects, Subject-Digests sowie erwartete und beobachtete Fehlercodes werden geprüft.
- Commit: Bestandteil des kontrollierten RC1-Release-Commits.

## RC1-FIX-002 – Präzises Vertrauensmodell und Release-Envelope

- Problem: Die Bezeichnung `ephemeral-self-signed` unterschied Integrität, Authentizität und Vertrauen nicht präzise; Manifest und Report waren nicht durch eine oberste Vertrauenseinheit gebunden.
- Auswirkung: Lokale Evidence konnte terminologisch überbewertet werden; eine zyklusfreie Releasebindung fehlte.
- Verträge: Artifact Manifest 1.0, Conformance Report 1.0, Release Envelope 1.0.
- Kompatibilität: Additive Release-Metadaten; Produkt- und Nutzdaten bleiben unverändert.
- Teständerung: Signaturprüfung, Trust-Metadaten, Manifest-/Report-/SBOM-Digests und Envelope-Bindung werden im Release-Task verifiziert.
- Commit: Bestandteil des kontrollierten RC1-Release-Commits.
