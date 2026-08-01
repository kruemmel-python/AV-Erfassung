package de.av.modular.reporting

import de.av.modular.model.ReportDefinition

data class ReportGroup(
    val dimensions: Map<String, String>,
    val metrics: Map<String, Double>,
)

data class GeneratedReport(
    val reportId: String,
    val title: String,
    val groups: List<ReportGroup>,
    val warnings: List<String>,
)

class ReportEngine {
    fun generate(definition: ReportDefinition, input: List<WorkRecord>): GeneratedReport {
        val warnings = mutableListOf<String>()
        // Audit reports must retain soft-deleted rows; operational KPI reports must not count them.
        val records = if ("correction_type" in definition.dimensions) input else input.filterNot(WorkRecord::deletedForAudit)
        val grouped = records.groupBy { record -> definition.dimensions.associateWith { dimension(record, it) } }
        val groups = grouped.map { (dimensions, values) ->
            val metrics = linkedMapOf<String, Double>()
            definition.metrics.forEach { metric ->
                metrics[metric.id] = when (metric.operation) {
                    "count" -> values.size.toDouble()
                    "sum" -> values.sumOf { field(it, metric.field.orEmpty()) }
                    "average" -> values.map { field(it, metric.field.orEmpty()) }.averageOrZero()
                    "minimum" -> values.minOfOrNull { field(it, metric.field.orEmpty()) } ?: 0.0
                    "maximum" -> values.maxOfOrNull { field(it, metric.field.orEmpty()) } ?: 0.0
                    "formula" -> formula(metric.expression.orEmpty(), values, warnings)
                    else -> 0.0.also { warnings += "Nicht unterstützte Metrik ${metric.operation}" }
                }
            }
            ReportGroup(dimensions, metrics)
        }.sortedBy { it.dimensions.values.joinToString("|") }
        return GeneratedReport(definition.reportId, definition.title, groups, warnings)
    }

    private fun dimension(record: WorkRecord, id: String): String = when (id) {
        "tenant_id" -> record.tenantId
        "module_id" -> record.moduleId
        "employee_id" -> record.employeeId
        "process_type" -> record.processType
        "shift_id" -> record.shiftId
        "shift_date" -> record.shiftDate
        "status" -> record.status
        "correction_type" -> record.correctionType
        else -> ""
    }

    private fun field(record: WorkRecord, id: String): Double = when (id) {
        "net_duration_seconds" -> record.netDurationSeconds
        "target_duration_seconds" -> record.targetDurationSeconds
        else -> 0.0
    }

    private fun formula(expression: String, records: List<WorkRecord>, warnings: MutableList<String>): Double = when (expression.trim()) {
        "target_duration_seconds / net_duration_seconds" -> {
            val target = records.sumOf(WorkRecord::targetDurationSeconds)
            val actual = records.sumOf(WorkRecord::netDurationSeconds)
            if (actual == 0.0) 0.0 else target / actual
        }
        else -> 0.0.also { warnings += "Nicht unterstützte Formel: $expression" }
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
