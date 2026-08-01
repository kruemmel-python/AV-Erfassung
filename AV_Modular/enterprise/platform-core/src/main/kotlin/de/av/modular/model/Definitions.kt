package de.av.modular.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ModuleManifest(
    @SerialName("module_id") val moduleId: String,
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("module_version") val moduleVersion: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("minimum_core_version") val minimumCoreVersion: String,
    @SerialName("processes_file") val processesFile: String,
    @SerialName("rules_file") val rulesFile: String,
    @SerialName("reports_file") val reportsFile: String,
    @SerialName("strings_file") val stringsFile: String,
)

@Serializable
data class ProcessCatalog(
    @SerialName("interruption_types") val interruptionTypes: List<InterruptionDefinition>,
    @SerialName("shift_types") val shiftTypes: List<ShiftDefinition>,
    @SerialName("work_items") val workItems: List<WorkItemDefinition>,
)

@Serializable
data class InterruptionDefinition(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val productive: Boolean,
    @SerialName("note_required") val noteRequired: Boolean = false,
)

@Serializable
data class ShiftDefinition(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("start_local") val startLocal: String,
    @SerialName("end_local") val endLocal: String,
    @SerialName("day_offset_end") val dayOffsetEnd: Int = 0,
)

@Serializable
data class WorkItemDefinition(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("target_duration_seconds") val targetDurationSeconds: Long,
    @SerialName("requires_employee_id") val requiresEmployeeId: Boolean,
    @SerialName("allowed_interruptions") val allowedInterruptions: List<String>,
    val fields: List<FieldDefinition> = emptyList(),
)

@Serializable
data class FieldDefinition(
    val id: String,
    val type: String,
    @SerialName("display_name") val displayName: String,
    val required: Boolean = false,
    @SerialName("max_length") val maxLength: Int? = null,
)

@Serializable
data class RuleCatalog(val rules: List<RuleDefinition>)

@Serializable
data class RuleDefinition(
    val id: String,
    val event: String,
    val conditions: List<RuleCondition>,
    val actions: List<RuleAction>,
)

@Serializable
data class RuleCondition(
    val fact: String,
    val operator: String,
    val value: JsonElement? = null,
)

@Serializable
data class RuleAction(
    val type: String,
    val parameter: String? = null,
)

@Serializable
data class ReportCatalog(val reports: List<ReportDefinition>)

@Serializable
data class ReportDefinition(
    @SerialName("report_id") val reportId: String,
    val title: String,
    val dimensions: List<String>,
    val metrics: List<MetricDefinition>,
)

@Serializable
data class MetricDefinition(
    val id: String,
    val operation: String,
    val field: String? = null,
    val expression: String? = null,
)

@Serializable
data class SignedCustomerProfile(
    val profile: CustomerProfile,
    val signature: SignatureBlock? = null,
)

@Serializable
data class CustomerProfile(
    @SerialName("profile_id") val profileId: String,
    val environment: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("display_name") val displayName: String,
    val timezone: String,
    val branding: Branding,
    @SerialName("enabled_modules") val enabledModules: List<String>,
    @SerialName("target_overrides") val targetOverrides: List<TargetOverride> = emptyList(),
    val roles: List<RoleDefinition>,
    val license: LicenseDefinition,
)

@Serializable
data class Branding(
    @SerialName("primary_color") val primaryColor: String,
    @SerialName("secondary_color") val secondaryColor: String,
    @SerialName("organization_name") val organizationName: String,
    val logo: String? = null,
)

@Serializable
data class TargetOverride(
    @SerialName("module_id") val moduleId: String,
    @SerialName("work_item_id") val workItemId: String,
    @SerialName("target_duration_seconds") val targetDurationSeconds: Long,
)

@Serializable
data class RoleDefinition(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val permissions: List<String>,
)

@Serializable
data class LicenseDefinition(
    @SerialName("license_id") val licenseId: String,
    @SerialName("valid_until") val validUntil: String,
    val modules: List<String>,
    val features: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class SignatureBlock(
    val algorithm: String,
    @SerialName("key_id") val keyId: String,
    @SerialName("value_base64") val valueBase64: String,
)

data class LoadedModule(
    val manifest: ModuleManifest,
    val processes: ProcessCatalog,
    val rules: RuleCatalog,
    val reports: ReportCatalog,
    val strings: Map<String, String>,
)

data class EffectiveModule(
    val source: LoadedModule,
    val workItems: Map<String, WorkItemDefinition>,
)

@Serializable
data class WorkItemRecord(
    val id: String,
    @SerialName("module_id") val moduleId: String,
    @SerialName("process_type") val processType: String,
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("location_id") val locationId: String,
    @SerialName("employee_id") val employeeId: String? = null,
    @SerialName("shift_id") val shiftId: String,
    @SerialName("start_timestamp") val startTimestamp: String,
    @SerialName("end_timestamp") val endTimestamp: String? = null,
    val status: String,
    @SerialName("custom_data") val customData: JsonObject = JsonObject(emptyMap()),
)

data class PlatformEvent(
    val type: String,
    val attributes: Map<String, String> = emptyMap(),
    val metrics: Map<String, Double> = emptyMap(),
)

data class TriggeredAction(
    val ruleId: String,
    val type: String,
    val parameter: String?,
)
