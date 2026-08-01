# AVM Release Evidence 1.0

Dieser normative Vertrag definiert die Vertrauenskette eines AVM-Releases. Die Schlüsselwörter MUST, MUST NOT, SHOULD und MAY gelten gemäß RFC 2119.

## Vertrauensmodell

Eine gültige Ed25519-Signatur beweist die Integrität des signierten kanonischen JSON-Inhalts. `signer_type` beschreibt die Art des Signers. `trust_status` beschreibt, ob der öffentliche Schlüssel einen anerkannten organisatorischen Vertrauensanker besitzt. `official_release` MUST nur dann `true` sein, wenn der verwendete Schlüssel organisationsverwaltet ist, der Quellstand clean ist und der Build auf einem externen, nachvollziehbaren Runner ausgeführt wurde.

Lokale Entwicklungsevidence MUST folgende Werte verwenden:

```json
{"signer_type":"ephemeral-test-key","trust_status":"untrusted-development-evidence","official_release":false}
```

## Erzeugungsreihenfolge

1. Der Build MUST alle Produktartefakte und die SBOM erzeugen.
2. Das signierte Artefaktmanifest MUST Pfad, Größe und SHA-256 jedes verteilten Artefakts enthalten.
3. Der finale Conformance-Report MUST den SHA-256 des vollständig signierten Manifests und der SBOM enthalten.
4. Das Release-Envelope MUST die SHA-256-Werte des vollständig signierten Manifests, des vollständig signierten Reports und der SBOM binden.
5. Manifest, Report und Envelope MUST unabhängig kryptografisch verifizierbar sein.

Der Report darf nicht Bestandteil des von ihm referenzierten Manifests sein. Dadurch entsteht keine zyklische Hashreferenz. Das Release-Envelope ist die oberste Vertrauenseinheit.

## Offizielle Freigabe

Ein offizielles Release MUST einen eindeutigen Commit, `dirty: false`, einen externen Workflow und eine externe Run-ID binden. Private Organisationsschlüssel MUST außerhalb des Repositorys und des allgemeinen Build-Workspace verbleiben. Ein Tag darf erst nach erfolgreicher externer Verifikation und Signaturfreigabe erzeugt werden.
