# AVM Interoperability & Pilot Program

Dieses Programm prüft Interoperabilität getrennt von der eingefrorenen AVM-1.0.0-RC1-Conformance. Es erweitert weder normative Pflichtfelder noch Kanonisierung, Digestsemantik oder Fehlercodes des veröffentlichten RC1.

## Technischer Referenznachweis

```powershell
.\gradlew.bat avmInteroperability --no-daemon --warning-mode=all
```

Der Gate vergleicht die getrennten Kotlin- und C++-Codebasen über dieselben positiven und negativen Vektoren. Er MUST fehlschlagen, sobald Digest, Annahmeentscheidung oder normativer Fehlercode voneinander abweichen. Dieser Nachweis belegt Sprach- und Codebase-Unabhängigkeit, aber ausdrücklich noch keine organisatorische Fremdimplementierung.

## Externer Industrienachweis

Eine Final-Evidence wird mit folgendem Befehl geprüft:

```powershell
.\conformance\avm-interoperability\build\install\avm-interoperability\bin\avm-interoperability.bat final-readiness <evidence.json> <evidence-bundle-root>
```

AVM 1.0.0 Final MUST zusätzlich nachweisen:

- mindestens zwei Implementierungen aus unterschiedlichen Organisationen,
- getrennte Codebasen und mindestens zwei Programmiersprachen,
- vollständige Ergebnismatrix für alle deklarierten Vektoren,
- bytegenaue Bindung aller Quellstände und Testvektoren an SHA-256,
- identische Digests beziehungsweise identische normative Ablehnungsgründe,
- mindestens zwei unterschiedliche, unabhängig bestätigte Freigabeidentitäten,
- mindestens zwei tatsächlich beteiligte Pilotorganisationen.

Die Vorlage unter `templates/external-industry-pilot.template.json` enthält absichtlich keine erfundenen Teilnehmer oder Freigaben. Bis eine echte externe Evidence vorliegt, darf der technische Referenznachweis nicht als Industriestandard-Adoption bezeichnet werden.
