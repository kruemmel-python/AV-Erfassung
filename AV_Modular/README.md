# AV Modular

AV Modular ist eine vertragsgetriebene Plattform für revisionssichere Schicht- und Prozessdatenerfassung. Der eingefrorene Stand trägt die Bezeichnung **AVM 1.0 Standard Candidate / 1.0.0-RC1**. Das Repository trennt den offenen technischen Vertrag, dessen unabhängige Nachweisführung und das produktive Enterprise-System physisch und im Build.

## Produktaufteilung

| Produktlinie | Verzeichnis | Verantwortung |
|---|---|---|
| **AVM Specification** | `specification/` | Normative, herstellerneutrale Verträge, Schemas, Fehlercodes, Migrationen und Golden-Vektoren |
| **AVM Conformance** | `conformance/` | Zertifizierungs-CLI, Angriffsvektoren, Kotlin-Prüfmodule und unabhängige C++-Implementierung |
| **AVM Enterprise** | `enterprise/` | Android Capture, Plattformkern, Designer, Reporter, Profilwerkzeug, nativer Host und Betriebsmittel |

Die Referenzmodule `modules/mail_processing`, `modules/document_scanning` und `modules/internal_logistics` liegen bewusst außerhalb des Enterprise-Codes. Sie beweisen, dass der Kern keine fest codierte Postkistenlogik enthält.

## Architektur und Verträge

```text
AVM Specification
        │ normative Schemas, Canonical Encoding, stabile Fehlercodes
        ▼
AVM Conformance ─── unabhängige C++-Golden-Implementierung
        │ zertifizierte Verträge und Angriffstests
        ▼
AVM Enterprise ─── Android Capture · Designer · Reporter · Native Host
```

Veröffentlichte Verträge sind Core Model 1.0, Canonical Encoding 1.0, Event Envelope 1.0, Module Format 1.0, Customer Profile 1.0, Package Format 1.0, Work Record 2.0, Evidence Backup 1.0, Support Diagnostic 1.0, Report Definition 1.0, Native Plugin ABI 1 und Compatibility 1.0.

Work Record 2.0 verwendet `record_id`, `revision_number`, `source_device_id`, `export_id` und `payload_digest`. Ein identischer Digest ist ein echtes Duplikat, eine höhere Revision ersetzt deterministisch die ältere und eine gleiche Revision mit abweichendem Digest wird quarantänisiert. Die Eingabereihenfolge ist keine Konfliktregel.

## Enterprise-Komponenten

| Komponente | Funktion |
|---|---|
| `enterprise/platform-core` | Modul-/Profilparser, Validierung, Regeln, RBAC, Audit, Lizenzen und signierte Pakete |
| `enterprise/capture-android` | generische Offline-Erfassung, Room, dynamische Compose-Oberfläche und MDM-Konfiguration |
| `enterprise/reporting-core` | revisionsfähiger CSV-Import, Konfliktquarantäne, Kennzahlen und QS-Analyse |
| `enterprise/reporter-cli` | Multi-Mitarbeiter-Import und HTML-QS-Berichte |
| `enterprise/designer-desktop` | grafischer Modul- und Profildesigner |
| `enterprise/profile-tool` | Ed25519-Schlüssel, `.avpkg`, Verifikation, Rotation und Sperrlisten |
| `enterprise/native-host` | abgesicherter C-ABI-v1-Host und Beispielplugin |

Enterprise erzwingt Mandantengrenzen, rollenbasierte Rechte, vollständige Teamleiterkorrektur, auditierbare Soft-Löschung, verpflichtende Änderungsgründe, SHA-256-verkettete Auditereignisse, signierte Konfigurationspakete und ein signiertes Evidence-Backup. Supportdiagnosen schließen Personenkennungen und Fachnutzdaten konstruktiv aus.

## Bauen, prüfen und zertifizieren

Voraussetzungen sind JDK 17, Android SDK 36, CMake und MSYS2/MinGW-w64. Warnungsfreiheit ist ein unverhandelbares Gate: Kotlin `allWarningsAsErrors`, Java `-Xlint:all -Werror`, Android Lint `warningsAsErrors`, C++ `/WX` oder `-Werror`. Warnungen werden nicht unterdrückt.

```powershell
.\gradlew.bat avmConformance --warning-mode=all
```

Der Gate validiert Spezifikationsartefakte, alle drei Referenzmodule, Work-Record-Golden-Daten, Diagnose- und Kompatibilitätsverträge, JVM-Tests, Android Lint sowie die unabhängige C++-Implementierung. Der maschinenlesbare Bericht unter `build/reports/avm-conformance.json` besitzt eindeutige Test-IDs, repository-relative Subjects mit SHA-256, erwartete und beobachtete Negativtest-Fehlercodes, Build- und Toolchainmetadaten sowie eine Ed25519-Signatur.

Der vollständige RC1-Build erzeugt zusätzlich reproduzierbar sortierte Distributionen und ein SHA-256-Artefaktmanifest:

```powershell
.\gradlew.bat clean --no-daemon --warning-mode=all
.\gradlew.bat avmReleaseCandidate --no-daemon --warning-mode=all
```

Vollständige Distributionsartefakte:

```powershell
.\gradlew.bat test :capture-android:assembleDebug `
  :profile-tool:installDist :reporter-cli:installDist :designer-desktop:installDist
```

Ergebnisse:

- APK: `enterprise/capture-android/build/outputs/apk/debug/capture-android-debug.apk`
- Reporter: `enterprise/reporter-cli/build/install/reporter-cli/bin/reporter-cli.bat`
- Designer: `enterprise/designer-desktop/build/install/designer-desktop/bin/designer-desktop.bat`
- Profile Tool: `enterprise/profile-tool/build/install/profile-tool/bin/profile-tool.bat`

## Conformance CLI

```powershell
.\gradlew.bat :avm-conformance:installDist
.\conformance\avm-conformance-cli\build\install\avm-conformance\bin\avm-conformance.bat test all .
```

Unterstützte Prüfziele sind `module`, `package`, `work-record`, `backup`, `diagnostic`, `plugin`, `runtime`, `compatibility` und `all`. Ergebnisse verwenden stabile AVM-Fehlercodes und werden zugleich menschen- und maschinenlesbar ausgegeben.

## Referenzmodule

- `mail_processing`: Tagespost, Sachbearbeitung, Rückläufer, Routing, Ablage und HR-Akte; Pause, Registrierung, Image und Diverse.
- `document_scanning`: Scanauftrag, Seitenzahl, Bildqualität, Nachscan, Klassifikation und Freigabestatus.
- `internal_logistics`: Behältertransport, Abhol- und Zielort, Übergabe, Verspätung und Verlustmeldung.

Die Zusatzfelder verwenden kollisionsarme Namensräume wie `avm.document.*` und `avm.transport.*`. Kundenerweiterungen SHOULD eine Reverse-DNS-Form wie `de.customer.poststelle.*` nutzen.

## Sicherheit und Betrieb

CSV ist ein Berichtsexport und kein vollständiges Backup. Eine beweiswerterhaltende Wiederherstellung benötigt das signierte Evidence-Backup mit Schichten, Vorgängen, Unterbrechungen, Revisionen, Löschmarkierungen, Auditkette sowie Paket- und Schema-Versionen. Private Schlüssel gehören in HSM oder Secret Store und niemals in Repository, APK oder allgemeine MDM-Dateifreigaben.

Für ein organisationsvertrauenswürdiges Conformance-Zertifikat werden `AVM_CONFORMANCE_PRIVATE_KEY`, `AVM_CONFORMANCE_PUBLIC_KEY`, `AVM_CONFORMANCE_KEY_ID` und `AVM_OFFICIAL_RELEASE=true` ausschließlich in der geschützten Signierumgebung gesetzt. Ohne externen Vertrauensanker erzeugt der lokale Entwicklungsbuild eine kryptografisch integre, aber ausdrücklich nicht vertrauenswürdige Evidence mit `signer_type: ephemeral-test-key`, `trust_status: untrusted-development-evidence` und `official_release: false`.

`gradlew avmReleaseCandidate` erzeugt ein signiertes Artefaktmanifest mit neun Digests, eine CycloneDX-SBOM, den daran gebundenen finalen Conformance-Report und ein signiertes Release-Envelope als oberste Vertrauenseinheit. Der offizielle Modus verweigert dirty Arbeitsstände und Builds ohne externen CI-Lauf. Der CI-Clean-Build ist auf Repository-Ebene unter `.github/workflows/avm-rc1-conformance.yml` definiert; Commit, Organisationssignatur, Tag und Veröffentlichung bleiben ein gesonderter Freigabevorgang.

Die offizielle Freigabe läuft ausschließlich über `.github/workflows/avm-rc1-release.yml` aus `main`. Sie verwendet die geschützte GitHub-Umgebung `avm-release`, prüft den registrierten Ed25519-Vertrauensanker, erzeugt eine signierte Android-Release-APK, erstellt eine GitHub-Provenienzattestierung und veröffentlicht anschließend den annotierten Tag `avm-v1.0.0-rc1` samt unveränderlichem GitHub Release.

- [Normative Spezifikation](specification/README.md)
- [Governance](specification/GOVERNANCE.md)
- [Security Policy](specification/SECURITY.md)
- [Kompatibilitätsmatrix](COMPATIBILITY.md)
- [Enterprise-Architektur](enterprise/docs/ARCHITECTURE.md)
- [Betriebshandbuch](enterprise/docs/OPERATIONS.md)
- [Deployment und MDM](enterprise/docs/DEPLOYMENT.md)
- [Incident Response](enterprise/docs/INCIDENT-RESPONSE.md)
- [CSV-Vertrag](enterprise/docs/CSV-CONTRACT.md)
- [RC1-Releasezeremonie](enterprise/docs/RELEASE-CEREMONY.md)
- [RC-Fix-Register](specification/RC-FIXES.md)
