# AVM 1.0.0-RC1 Releasezeremonie

Der aktuelle Entwicklungsstand ist technisch eingefroren, aber nicht offiziell veröffentlicht. Der lokale Task `gradlew avmReleaseCandidate --no-daemon --warning-mode=all` erzeugt ausschließlich Development Evidence mit `official_release: false`.

## Freigabekette

1. Vorgesehene Änderungen prüfen; Geheimnisse, lokale Pfade, private Schlüssel und temporäre Reports ausschließen.
2. RC-Fix-Register vervollständigen, Commit erzeugen und einen clean Arbeitsbaum nachweisen.
3. Frischen Checkout auf einem unabhängigen Windows-Runner erstellen.
4. Vollständigen Clean-Build ohne übernommene Buildverzeichnisse und ohne Gradle-Daemon ausführen.
5. Alle Conformance-, JVM-, Android-, C++- und JSON-Prüfungen ohne Warnungen bestehen.
6. Unsigned Evidence und Digests in die getrennte Signierumgebung übergeben.
7. Organisationsschlüsselstatus, Zweck, Fingerabdruck und Widerrufsstatus prüfen.
8. Manifest, finalen Report und Release-Envelope signieren und anschließend erneut verifizieren.
9. Artefakte unveränderlich ablegen und erst dann einen annotierten Tag erzeugen.
10. Tag-Nachricht mit Commit, Profil, CI-Lauf, Key-ID sowie Manifest- und Report-Digest dokumentieren.

## Signierumgebung

Der private Schlüssel darf das HSM oder den geschützten Secret-Store nicht verlassen. Der Build erhält nur die für den Signaturvorgang erforderliche Schnittstelle. Für den offiziellen Modus sind mindestens diese Variablen erforderlich:

```text
AVM_CONFORMANCE_PRIVATE_KEY
AVM_CONFORMANCE_PUBLIC_KEY
AVM_CONFORMANCE_KEY_ID
AVM_OFFICIAL_RELEASE=true
GITHUB_RUN_ID
```

Die offizielle GitHub-Umgebung heißt `avm-release`, akzeptiert ausschließlich `main` und benötigt eine manuelle Freigabe durch den registrierten Reviewer. Die privaten Schlüssel liegen als verschlüsselte Environment-Secrets vor und werden ausschließlich im temporären Runnerverzeichnis materialisiert sowie im abschließenden `always()`-Schritt entfernt. Die dateibasierten Schlüsselvariablen sind für diesen Referenz-Runner vorgesehen. Eine spätere Produktionsintegration SHOULD sie durch eine HSM- oder KMS-Signieroperation ersetzen.

Der öffentliche Ed25519-Vertrauensanker und der Android-Zertifikatsfingerabdruck sind unter `specification/trust/release-keys.json` registriert. Der Releasecode vergleicht den tatsächlich verwendeten öffentlichen Schlüssel bytegenau mit dem eingecheckten Vertrauensanker. Zusätzlich bindet er Repository, Hauptbranch, Commit, Workflowdatei, Workflow-Revision, Run-ID und Releaseumgebung.

## Unveränderlichkeit

Bereits veröffentlichte RC1-Artefakte dürfen niemals unter demselben Namen ausgetauscht werden. Ändert ein Fix normatives Verhalten oder bereits veröffentlichte Digests, wird AVM 1.0.0-RC2 erzeugt.
