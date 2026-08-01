package de.av.modular.reporting

data class QualitySummary(
    val validWorkItems: Int,
    val deletedWorkItems: Int,
    val manualChanges: Int,
    val averageMinutes: Double,
    val targetRatio: Double,
    val narrative: String,
    val findings: List<String>,
)

class QualityAnalyzer {
    fun analyze(records: List<WorkRecord>): QualitySummary {
        val valid = records.filterNot(WorkRecord::deletedForAudit)
        val deleted = records.count(WorkRecord::deletedForAudit)
        val changed = records.count(WorkRecord::manuallyModified)
        val average = valid.map(WorkRecord::netDurationSeconds).averageOrZero() / 60.0
        val target = valid.sumOf(WorkRecord::targetDurationSeconds)
        val actual = valid.sumOf(WorkRecord::netDurationSeconds)
        val ratio = if (actual == 0.0) 0.0 else target / actual
        val findings = buildList {
            if (deleted > 0) add("$deleted gelöschte Vorgänge sind in der Prüfspur vorhanden.")
            if (changed > 0) add("$changed Vorgänge wurden manuell geändert.")
            if (valid.any { it.endTimestamp == null || it.status == "active" }) add("Mindestens ein Vorgang war beim Export nicht abgeschlossen.")
            if (ratio < 0.8 && valid.isNotEmpty()) add("Die Bearbeitungszeit liegt deutlich über der hinterlegten Zielzeit.")
        }
        val narrative = when {
            valid.isEmpty() -> "Für die gewählte Auswertung liegen keine gültigen Vorgänge vor."
            ratio >= 1.0 -> "Die Bearbeitungsleistung liegt im betrachteten Zeitraum insgesamt im Zielbereich."
            ratio >= 0.8 -> "Die Bearbeitungsleistung liegt leicht unter dem konfigurierten Zielbereich."
            else -> "Die Bearbeitungsleistung weicht deutlich vom konfigurierten Zielbereich ab und sollte fachlich geprüft werden."
        }
        return QualitySummary(valid.size, deleted, changed, average, ratio, narrative, findings)
    }

    fun toHtml(title: String, summary: QualitySummary): String = """
        <!doctype html><html lang="de"><head><meta charset="utf-8"><title>${escape(title)}</title>
        <style>body{font-family:Arial,sans-serif;margin:32px;color:#222}h1,h2{color:#b00000}.kpi{display:inline-block;padding:12px;margin:6px;background:#fff0ad;border-left:4px solid #d40511}li{margin:6px 0}</style></head>
        <body><h1>${escape(title)}</h1><p>${escape(summary.narrative)}</p>
        <div class="kpi"><b>${summary.validWorkItems}</b><br>gültige Vorgänge</div>
        <div class="kpi"><b>${"%.1f".format(summary.averageMinutes)}</b><br>Ø Minuten</div>
        <div class="kpi"><b>${"%.1f".format(summary.targetRatio * 100)} %</b><br>Zielerreichung</div>
        <h2>Prüfhinweise</h2><ul>${summary.findings.joinToString("") { "<li>${escape(it)}</li>" }}</ul></body></html>
    """.trimIndent()

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
