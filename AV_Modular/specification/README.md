# AVM Specification

AVM Specification 1.0.0-RC1 ist die eingefrorene Standard-Candidate-Fassung der herstellerneutralen, normativen Vertragsbasis von AV Modular. Die Schlüsselwörter **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT** und **MAY** sind gemäß RFC 2119 auszulegen. Maschinenlesbare Schemas sind normativ; Beispiele und Referenzcode erläutern deren Anwendung.

Jeder veröffentlichte Vertrag besitzt eine stabile Kennung, eine unabhängige Versionsnummer, positive und negative Testvektoren sowie eine benannte Referenzimplementierung. Inkompatible Änderungen benötigen eine neue Hauptversion und ein AVM Enhancement Proposal (AVM-EP).

| Vertrag | Version | Referenzimplementierung |
|---|---:|---|
| Core Model | 1.0 | `enterprise/platform-core` |
| Canonical Encoding | 1.0 | `conformance/avm-canonical` |
| Event Envelope | 1.0 | `specification/avm-specification` |
| Module Format | 1.0 | `enterprise/platform-core` |
| Customer Profile | 1.0 | `enterprise/platform-core` |
| Package Format | 1.0 | `enterprise/platform-core` |
| Work Record | 2.0 | `conformance/avm-canonical`, `enterprise/reporting-core` |
| Evidence Backup | 1.0 | `conformance/avm-backup` |
| Support Diagnostic | 1.0 | `conformance/avm-diagnostics` |
| Report Definition | 1.0 | `enterprise/reporting-core` |
| Native Plugin ABI | 1 | `enterprise/native-host` |
| Compatibility | 1.0 | `conformance/avm-compatibility` |
| Artifact Manifest | 1.0 | `conformance/avm-conformance-cli` |
| Conformance Report | 1.0 | `conformance/avm-conformance-cli` |
| Release Envelope | 1.0 | `conformance/avm-conformance-cli` |
| Release Key Registry | 1.0 | `specification/trust` |

Sicherheitsmeldungen, Governance und Releaseverfahren stehen in `GOVERNANCE.md`. Die Zertifizierungsprüfung wird mit `gradlew avmConformance` ausgeführt. Die Releaseverträge unter `release-evidence-v1/` trennen Artefaktintegrität, Signerauthentizität und organisationsverwaltetes Vertrauen ausdrücklich voneinander.
