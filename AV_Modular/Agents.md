Jetzt programmieren wir **nicht einfach weitere Funktionen**, sondern wir trennen drei Dinge sauber voneinander:

```text
AV Modular Specification
    normativer, herstellerneutraler Branchenvertrag

AV Modular Reference Implementation
    deine lauffähige Referenzumsetzung

AV Modular Enterprise
    das kommerzielle Produkt mit Support, Designer und Integrationen
```

Der bestehende Stand ist dafür bereits geeignet: Der Kern ist fachneutral, Konfigurationen liegen außerhalb des Codes, Regeln sind deterministisch, externe Grenzen sind versioniert und Kundenprofile erzeugen keine Kern-Forks.

# 1. Zuerst definieren wir, was der Standard ist

Der Standard darf nicht „die AV-App“ sein. Er muss aus **implementierbaren Verträgen** bestehen.

Die erste AVM-Spezifikation sollte enthalten:

```text
AVM Core Model             1.0
AVM Event Envelope         1.0
AVM Module Format          1.0
AVM Customer Profile       1.0
AVM Package Format         1.0
AVM Work Record            2.0
AVM Backup Format          1.0
AVM Diagnostic Format      1.0
AVM Report Definition      1.0
AVM Native Plugin ABI      1
AVM Compatibility Contract 1.0
```

Diese Verträge müssen unabhängig von Kotlin, Android, Room oder deinem Reporter verständlich sein.

Ein anderer Hersteller muss theoretisch einen kompatiblen Reporter oder Scanner-Adapter schreiben können, ohne deinen Quellcode zu besitzen.

# 2. Die Repository-Struktur ändern

Im Projekt sollte eine klar getrennte Spezifikation entstehen:

```text
AV_Modular/
├── specification/
│   ├── core-model/
│   ├── event-envelope/
│   ├── module-format/
│   ├── package-format/
│   ├── work-record-v2/
│   ├── backup-v1/
│   ├── diagnostic-v1/
│   ├── reporting-v1/
│   ├── plugin-abi-v1/
│   ├── compatibility/
│   ├── error-codes/
│   └── test-vectors/
│
├── reference-implementation/
│   ├── platform-core/
│   ├── capture-android/
│   ├── reporting-core/
│   ├── designer-desktop/
│   ├── profile-tool/
│   └── native-host/
│
├── conformance/
│   ├── avm-conformance-cli/
│   ├── positive-fixtures/
│   ├── negative-fixtures/
│   ├── golden-results/
│   └── certification-profiles/
│
└── modules/
    ├── mail_processing/
    ├── document_scanning/
    └── internal_logistics/
```

Die Spezifikation ist dann nicht mehr nur Dokumentation neben dem Code. Sie wird zum primären Vertrag, gegen den der Code gebaut wird.

# 3. Jede Spezifikation braucht vier Bestandteile

Für jeden Vertrag benötigen wir immer:

```text
1. Normativer Text
2. Maschinenlesbares Schema
3. Positive und negative Testvektoren
4. Referenzimplementierung
```

Beispiel für `AVM Work Record 2.0`:

```text
specification/work-record-v2/
├── SPECIFICATION.md
├── work-record-v2.schema.json
├── canonicalization.md
├── error-codes.md
├── examples/
│   ├── valid-original.csv
│   ├── valid-revision.csv
│   ├── valid-duplicate.csv
│   └── invalid-conflict.csv
└── test-vectors/
    ├── expected-digests.json
    └── expected-conflicts.json
```

Nur ein JSON-Schema reicht nicht. Das Schema kann Feldtypen prüfen, aber nicht alle semantischen Regeln.

# 4. Normative Sprache verwenden

Die Spezifikationen sollten bewusst mit verbindlichen Begriffen arbeiten:

```text
MUST       zwingend
MUST NOT   verboten
SHOULD     empfohlen, Abweichung muss begründet werden
MAY        optional
```

Beispiel:

> Eine Implementierung MUST einen Import abbrechen, wenn dieselbe `record_id` und `revision_number` mit unterschiedlichen `payload_digest`-Werten vorkommen.

> Eine Implementierung MUST NOT in diesem Fall einen Bericht erzeugen.

Damit wird Verhalten nicht nur beschrieben, sondern eindeutig vorgeschrieben.

# 5. Kanonisierung wird ein eigener Kernvertrag

Bei Signaturen, Digests und Konflikterkennung darf kein Interpretationsspielraum existieren.

Wir benötigen eine zentrale Bibliothek beziehungsweise Spezifikation:

```text
AVM Canonical Data Encoding 1.0
```

Sie definiert:

* UTF-8 ohne BOM
* Normalisierung von Zeilenenden
* Sortierung von JSON-Objektschlüsseln
* Behandlung von `null`
* Zahlenformat
* Boolean-Darstellung
* Zeitstempel ausschließlich ISO-8601/UTC
* Feldreihenfolge bei kanonischen Datensätzen
* Unicode-Normalisierung
* Digest-Algorithmus
* Ausschlussfelder für den Digest

Beispiel:

```text
WorkRecord
    ↓ Typvalidierung
    ↓ Normalisierung
    ↓ kanonische Serialisierung
    ↓ SHA-256
payload_digest
```

Ohne eine solche Festlegung könnten Kotlin-, C++- und fremde Implementierungen unterschiedliche Digests für denselben Datensatz erzeugen.

# 6. Einheitlicher Fehlerkatalog

Ein Industriestandard braucht stabile Fehlercodes, nicht nur Fehlermeldungen.

Beispiel:

```text
AVM-PKG-1001  PACKAGE_SIGNATURE_INVALID
AVM-PKG-1002  PACKAGE_KEY_REVOKED
AVM-PKG-1003  PACKAGE_PATH_UNSAFE

AVM-CSV-2001  CONTRACT_VERSION_UNSUPPORTED
AVM-CSV-2002  REQUIRED_FIELD_MISSING
AVM-CSV-2003  SAME_REVISION_DIGEST_CONFLICT
AVM-CSV-2004  REVISION_CHAIN_BROKEN

AVM-BKP-3001  BACKUP_SIGNATURE_INVALID
AVM-BKP-3002  BACKUP_TENANT_MISMATCH
AVM-BKP-3003  DATABASE_SCHEMA_INCOMPATIBLE

AVM-ABI-4001  ABI_VERSION_UNSUPPORTED
AVM-ABI-4002  FUNCTION_TABLE_INCOMPLETE
AVM-ABI-4003  MODULE_ID_MISMATCH
```

Der Text darf lokalisiert werden. Der Code bleibt dauerhaft gleich.

# 7. Die Conformance Suite wird das Herzstück

Ein Format wird erst zum Standard, wenn überprüfbar ist, ob eine Implementierung kompatibel ist.

Wir bauen:

```powershell
avm-conformance test module <path>
avm-conformance test package <file.avpkg>
avm-conformance test work-record <file.csv>
avm-conformance test backup <file.avbackup>
avm-conformance test diagnostic <file.json>
avm-conformance test plugin <file.dll>
avm-conformance test runtime <endpoint-or-cli>
```

Beispielausgabe:

```text
AVM Work Record 2.0 Conformance

Schema validation                  PASS
Canonical digest                   PASS
Duplicate handling                 PASS
Revision ordering                  PASS
Conflict rejection                 PASS
Unknown field policy               PASS
Timestamp normalization            PASS

Result: AVM-WR2 CONFORMANT
```

Die Suite muss sowohl Erfolgs- als auch Angriffsfälle enthalten:

* manipuliertes Paket,
* falscher Digest,
* gleiche Revision mit anderem Inhalt,
* fehlende Revision,
* unsicherer ZIP-Pfad,
* gesperrter Schlüssel,
* inkompatible ABI,
* personenbezogene Daten in Supportdiagnose,
* falscher Mandant im Backup.

Die bestehenden Sicherheitsgrenzen eignen sich bereits als Grundlage: Pakete binden jede Datei kryptografisch, gesperrte Schlüssel werden abgelehnt und native Module werden erst nach Paket- und ABI-Prüfung geladen.

# 8. Eine zweite unabhängige Implementierung bauen

Das ist der wichtigste technische Nachweis.

Solange nur deine Kotlin-Implementierung die Verträge versteht, ist AVM ein Produktformat. Für einen Standard benötigen wir mindestens eine zweite unabhängige Umsetzung.

Ich würde in C++ implementieren:

```text
avm-contracts-cpp/
├── canonical_json
├── work_record_v2
├── package_validator
├── diagnostic_validator
├── compatibility_reader
└── conformance_runner
```

Diese C++-Implementierung darf keinen Kotlin-Code verwenden.

Beide Implementierungen müssen dieselben Golden Tests bestehen:

```text
Kotlin Runtime
    ┐
    ├── gleicher kanonischer Digest
    ├── gleiche Fehlercodes
    ├── gleiche Konfliktentscheidung
    └── gleiche Kompatibilitätsbewertung
C++ Runtime
```

Wenn Kotlin und C++ unabhängig zu denselben Ergebnissen kommen, ist der Vertrag wirklich spezifiziert.

# 9. Erweiterungen standardisieren, ohne den Kern aufzublähen

Der Kern darf nicht alle denkbaren Postprozesse kennen.

Stattdessen braucht AVM Namensräume:

```text
avm.core.*
avm.mail.*
avm.document.*
avm.quality.*
avm.transport.*

com.customer.example.*
de.vendor.module.*
```

Für kundenspezifische Felder:

```json
{
  "namespace": "de.customer.poststelle",
  "field": "special_route_code"
}
```

Dadurch können Unternehmen eigene Erweiterungen definieren, ohne mit späteren AVM-Kernfeldern zu kollidieren.

# 10. Capability Negotiation einführen

Eine Komponente sollte nicht nur ihre Versionsnummer nennen, sondern ihre Fähigkeiten.

Beispiel:

```json
{
  "component": "av-reporter",
  "version": "1.0.0",
  "supported_contracts": {
    "avm-package": [1],
    "avm-work-record": [1, 2],
    "avm-report-definition": [1],
    "avm-diagnostic": [1]
  },
  "capabilities": [
    "revision-aware-import",
    "conflict-rejection",
    "soft-delete-audit",
    "dynamic-metrics"
  ]
}
```

Damit können Komponenten beim Start prüfen:

* Kann der Reporter diesen Export lesen?
* Kann die App dieses Modul ausführen?
* Kann der Designer dieses Profil bearbeiten?
* Kann der Host dieses Plugin laden?

# 11. Migration wird Teil des Standards

Jede Major-Version braucht definierte Übergänge.

```text
av-work-record-v1
        ↓
    v1-to-v2 migrator
        ↓
av-work-record-v2
```

Wichtig:

* Migrationen sind deterministisch.
* Originaldaten werden nicht überschrieben.
* Das Migrationsergebnis erhält einen Nachweis.
* Verlustbehaftete Migrationen werden ausdrücklich gekennzeichnet.
* Nicht migrierbare Daten führen zu einem Fehler, nicht zu stiller Entfernung.

# 12. Zertifizierungsprofile definieren

Nicht jedes System muss alle AVM-Funktionen implementieren.

Deshalb definieren wir Profile:

```text
AVM Capture Compatible
    Module Format
    Customer Profile
    Work Record Export
    Package Verification

AVM Reporter Compatible
    Work Record Import
    Revision Handling
    Conflict Detection
    Report Definitions

AVM Designer Compatible
    Module/Profile Editing
    Schema Validation
    Package Creation

AVM Enterprise Compatible
    RBAC
    Audit
    Signed Packages
    Backup
    Diagnostics
    Key Revocation

AVM Native Extension Compatible
    Plugin ABI
    Package Verification
    Lifecycle and memory rules
```

So können auch kleinere Anbieter kompatibel werden, ohne die komplette Plattform nachzubauen.

# 13. Drei Referenzmodule statt nur Postbearbeitung

Die Postbearbeitung bleibt das erste Referenzmodul. Zusätzlich benötigen wir mindestens zwei deutlich andere Module:

## `document_scanning`

```text
Scanauftrag
Seitenzahl
Bildqualität
Nachscan
Klassifikation
Fehlerart
Freigabestatus
```

## `internal_container_transport`

```text
Behälter
Abholort
Zielort
Übergabe
Empfang
Verspätung
Verlustmeldung
```

Wenn beide Module ohne Änderung des Plattformkerns funktionieren, ist bewiesen, dass AVM mehr als eine abstrahierte Postkisten-App ist.

# 14. Standard und kommerzielles Produkt trennen

Wir sollten ausgewählte Verträge veröffentlichen können, ohne das Produkt zu verschenken.

Offen beziehungsweise öffentlich dokumentierbar:

* Datenmodelle
* Schemas
* Ereignisse
* Fehlercodes
* Modulformat
* CSV-Vertrag
* Paketformat
* Plugin-ABI
* Conformance Tests

Kommerziell bleiben:

* AV Capture
* AV Designer Pro
* AV Reporter Enterprise
* MDM-Integration
* zentrale Administration
* SAP-/DMS-Connectoren
* Support
* Zertifizierung
* kundenspezifische Module

Das ist strategisch wichtig:

> Der Standard schafft das Ökosystem.
> Das Produkt verdient das Geld.

# 15. Governance einführen

Ab Veröffentlichung von Version 1 darfst auch du selbst Verträge nicht mehr spontan verändern.

Wir benötigen:

```text
AVM Enhancement Proposal
AVM-EP-0001
```

Jede Änderung enthält:

* Problem
* Motivation
* Vertragsänderung
* Sicherheitsauswirkung
* Kompatibilitätsauswirkung
* Migrationspfad
* Testvektoren
* Referenzimplementierung

Status:

```text
Draft
Accepted
Implemented
Released
Deprecated
Rejected
```

Für v1 gilt:

```text
Patch:
Klarstellung oder Fehlerkorrektur ohne Verhaltensänderung

Minor:
Neue optionale Felder oder Fähigkeiten

Major:
Inkompatible Vertragsänderung mit Migration
```

# 16. Reihenfolge der nächsten Programmierung

## Phase 1 – Vertrags-Freeze

* alle Vertragsversionen festlegen,
* Work Record v2 implementieren,
* Backup v1 implementieren,
* Diagnostic v1 implementieren,
* Compatibility v1 implementieren,
* Fehlercodes zentralisieren,
* Kanonisierung festschreiben.

## Phase 2 – Conformance Framework

* `avm-conformance` CLI,
* positive und negative Fixtures,
* Golden Digests,
* Paket-, CSV-, Backup- und Diagnoseprüfungen,
* maschinenlesbarer Prüfbericht.

## Phase 3 – Zweite Implementierung

* C++-Validator,
* C++-Kanonisierung,
* C++-Digestprüfung,
* Cross-Language-Golden-Tests.

## Phase 4 – Referenzmodule

* Postbearbeitung,
* Dokumentenscanning,
* interner Behältertransport.

## Phase 5 – Integrations-SDK

* Java/Kotlin SDK,
* C++ SDK,
* CLI-Werkzeuge,
* Beispielconnector,
* Modulvorlagen,
* Zertifizierungsprofile.

## Phase 6 – Pilotstandard

* mehrere Unternehmen,
* unterschiedliche Prozesse,
* gemeinsame Datenverträge,
* dokumentierte Abweichungen,
* reale Upgrade- und Migrationsläufe.

# Das unmittelbare nächste Implementierungspaket

Ich würde jetzt konkret diese neuen Module anlegen:

```text
:avm-specification
:avm-canonical
:avm-error-codes
:avm-conformance
:avm-backup
:avm-diagnostics
:avm-compatibility
```

Und als erstes vollständiges Gate:

```text
./gradlew avmConformance
```

Dieses Gate muss prüfen:

```text
Module Format             PASS
Package Format            PASS
Work Record v2            PASS
Backup v1                 PASS
Diagnostic v1             PASS
Compatibility Contract    PASS
Kotlin Golden Tests       PASS
C++ Golden Tests          PASS
Android Lint              PASS
No warnings               PASS
```

## Der entscheidende Gedanke

Ein Industriestandard entsteht nicht dadurch, dass AV Modular immer größer wird.

Er entsteht, wenn:

1. die Verträge eindeutig sind,
2. mehrere Implementierungen dieselben Ergebnisse liefern,
3. Kompatibilität automatisch prüfbar ist,
4. Erweiterungen ohne Kernänderung möglich sind,
5. reale Unternehmen dieselben Formate verwenden.

AV Modular hat bereits den notwendigen Produktkern: Capture, Reporter, Designer, signierte Pakete, Rollen, Audit, MDM und native Erweiterungen sind umgesetzt.
