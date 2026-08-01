package de.av.modular.capture.data

import de.av.modular.runtime.PlatformRuntime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LegacyImportRecord(
    val shiftId: String,
    val employeeId: String,
    val processType: String,
    val startTimestamp: String,
    val endTimestamp: String?,
    val status: String,
    val deletedForAudit: Boolean,
)

data class LegacyImportResult(val records: List<LegacyImportRecord>, val warnings: List<String>)

class LegacyAvCsvImporter(private val runtime: PlatformRuntime) {
    private val date = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun parse(csv: String): LegacyImportResult {
        val lines = csv.lineSequence().filter(String::isNotBlank).toList()
        require(lines.isNotEmpty()) { "CSV-Datei ist leer" }
        val header = split(lines.first()).mapIndexed { index, name -> name.removePrefix("\uFEFF") to index }.toMap()
        fun index(vararg candidates: String): Int = candidates.firstNotNullOfOrNull(header::get)
            ?: error("CSV-Spalte fehlt: ${candidates.joinToString()}")
        val shiftColumn = index("Schicht-ID")
        val employeeColumn = index("Personalnummer", "Personalnr.")
        val typeColumn = index("Kistenart", "Vorgangsart")
        val startDateColumn = index("Startdatum")
        val startTimeColumn = index("Startzeit")
        val endDateColumn = index("Enddatum")
        val endTimeColumn = index("Endzeit")
        val statusColumn = header["Status"]
        val logColumn = header["Änderungsprotokoll"]
        val labels = runtime.module.workItems.values.associateBy { it.displayName.lowercase() }
        val warnings = mutableListOf<String>()
        val records = lines.drop(1).mapIndexedNotNull { rowIndex, line ->
            val values = split(line)
            val label = values.getOrNull(typeColumn).orEmpty().trim()
            if (label.isBlank()) return@mapIndexedNotNull null
            val definition = labels[label.lowercase()]
            if (definition == null) {
                warnings += "Zeile ${rowIndex + 2}: unbekannte Vorgangsart $label"
                return@mapIndexedNotNull null
            }
            val employee = values.getOrNull(employeeColumn).orEmpty().trim()
            if (!employee.matches(Regex("^[0-9]+$"))) {
                warnings += "Zeile ${rowIndex + 2}: ungültige Personalnummer"
                return@mapIndexedNotNull null
            }
            val start = timestamp(values[startDateColumn], values[startTimeColumn])
            val end = values.getOrNull(endDateColumn).orEmpty().takeIf(String::isNotBlank)?.let {
                timestamp(it, values.getOrNull(endTimeColumn).orEmpty())
            }
            val log = logColumn?.let(values::getOrNull).orEmpty()
            LegacyImportRecord(
                shiftId = values.getOrNull(shiftColumn).orEmpty(),
                employeeId = employee,
                processType = definition.id,
                startTimestamp = start,
                endTimestamp = end,
                status = statusColumn?.let(values::getOrNull)?.lowercase()?.ifBlank { "completed" } ?: "completed",
                deletedForAudit = log.contains("gelöscht", ignoreCase = true),
            )
        }
        return LegacyImportResult(records, warnings)
    }

    private fun timestamp(dateValue: String, timeValue: String): String = LocalDateTime.of(
        LocalDate.parse(dateValue.trim(), date), LocalTime.parse(timeValue.trim(), time),
    ).atZone(ZoneId.of(runtime.profile.profile.timezone)).toInstant().toString()

    private fun split(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> { current.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == ';' && !quoted -> { result += current.toString(); current.clear() }
                else -> current.append(char)
            }
            index++
        }
        result += current.toString()
        return result
    }
}
