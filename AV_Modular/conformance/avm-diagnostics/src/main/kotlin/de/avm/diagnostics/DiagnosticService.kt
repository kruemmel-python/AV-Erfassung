package de.avm.diagnostics

import de.avm.errors.AvmError
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Support metadata by construction: no employee IDs, record payloads or correction reasons. */
@Serializable
data class DiagnosticSnapshot(
    val contract: String = CONTRACT,
    @SerialName("generated_at_utc") val generatedAtUtc: String,
    @SerialName("core_version") val coreVersion: String,
    @SerialName("module_id") val moduleId: String,
    @SerialName("module_version") val moduleVersion: String,
    @SerialName("module_schema_version") val moduleSchemaVersion: Int,
    @SerialName("profile_id") val profileId: String,
    val environment: String,
    val timezone: String,
    @SerialName("storage_schema_version") val storageSchemaVersion: Int,
    val counters: Map<String, Long>,
) {
    fun supportText(): String = buildString {
        appendLine("AV Modular – technische Diagnose")
        appendLine("Erzeugt: $generatedAtUtc")
        appendLine("Core: $coreVersion")
        appendLine("Modul: $moduleId $moduleVersion (Schema $moduleSchemaVersion)")
        appendLine("Profil: $profileId ($environment)")
        appendLine("Zeitzone: $timezone")
        appendLine("Storage-Schema: $storageSchemaVersion")
        counters.toSortedMap().forEach { (name, count) -> appendLine("$name: $count") }
    }

    fun contractJson(): String = CONTRACT_JSON.encodeToString(this)

    companion object {
        const val CONTRACT = "av-support-diagnostic-v1"
        private val CONTRACT_JSON = Json { prettyPrint = true; encodeDefaults = true }
    }
}

class DiagnosticService(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val allowedCounters = setOf("shifts", "work_items", "activities", "corrections", "audit_events", "import_warnings")

    fun capture(metadata: DiagnosticMetadata, counters: Map<String, Long>): DiagnosticSnapshot {
        require(metadata.storageSchemaVersion > 0) { "Storage-Schema muss positiv sein" }
        require(counters.keys.all { it in allowedCounters }) { "Diagnose enthält nicht freigegebene Zähler" }
        require(counters.values.all { it >= 0 }) { "Diagnosezähler dürfen nicht negativ sein" }
        return DiagnosticSnapshot(
            generatedAtUtc = Instant.now(clock).toString(),
            coreVersion = metadata.coreVersion,
            moduleId = metadata.moduleId,
            moduleVersion = metadata.moduleVersion,
            moduleSchemaVersion = metadata.moduleSchemaVersion,
            profileId = metadata.profileId,
            environment = metadata.environment,
            timezone = metadata.timezone,
            storageSchemaVersion = metadata.storageSchemaVersion,
            counters = counters.toMap(),
        )
    }

    fun parseStrict(value: String): DiagnosticSnapshot = runCatching {
        STRICT_JSON.decodeFromString<DiagnosticSnapshot>(value)
    }.getOrElse { cause ->
        throw DiagnosticContractException(AvmError.DIAGNOSTIC_FIELD_FORBIDDEN, cause)
    }

    private companion object {
        val STRICT_JSON = Json { ignoreUnknownKeys = false }
    }
}

class DiagnosticContractException(
    val error: AvmError,
    cause: Throwable,
) : IllegalArgumentException("Diagnose verletzt die Feld- oder Typ-Allowlist", cause)

data class DiagnosticMetadata(
    val coreVersion: String,
    val moduleId: String,
    val moduleVersion: String,
    val moduleSchemaVersion: Int,
    val profileId: String,
    val environment: String,
    val timezone: String,
    val storageSchemaVersion: Int,
)
