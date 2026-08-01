package de.avm.specification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContractVersion(
    val id: String,
    val version: String,
    val status: String = "released",
)

object AvmContracts {
    const val CORE_MODEL = "avm-core-model-1.0"
    const val CANONICAL_ENCODING = "avm-canonical-encoding-1.0"
    const val EVENT_ENVELOPE = "avm-event-envelope-1.0"
    const val MODULE_FORMAT = "avm-module-format-1.0"
    const val CUSTOMER_PROFILE = "avm-customer-profile-1.0"
    const val PACKAGE_FORMAT = "avm-package-format-1.0"
    const val WORK_RECORD = "avm-work-record-2.0"
    const val BACKUP_FORMAT = "avm-backup-format-1.0"
    const val DIAGNOSTIC_FORMAT = "avm-diagnostic-format-1.0"
    const val REPORT_DEFINITION = "avm-report-definition-1.0"
    const val PLUGIN_ABI = "avm-native-plugin-abi-1"
    const val COMPATIBILITY = "avm-compatibility-contract-1.0"
    const val ARTIFACT_MANIFEST = "avm-artifact-manifest-1.0"
    const val CONFORMANCE_REPORT = "avm-conformance-report-1.0"
    const val RELEASE_ENVELOPE = "avm-release-envelope-1.0"
    const val RELEASE_KEY_REGISTRY = "avm-release-key-registry-1.0"

    val released = listOf(
        CORE_MODEL, CANONICAL_ENCODING, EVENT_ENVELOPE, MODULE_FORMAT, CUSTOMER_PROFILE,
        PACKAGE_FORMAT, WORK_RECORD, BACKUP_FORMAT, DIAGNOSTIC_FORMAT, REPORT_DEFINITION,
        PLUGIN_ABI, COMPATIBILITY, ARTIFACT_MANIFEST, CONFORMANCE_REPORT, RELEASE_ENVELOPE,
        RELEASE_KEY_REGISTRY,
    )
}

@Serializable
data class ComponentCapabilities(
    val component: String,
    val version: String,
    @SerialName("supported_contracts") val supportedContracts: Map<String, List<Int>>,
    val capabilities: Set<String>,
)

@Serializable
data class CapabilityRequirement(
    val contract: String,
    @SerialName("accepted_versions") val acceptedVersions: Set<Int>,
    @SerialName("required_capabilities") val requiredCapabilities: Set<String> = emptySet(),
)

data class CapabilityDecision(val compatible: Boolean, val reasons: List<String>)

object CapabilityNegotiation {
    fun evaluate(component: ComponentCapabilities, requirements: List<CapabilityRequirement>): CapabilityDecision {
        val reasons = buildList {
            requirements.forEach { requirement ->
                val offered = component.supportedContracts[requirement.contract].orEmpty().toSet()
                if (offered.intersect(requirement.acceptedVersions).isEmpty()) add("contract:${requirement.contract}")
                val missing = requirement.requiredCapabilities - component.capabilities
                missing.sorted().forEach { add("capability:$it") }
            }
        }
        return CapabilityDecision(reasons.isEmpty(), reasons)
    }
}
