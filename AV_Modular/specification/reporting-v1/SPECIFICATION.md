# AVM Report Definition 1.0

Berichte MUST eine stabile `report_id`, Dimensionen, Metriken und eine deklarierte Vertragsversion besitzen. Berechnungen MUST deterministisch sein und gelöschte sowie manuell geänderte Vorgänge sichtbar unterscheiden. Teamaggregate MUST aus denselben akzeptierten Revisionen wie Mitarbeiteraggregate berechnet werden. Konfliktbehaftete Datensätze dürfen nicht still in Kennzahlen einfließen.

Berichtsausgaben SHOULD Datenstand, Importquellen, Duplikate, ersetzte Revisionen, Konflikte und verwendete Zielwerte offenlegen. Referenz: `enterprise/reporting-core`.
