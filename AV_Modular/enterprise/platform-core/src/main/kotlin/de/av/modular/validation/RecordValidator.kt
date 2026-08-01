package de.av.modular.validation

import de.av.modular.model.CustomerProfile
import de.av.modular.model.EffectiveModule
import de.av.modular.model.WorkItemRecord
import java.time.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

class RecordValidator {
    fun validate(record: WorkItemRecord, module: EffectiveModule, profile: CustomerProfile): List<ValidationIssue> = buildList {
        if (record.moduleId != module.source.manifest.moduleId) error("module_id", "Datensatz gehört zu einem anderen Modul")
        if (record.tenantId != profile.tenantId) error("tenant_id", "Datensatz gehört zu einem anderen Mandanten")
        if (record.schemaVersion != module.source.manifest.schemaVersion) error("schema_version", "Nicht unterstützte Schema-Version")
        val definition = module.workItems[record.processType]
        if (definition == null) {
            error("process_type", "Unbekannter Vorgang: ${record.processType}")
            return@buildList
        }
        if (definition.requiresEmployeeId && !record.employeeId.orEmpty().matches(Regex("^[0-9]+$"))) {
            error("employee_id", "Eine numerische Personalnummer ist erforderlich")
        }
        val start = runCatching { Instant.parse(record.startTimestamp) }.getOrElse {
            error("start_timestamp", "Ungültiger UTC-Zeitstempel"); null
        }
        val end = record.endTimestamp?.let { value ->
            runCatching { Instant.parse(value) }.getOrElse {
                error("end_timestamp", "Ungültiger UTC-Zeitstempel"); null
            }
        }
        if (start != null && end != null && end.isBefore(start)) error("end_timestamp", "Ende liegt vor dem Start")

        val knownFields = definition.fields.associateBy { it.id }
        record.customData.keys.filterNot(knownFields::containsKey).forEach { error("custom_data.$it", "Unbekanntes modulspezifisches Feld") }
        definition.fields.forEach { field ->
            val value = record.customData[field.id]
            if (field.required && value == null) error("custom_data.${field.id}", "Pflichtfeld fehlt")
            val primitive = value as? JsonPrimitive ?: return@forEach
            val validType = when (field.type) {
                "text", "date", "choice" -> primitive.isString
                "number" -> primitive.doubleOrNull != null
                "boolean" -> primitive.booleanOrNull != null
                else -> false
            }
            if (!validType) error("custom_data.${field.id}", "Datentyp ${field.type} erwartet")
            if (field.maxLength != null && primitive.content.length > field.maxLength) {
                error("custom_data.${field.id}", "Maximale Länge ${field.maxLength} überschritten")
            }
        }
    }

    private fun MutableList<ValidationIssue>.error(path: String, message: String) =
        add(ValidationIssue(Severity.ERROR, path, message))
}
