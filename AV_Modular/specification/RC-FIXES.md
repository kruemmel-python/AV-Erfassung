# AVM 1.0.0-RC1 → RC2 Fix-Register

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

## RC1-FIX-003 – Reproduzierbare CI-Quellprovenienz

- Problem: Git war aus einem Windows-JavaExec-Prozess innerhalb der MSYS2-Runnerumgebung nicht zuverlässig auflösbar; CI-Reports konnten deshalb `commit: unavailable` enthalten.
- Auswirkung: Die Workflowprüfung war korrekt, die Evidence-Engine konnte denselben Checkout aber nicht eindeutig binden.
- Verträge: Conformance Report 1.0, Release Envelope 1.0.
- Kompatibilität: Ausschließlich Releaseprovenienz; Produkt-, Fach- und Nutzdaten bleiben unverändert.
- Teständerung: Der Workflow verifiziert Commit und Arbeitsbaum vor dem Build, übergibt eine an `GITHUB_SHA` gebundene Quellassertion und die Evidence-Engine lehnt abweichende oder außerhalb GitHub Actions gesetzte Assertionen ab.
- Commit: Bestandteil des kontrollierten RC1-Fix-Commits.

## RC1-FIX-004 – Vertrauenskontextabhängiger Evidence-Test

- Problem: Der Signaturtest erwartete unabhängig vom aktiven Vertrauenskontext ausschließlich die Metadaten einer ephemeren Entwicklungssignatur.
- Auswirkung: Eine korrekt organisationssignierte offizielle Evidence wurde im Testlauf abgelehnt, obwohl Signatur, Vertrauensanker und Release-Guards gültig waren.
- Verträge: Conformance Report 1.0 und Release-Trust-Modell; das normative Verhalten bleibt unverändert.
- Kompatibilität: Ausschließlich Testkorrektur; Produkt-, Fach-, Signatur- und Nutzdaten bleiben unverändert.
- Teständerung: Der Test prüft Entwicklungsevidence strikt auf `ephemeral-test-key` und offizielle Evidence strikt auf `organization-managed-release-key` sowie `trusted-official-release`.
- Commit: Bestandteil des kontrollierten RC1-Fix-Commits.

## RC1-FIX-005 – Unveränderliche GitHub-Action-Referenzen

- Problem: Die CI- und Releaseworkflows verwendeten bewegliche Major-Tags für externe GitHub Actions.
- Auswirkung: Ein später veränderter Tag hätte ohne Repositoryänderung anderen Drittcode in die Lieferkette aufnehmen können.
- Verträge: Release- und Supply-Chain-Governance; normative AVM-Datenverträge bleiben unverändert.
- Kompatibilität: Ausschließlich CI-Härtung.
- Teständerung: `verifyPinnedActions` lehnt jede externe Action ohne vollständigen 40-stelligen Commit-SHA ab und ist Teil des Clean-Build-Gates.
- Commit: Bestandteil des kontrollierten RC1-Fix-Commits.

## RC1-FIX-006 – Getrennter Interoperabilitätsnachweis

- Problem: Der technische Kotlin-/C++-Nachweis war vorhanden, aber organisatorische Fremdimplementierung und Vier-Augen-Freigabe waren nicht als eigenständiges Final-Gate maschinenlesbar getrennt.
- Auswirkung: Technische Cross-Language-Konformität hätte irrtümlich als externe Branchenadoption interpretiert werden können.
- Verträge: Nichtnormative Pilot-Evidence 1.0 und Final-Governance; die 37 RC1-Conformance-Prüfungen bleiben unverändert.
- Kompatibilität: Additiver Prüf- und Governance-Rahmen ohne Änderung gültiger AVM-Eingaben oder Ausgaben.
- Teständerung: `avmInteroperability` prüft die technische Referenzmatrix; `final-readiness` verlangt zwei Organisationen und zwei unabhängige Freigabeidentitäten.
- Commit: Bestandteil des kontrollierten RC1-Fix-Commits.

## RC1-FIX-007 – Explizite Pre-Release-Klassifizierung

- Problem: Der veröffentlichte RC1 wurde nachträglich korrekt als GitHub Pre-Release markiert, der Workflow kodierte diese Klassifizierung jedoch nicht selbst.
- Auswirkung: Eine Wiederverwendung des Releaseverfahrens könnte einen Release Candidate fälschlich als stabiles Final-Release darstellen.
- Verträge: Release-Metadaten; normative AVM-Verträge bleiben unverändert.
- Kompatibilität: Ausschließlich Veröffentlichungsmetadaten.
- Teständerung: Der Releasebefehl setzt `--prerelease` explizit.
- Commit: Bestandteil des kontrollierten RC1-Fix-Commits.

## RC1-FIX-008 – Unveränderlicher RC2-Produktrelease

- Problem: Die nach RC1 abgeschlossenen Härtungen und das Interoperabilitäts-Gate waren noch nicht als eigener signierter Candidate veröffentlicht; die drei produktiven Anwendungen nutzten unterschiedliche Releaseverfahren.
- Auswirkung: Aktuelle AVM-Artefakte und die Produktanwendungen konnten nicht aus demselben sauberen Commit und derselben kontrollierten Freigabezeremonie bezogen werden.
- Verträge: Release-Metadaten und Artefaktmanifest; normative AVM-Verträge bleiben unverändert.
- Kompatibilität: RC2 ist verhaltenskompatibel zu RC1 und erhält eigene unveränderliche Artefakte und Tags.
- Teständerung: Der gemeinsame Produktrelease baut Android, Qt/C++, AVM Conformance und Interoperabilität warnungsfrei, verifiziert drei APK-Signaturen und bindet alle Downloads über SHA-256 sowie GitHub Attestations.
- Commit: Bestandteil des kontrollierten RC2-Release-Commits.
