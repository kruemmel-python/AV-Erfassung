# AVM Reference Implementations

| Vertrag | Kotlin/JVM-Referenz | Unabhängiger Nachweis |
|---|---|---|
| Core Model 1.0 | `enterprise/platform-core` | Conformance-Module- und Runtime-Tests |
| Canonical Encoding 1.0 | `conformance/avm-canonical` | `conformance/avm-contracts-cpp` |
| Event Envelope 1.0 | `enterprise/platform-core` | Schema-/Fixture-Prüfung |
| Module/Profile 1.0 | `enterprise/platform-core` | drei fachlich verschiedene Referenzmodule |
| Package Format 1.0 | `enterprise/platform-core` | C++-Pfadvalidator und Angriffsfixture |
| Work Record 2.0 | `enterprise/reporting-core` | C++-Digest- und Revisionsentscheidung |
| Backup 1.0 | `conformance/avm-backup` | kryptografischer JVM-Angriffstest |
| Diagnostic 1.0 | `conformance/avm-diagnostics` | C++-Allowlist und personenbezogenes Negativfixture |
| Report Definition 1.0 | `enterprise/reporting-core` | QS- und Soft-Delete-Tests |
| Plugin ABI 1 | `enterprise/native-host` | Beispielplugin, Hosttest und C++-Layoutprüfung |
| Compatibility 1.0 | `conformance/avm-compatibility` | unabhängiger C++-Hauptversionsvergleich |

Referenzcode erläutert die Verträge, ersetzt aber niemals den normativen Text und die maschinenlesbaren Schemas. Bei Abweichungen gilt die veröffentlichte Spezifikation; eine Unklarheit MUST über AVM-EP korrigiert werden.
