# AVM Conformance

AVM Conformance 1.0.0-RC2 zertifiziert Implementierungen gegen AVM Specification. Die Suite enthält positive Golden-Vektoren, aktive Angriffsfälle, fünf abgestufte Zertifizierungsprofile, stabile Fehlercodes und zwei voneinander unabhängige Vertragsimplementierungen in Kotlin und C++.

```powershell
.\gradlew.bat avmConformance --warning-mode=all
```

Ein Lauf ist nur erfolgreich, wenn alle normativen Artefakte vorhanden sind, alle drei Referenzmodule validieren, Manipulationen abgelehnt werden, Kotlin und C++ denselben Digest bilden, Android Lint keine Meldung erzeugt und sowohl der C++-Conformance-Runner als auch der Enterprise Native Host ohne Compilerwarnung bauen und testen.

Der Bericht `build/reports/avm-conformance.json` ist ein Evidence-Artefakt. Jede ID ist innerhalb des Berichts eindeutig. Jeder Subject-Pfad ist repository-relativ und durch `subject_sha256` gebunden. Negativtests dokumentieren `expected_outcome`, `observed_outcome`, `expected_error_code` und `observed_error_code`. Der Reportkopf bindet Tool, Implementierung, Commit, Build-ID, Zertifizierungsprofil, Betriebssystem, Architektur, Java- und C++-Toolchain sowie alle Vertragsversionen.

Die kanonische Reportnutzlast erhält SHA-256 und eine Ed25519-Signatur. Lokal erzeugte Ephemeral-Signaturen beweisen Integrität innerhalb des Evidence-Pakets, aber keine organisatorische Identität. Freigegebene Zertifikate MUST mit einem extern verwalteten Schlüssel signiert werden. Ein Zertifikat darf nur für exakt den geprüften Commit, die angegebene Toolchain und eines der Profile unter `certification-profiles/` ausgestellt werden.
