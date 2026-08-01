package de.avm.compatibility

import de.avm.errors.AvmError
import de.avm.errors.AvmFailure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CompatibilityManifest(
    val contract: String = "avm-compatibility-1.0",
    @SerialName("platform_core") val platformCore: String,
    @SerialName("package_format") val packageFormat: Int,
    @SerialName("work_record") val workRecord: Int,
    @SerialName("backup_format") val backupFormat: Int,
    @SerialName("diagnostic_format") val diagnosticFormat: Int,
    @SerialName("plugin_abi") val pluginAbi: Int,
    @SerialName("room_schema") val roomSchema: Int,
    val designer: String,
    val capture: String,
    val reporter: String,
)

data class CompatibilityResult(val compatible: Boolean, val failures: List<AvmFailure>)

class CompatibilityService(private val json: Json = Json { ignoreUnknownKeys = false }) {
    fun parse(value: String): CompatibilityManifest = json.decodeFromString(value)

    fun evaluate(required: CompatibilityManifest, offered: CompatibilityManifest): CompatibilityResult {
        val failures = buildList {
            if (major(required.platformCore) != major(offered.platformCore)) add(failure("platform_core"))
            if (required.packageFormat != offered.packageFormat) add(failure("package_format"))
            if (required.workRecord != offered.workRecord) add(failure("work_record"))
            if (required.backupFormat != offered.backupFormat) add(failure("backup_format"))
            if (required.diagnosticFormat != offered.diagnosticFormat) add(failure("diagnostic_format"))
            if (required.pluginAbi != offered.pluginAbi) add(failure("plugin_abi"))
            if (required.roomSchema != offered.roomSchema) add(failure("room_schema"))
            if (major(required.designer) != major(offered.designer)) add(failure("designer"))
            if (major(required.capture) != major(offered.capture)) add(failure("capture"))
            if (major(required.reporter) != major(offered.reporter)) add(failure("reporter"))
        }
        return CompatibilityResult(failures.isEmpty(), failures)
    }

    private fun major(version: String): Int = version.substringBefore('.').toInt()
    private fun failure(field: String) = AvmFailure(AvmError.COMPATIBILITY_UNSATISFIED, "Inkompatible Version", field)
}
