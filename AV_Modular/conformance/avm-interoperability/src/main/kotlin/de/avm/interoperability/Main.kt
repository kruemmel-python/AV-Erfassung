package de.avm.interoperability

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import kotlin.system.exitProcess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class InteroperabilityEvidence(
    val contract: String,
    @SerialName("evidence_id") val evidenceId: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("generated_at_utc") val generatedAtUtc: String,
    val scope: String,
    val implementations: List<ImplementationEvidence>,
    val vectors: List<VectorEvidence>,
    @SerialName("pilot_organizations") val pilotOrganizations: List<String> = emptyList(),
    val approvals: List<ApprovalEvidence> = emptyList(),
)

@Serializable
data class ImplementationEvidence(
    @SerialName("implementation_id") val implementationId: String,
    val name: String,
    val version: String,
    val language: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("source_digest") val sourceDigest: String,
    @SerialName("codebase_uri") val codebaseUri: String,
)

@Serializable
data class VectorEvidence(
    @SerialName("vector_id") val vectorId: String,
    val subject: String,
    @SerialName("subject_sha256") val subjectSha256: String,
    val results: List<VectorResult>,
)

@Serializable
data class VectorResult(
    @SerialName("implementation_id") val implementationId: String,
    val outcome: String,
    val digest: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
)

@Serializable
data class ApprovalEvidence(
    @SerialName("approver_id") val approverId: String,
    @SerialName("organization_id") val organizationId: String,
    val role: String,
    @SerialName("approved_at_utc") val approvedAtUtc: String,
    @SerialName("independence_attested") val independenceAttested: Boolean,
)

@Serializable
data class GateCheck(val id: String, val passed: Boolean, val detail: String)

@Serializable
data class InteroperabilityReport(
    val contract: String = "avm-interoperability-report-1.0",
    @SerialName("evidence_id") val evidenceId: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("external_independence_required") val externalIndependenceRequired: Boolean,
    val passed: Boolean,
    val checks: List<GateCheck>,
)

object InteroperabilityVerifier {
    private val digestPattern = Regex("[0-9a-f]{64}")
    private val errorPattern = Regex("AVM-[A-Z]+-[0-9]{4}")

    fun parse(path: Path): InteroperabilityEvidence =
        Json.decodeFromString(Files.readString(path))

    fun verify(
        evidence: InteroperabilityEvidence,
        requireExternalIndependence: Boolean,
        evidenceRoot: Path? = null,
    ): InteroperabilityReport {
        val checks = mutableListOf<GateCheck>()
        fun check(id: String, condition: Boolean, detail: String) {
            checks += GateCheck(id, condition, detail)
        }

        check("INTEROP-CONTRACT-001", evidence.contract == "avm-interoperability-evidence-1.0", "Versionierter Evidence-Vertrag")
        check("INTEROP-TIME-001", runCatching { Instant.parse(evidence.generatedAtUtc) }.isSuccess, "UTC-Erzeugungszeit ist ISO-8601")

        val implementationIds = evidence.implementations.map(ImplementationEvidence::implementationId)
        check("INTEROP-IMPLEMENTATION-001", implementationIds.size >= 2 && implementationIds.distinct().size == implementationIds.size, "Mindestens zwei eindeutige Implementierungen")
        check("INTEROP-CODEBASE-001", evidence.implementations.map(ImplementationEvidence::codebaseUri).distinct().size >= 2, "Getrennte Codebasen")
        check("INTEROP-LANGUAGE-001", evidence.implementations.map(ImplementationEvidence::language).distinct().size >= 2, "Unabhängige Programmiersprachen")
        check("INTEROP-SOURCE-001", evidence.implementations.all { digestPattern.matches(it.sourceDigest) }, "Quellstände sind über SHA-256 gebunden")

        val vectorIds = evidence.vectors.map(VectorEvidence::vectorId)
        check("INTEROP-VECTOR-001", vectorIds.isNotEmpty() && vectorIds.distinct().size == vectorIds.size, "Eindeutige Testvektoren")
        check("INTEROP-SUBJECT-001", evidence.vectors.all { digestPattern.matches(it.subjectSha256) && ':' !in it.subject && '\\' !in it.subject }, "Relative Subjects mit SHA-256")

        if (evidenceRoot != null) {
            val normalizedRoot = evidenceRoot.toAbsolutePath().normalize()
            fun resolveSafe(relative: String): Path? {
                if (relative.isBlank() || ':' in relative || '\\' in relative) return null
                val candidate = normalizedRoot.resolve(relative).normalize()
                return candidate.takeIf { it.startsWith(normalizedRoot) && Files.isRegularFile(it) }
            }
            val sourceBindings = evidence.implementations.all { implementation ->
                resolveSafe(implementation.codebaseUri)?.let(::sha256) == implementation.sourceDigest
            }
            check("INTEROP-SOURCE-BINDING-001", sourceBindings, "Quell-Digests stimmen bytegenau mit dem Evidence-Bundle überein")
            val subjectBindings = evidence.vectors.all { vector ->
                resolveSafe(vector.subject)?.let(::sha256) == vector.subjectSha256
            }
            check("INTEROP-SUBJECT-BINDING-001", subjectBindings, "Vektor-Digests stimmen bytegenau mit dem Evidence-Bundle überein")
        }

        val expectedImplementations = implementationIds.toSet()
        val completeResults = evidence.vectors.all { vector ->
            vector.results.map(VectorResult::implementationId).toSet() == expectedImplementations &&
                vector.results.size == expectedImplementations.size
        }
        check("INTEROP-MATRIX-001", completeResults, "Jede Implementierung bewertet jeden Vektor genau einmal")

        val validOutcomes = evidence.vectors.flatMap(VectorEvidence::results).all { result ->
            when (result.outcome) {
                "ACCEPT" -> result.errorCode == null && result.digest?.let(digestPattern::matches) == true
                "REJECT" -> result.digest == null && result.errorCode?.let(errorPattern::matches) == true
                else -> false
            }
        }
        check("INTEROP-RESULT-001", validOutcomes, "Ergebnisse verwenden normative Digests oder Fehlercodes")

        val sameDecisions = evidence.vectors.all { vector ->
            vector.results.map { Triple(it.outcome, it.digest, it.errorCode) }.distinct().size == 1
        }
        check("INTEROP-DECISION-001", sameDecisions, "Alle Implementierungen liefern identische Entscheidungen")

        if (requireExternalIndependence) {
            val organizations = evidence.implementations.map(ImplementationEvidence::organizationId).toSet()
            val approvers = evidence.approvals.map(ApprovalEvidence::approverId)
            check("FINAL-SCOPE-001", evidence.scope == "industry-pilot", "Evidence stammt aus einem Industriepilot")
            check("FINAL-ORGANIZATION-001", organizations.size >= 2 && evidence.pilotOrganizations.toSet().containsAll(organizations), "Mindestens zwei externe Organisationen")
            check("FINAL-APPROVAL-001", approvers.size >= 2 && approvers.distinct().size == approvers.size && evidence.approvals.all(ApprovalEvidence::independenceAttested), "Zwei unabhängige Vier-Augen-Freigaben")
        }

        return InteroperabilityReport(
            evidenceId = evidence.evidenceId,
            profileId = evidence.profileId,
            externalIndependenceRequired = requireExternalIndependence,
            passed = checks.all(GateCheck::passed),
            checks = checks,
        )
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { byte -> "%02x".format(byte) }
}

private val outputJson = Json { prettyPrint = true }

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "baseline"
    val (path, external, evidenceRoot) = when (command) {
        "baseline" -> {
            val root = Path.of(args.getOrElse(1) { "." })
            Triple(root.resolve("pilot/evidence/reference-cross-language.json"), false, root)
        }
        "verify", "final-readiness" -> {
            val evidencePath = Path.of(requireNotNull(args.getOrNull(1)) { "Evidence-Datei fehlt" })
            val root = args.getOrNull(2)?.let(Path::of) ?: Path.of(".")
            Triple(evidencePath, command == "final-readiness", root)
        }
        else -> error("Verwendung: avm-interoperability <baseline [AVM-Wurzel]|verify <Evidence> [Evidence-Wurzel]|final-readiness <Evidence> [Evidence-Wurzel]>")
    }
    val evidence = InteroperabilityVerifier.parse(path)
    val report = InteroperabilityVerifier.verify(evidence, external, evidenceRoot)
    println(outputJson.encodeToString(report))
    if (!report.passed) exitProcess(2)
}
