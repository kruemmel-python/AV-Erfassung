# AVM Compatibility Contract 1.0

Jede auslieferbare Komponente MUST ihre Produktversion, Vertrags-Hauptversionen, Storage-Schema und Plugin-ABI offenlegen. Vor Installation oder Datenaustausch MUST Capability Negotiation stattfinden. Unterschiedliche Hauptversionen eines Pflichtvertrags sind inkompatibel. Fehlende optionale Capabilities MAY zu einer dokumentierten Funktionsreduktion führen; fehlende Pflichtcapabilities MUST zur Ablehnung führen.

Die normative Matrix steht in `compatibility-matrix.json`. Referenz: `conformance/avm-compatibility`.
