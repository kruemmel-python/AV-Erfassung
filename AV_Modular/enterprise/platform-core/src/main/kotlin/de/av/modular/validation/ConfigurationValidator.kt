package de.av.modular.validation

import de.av.modular.model.LoadedModule
import de.av.modular.model.SignedCustomerProfile
import java.time.LocalDate
import java.time.LocalTime

enum class Severity { ERROR, WARNING }

data class ValidationIssue(val severity: Severity, val path: String, val message: String)

class ConfigurationValidator(
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val idPattern = Regex("^[a-z][a-z0-9_]{2,63}$")
    private val namespacedFieldPattern = Regex("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*){2,7}$")
    private val colorPattern = Regex("^#[0-9A-Fa-f]{6}$")
    private val allowedFieldTypes = setOf("text", "number", "boolean", "date", "choice")
    private val allowedOperators = setOf("equals", "exists", "greater_than", "greater_than_target_factor")
    private val allowedActions = setOf("require_reason", "create_qs_flag", "create_warning")
    private val allowedMetricOperations = setOf("count", "sum", "average", "minimum", "maximum", "formula")

    fun validate(module: LoadedModule, signedProfile: SignedCustomerProfile): List<ValidationIssue> = buildList {
        val manifest = module.manifest
        val profile = signedProfile.profile
        if (!idPattern.matches(manifest.moduleId)) error("module.module_id", "Ungültige Modul-ID")
        if (manifest.schemaVersion != 1) error("module.schema_version", "Nur Schema-Version 1 wird unterstützt")
        if (manifest.moduleVersion.isBlank()) error("module.module_version", "Modulversion fehlt")

        duplicateIds(module.processes.workItems.map { it.id }).forEach { error("processes.work_items", "Doppelte ID: $it") }
        duplicateIds(module.processes.interruptionTypes.map { it.id }).forEach { error("processes.interruption_types", "Doppelte ID: $it") }
        duplicateIds(module.processes.shiftTypes.map { it.id }).forEach { error("processes.shift_types", "Doppelte ID: $it") }
        val interruptionIds = module.processes.interruptionTypes.map { it.id }.toSet()
        module.processes.workItems.forEachIndexed { index, item ->
            val path = "processes.work_items[$index]"
            if (!idPattern.matches(item.id)) error("$path.id", "Ungültige Vorgangs-ID")
            if (item.targetDurationSeconds <= 0) error("$path.target_duration_seconds", "Zielzeit muss positiv sein")
            item.allowedInterruptions.filterNot(interruptionIds::contains).forEach {
                error("$path.allowed_interruptions", "Unbekannte Unterbrechung: $it")
            }
            duplicateIds(item.fields.map { it.id }).forEach { error("$path.fields", "Doppelte Feld-ID: $it") }
            item.fields.forEach { field ->
                if (!idPattern.matches(field.id) && !namespacedFieldPattern.matches(field.id)) {
                    error("$path.fields.${field.id}.id", "Feld-ID muss einfach oder namespaced sein")
                }
                if (field.type !in allowedFieldTypes) error("$path.fields.${field.id}", "Nicht unterstützter Feldtyp: ${field.type}")
                if (field.maxLength != null && field.maxLength <= 0) error("$path.fields.${field.id}.max_length", "max_length muss positiv sein")
            }
        }
        module.processes.shiftTypes.forEach { shift ->
            runCatching { LocalTime.parse(shift.startLocal) }.onFailure { error("shift.${shift.id}.start_local", "Ungültige Uhrzeit") }
            runCatching { LocalTime.parse(shift.endLocal) }.onFailure { error("shift.${shift.id}.end_local", "Ungültige Uhrzeit") }
            if (shift.dayOffsetEnd !in 0..1) error("shift.${shift.id}.day_offset_end", "Nur 0 oder 1 ist zulässig")
        }

        duplicateIds(module.rules.rules.map { it.id }).forEach { error("rules", "Doppelte Regel-ID: $it") }
        module.rules.rules.forEach { rule ->
            if (rule.conditions.isEmpty()) warning("rules.${rule.id}", "Regel besitzt keine Bedingung")
            rule.conditions.filter { it.operator !in allowedOperators }.forEach {
                error("rules.${rule.id}.conditions", "Nicht unterstützter Operator: ${it.operator}")
            }
            rule.actions.filter { it.type !in allowedActions }.forEach {
                error("rules.${rule.id}.actions", "Nicht unterstützte Aktion: ${it.type}")
            }
            if (rule.actions.isEmpty()) error("rules.${rule.id}.actions", "Mindestens eine Aktion ist erforderlich")
        }

        duplicateIds(module.reports.reports.map { it.reportId }).forEach { error("reports", "Doppelte Bericht-ID: $it") }
        module.reports.reports.flatMap { it.metrics }.forEach { metric ->
            if (metric.operation !in allowedMetricOperations) error("reports.metrics.${metric.id}", "Nicht unterstützte Operation: ${metric.operation}")
            if (metric.operation == "formula" && metric.expression.isNullOrBlank()) error("reports.metrics.${metric.id}", "Formel fehlt")
            if (metric.operation != "formula" && metric.operation != "count" && metric.field.isNullOrBlank()) error("reports.metrics.${metric.id}", "Feld fehlt")
        }

        if (!idPattern.matches(profile.profileId)) error("profile.profile_id", "Ungültige Profil-ID")
        if (profile.environment !in setOf("development", "production")) error("profile.environment", "Nur development oder production ist zulässig")
        if (manifest.moduleId !in profile.enabledModules) error("profile.enabled_modules", "Modul ${manifest.moduleId} ist nicht aktiviert")
        if (manifest.moduleId !in profile.license.modules) error("profile.license.modules", "Modul ${manifest.moduleId} ist nicht lizenziert")
        runCatching { LocalDate.parse(profile.license.validUntil) }
            .onSuccess { if (it < today()) error("profile.license.valid_until", "Lizenz ist abgelaufen") }
            .onFailure { error("profile.license.valid_until", "Ungültiges Datum") }
        if (!colorPattern.matches(profile.branding.primaryColor)) error("profile.branding.primary_color", "Farbe muss #RRGGBB entsprechen")
        if (!colorPattern.matches(profile.branding.secondaryColor)) error("profile.branding.secondary_color", "Farbe muss #RRGGBB entsprechen")
        duplicateIds(profile.roles.map { it.id }).forEach { error("profile.roles", "Doppelte Rollen-ID: $it") }

        val workItemIds = module.processes.workItems.map { it.id }.toSet()
        profile.targetOverrides.filter { it.moduleId == manifest.moduleId }.forEach { override ->
            if (override.workItemId !in workItemIds) error("profile.target_overrides", "Unbekannter Vorgang: ${override.workItemId}")
            if (override.targetDurationSeconds <= 0) error("profile.target_overrides", "Zielzeit muss positiv sein")
        }

        if (profile.environment == "production" && signedProfile.signature == null) {
            error("signature", "Produktionsprofile müssen signiert sein")
        }
    }

    private fun MutableList<ValidationIssue>.error(path: String, message: String) =
        add(ValidationIssue(Severity.ERROR, path, message))

    private fun MutableList<ValidationIssue>.warning(path: String, message: String) =
        add(ValidationIssue(Severity.WARNING, path, message))

    private fun duplicateIds(ids: List<String>): Set<String> = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
