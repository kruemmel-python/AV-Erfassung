package de.avm.conformance

import de.av.modular.config.ConfigurationLoader
import de.av.modular.packages.ConfigurationPackageService
import de.av.modular.reporting.WorkRecordCsvImporter
import de.av.modular.validation.ConfigurationValidator
import de.av.modular.validation.Severity
import de.avm.backup.EvidenceBackupMetadata
import de.avm.backup.EvidenceBackupService
import de.avm.canonical.CanonicalEncoding
import de.avm.compatibility.CompatibilityService
import de.avm.diagnostics.DiagnosticContractException
import de.avm.diagnostics.DiagnosticService
import de.avm.errors.AvmError
import de.avm.specification.AvmContracts
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class CheckResult(
    val id: String,
    val subject: String,
    @SerialName("subject_sha256") val subjectSha256: String? = null,
    val status: String,
    @SerialName("test_type") val testType: String,
    @SerialName("expected_outcome") val expectedOutcome: String,
    @SerialName("observed_outcome") val observedOutcome: String,
    @SerialName("expected_error_code") val expectedErrorCode: String? = null,
    @SerialName("observed_error_code") val observedErrorCode: String? = null,
    val detail: String = "",
) {
    val passed: Boolean get() = status == PASS

    companion object { const val PASS = "PASS" }
}

data class Observation(
    val outcome: String,
    val error: AvmError? = null,
    val detail: String = "",
)

class ConformanceRunner(private val root: Path) {
    private val loader = ConfigurationLoader()
    private val profile by lazy { loader.loadProfile(root.resolve("enterprise/profiles/demo_dhl")) }
    private val checks = linkedMapOf<String, () -> List<CheckResult>>(
        "specification" to ::specification,
        "module" to { modules("MODULE-SPEC") },
        "package" to ::packageFormat,
        "work-record" to ::workRecords,
        "backup" to ::backup,
        "diagnostic" to ::diagnostic,
        "plugin" to ::plugin,
        "runtime" to ::runtime,
        "compatibility" to ::compatibility,
    )

    fun run(target: String): List<CheckResult> {
        val results = when (target) {
            "all" -> checks.values.flatMap { it() }
            in checks -> checks.getValue(target)()
            else -> listOf(positive("CLI-001", "cli/$target", Observation(REJECT, AvmError.COMPATIBILITY_UNSATISFIED, "Unbekanntes Prüfziel")))
        }
        require(results.map(CheckResult::id).distinct().size == results.size) { "Conformance-Report enthält doppelte Test-IDs" }
        return results
    }

    private fun specification(): List<CheckResult> = requiredContracts().mapIndexed { index, relative ->
        val observed = if (root.resolve(relative).exists()) Observation(ACCEPT) else Observation(REJECT, AvmError.COMPATIBILITY_UNSATISFIED, "Normativer Vertrag fehlt")
        positive("SPEC-${(index + 1).toString().padStart(3, '0')}", relative, observed)
    }

    private fun modules(prefix: String): List<CheckResult> = listOf("mail_processing", "document_scanning", "internal_logistics").map { moduleId ->
        val subject = "modules/$moduleId"
        val observed = runCatching {
            val loaded = loader.loadModule(root.resolve(subject))
            val errors = ConfigurationValidator().validate(loaded, profile).filter { it.severity == Severity.ERROR }
            require(errors.isEmpty()) { errors.joinToString { "${it.path}: ${it.message}" } }
        }.fold(
            onSuccess = { Observation(ACCEPT) },
            onFailure = { Observation(REJECT, AvmError.PACKAGE_SCHEMA_INVALID, it.message.orEmpty()) },
        )
        positive("$prefix-$moduleId", subject, observed)
    }

    private fun packageFormat(): List<CheckResult> = listOf(
        fileCheck("PACKAGE-001", "specification/package-format/package-manifest.schema.json", AvmError.PACKAGE_SCHEMA_INVALID),
        fileCheck("PACKAGE-002", "specification/package-format/SPECIFICATION.md", AvmError.PACKAGE_SCHEMA_INVALID),
        negative("PACKAGE-ATTACK-001", "conformance/negative-fixtures/package-path-traversal.json", AvmError.PACKAGE_PATH_UNSAFE, packagePathTraversalAttack()),
        negative("PACKAGE-ATTACK-002", "conformance/negative-fixtures/package-revoked-key.json", AvmError.PACKAGE_KEY_REVOKED, packageRevocationAttack()),
    )

    private fun workRecords(): List<CheckResult> {
        val relative = "specification/work-record-v2/examples/valid-multi-employee.csv"
        val source = root.resolve(relative)
        val golden = runCatching {
            val batch = WorkRecordCsvImporter().import(listOf(relative to Files.readString(source)))
            require(batch.issues.isEmpty()) { batch.issues.joinToString() }
            require(batch.conflicts.isEmpty()) { "Unerwarteter Revisionskonflikt" }
            require(batch.records.size == 4) { "Vier Golden Records erwartet, erhalten: ${batch.records.size}" }
        }.fold(
            onSuccess = { Observation(ACCEPT) },
            onFailure = { Observation(REJECT, AvmError.CSV_DIGEST_MISMATCH, it.message.orEmpty()) },
        )
        val content = Files.readString(source)
        val tampered = content.replaceFirst("2026-08-01T04:18:00Z", "2026-08-01T04:19:00Z")
        val attackBatch = WorkRecordCsvImporter().import(listOf("tampered.csv" to tampered))
        val observedError = attackBatch.issues.singleOrNull { it.error == AvmError.PAYLOAD_DIGEST_INVALID }?.error
        val attack = if (attackBatch.records.size == 3 && observedError != null) Observation(REJECT, observedError) else Observation(ACCEPT, detail = "Manipulierter Payload wurde nicht normativ verworfen")
        return listOf(
            positive("WORK-001", relative, golden),
            negative("WORK-ATTACK-001", "conformance/negative-fixtures/work-record-tampered.json", AvmError.PAYLOAD_DIGEST_INVALID, attack),
        )
    }

    private fun backup(): List<CheckResult> {
        val attacks = backupAttacks()
        return listOf(
            fileCheck("BACKUP-001", "specification/backup-v1/backup-manifest.schema.json", AvmError.BACKUP_MANIFEST_INVALID),
            fileCheck("BACKUP-002", "specification/backup-v1/SPECIFICATION.md", AvmError.BACKUP_MANIFEST_INVALID),
            negative("BACKUP-ATTACK-001", "conformance/negative-fixtures/backup-incomplete.json", AvmError.BACKUP_INCOMPLETE, attacks.first),
            negative("BACKUP-ATTACK-002", "conformance/negative-fixtures/backup-revoked-key.json", AvmError.BACKUP_KEY_REVOKED, attacks.second),
        )
    }

    private fun diagnostic(): List<CheckResult> {
        val relative = "specification/diagnostic-v1/examples/valid.json"
        val positiveObservation = runCatching { DiagnosticService().parseStrict(Files.readString(root.resolve(relative))) }.fold(
            onSuccess = { Observation(ACCEPT) },
            onFailure = { Observation(REJECT, AvmError.DIAGNOSTIC_FIELD_FORBIDDEN, it.message.orEmpty()) },
        )
        val attackRelative = "conformance/negative-fixtures/diagnostic-personal-data.json"
        val attackObservation = try {
            DiagnosticService().parseStrict(Files.readString(root.resolve(attackRelative)))
            Observation(ACCEPT, detail = "Personenfeld wurde akzeptiert")
        } catch (exception: DiagnosticContractException) {
            Observation(REJECT, exception.error)
        }
        return listOf(
            positive("DIAGNOSTIC-001", relative, positiveObservation),
            negative("DIAGNOSTIC-ATTACK-001", attackRelative, AvmError.DIAGNOSTIC_FIELD_FORBIDDEN, attackObservation),
        )
    }

    private fun plugin(): List<CheckResult> {
        val attackRelative = "conformance/negative-fixtures/plugin-abi-incompatible.json"
        val reported = JSON.parseToJsonElement(Files.readString(root.resolve(attackRelative))).jsonObject.getValue("reported_abi_version").jsonPrimitive.int
        val attackObservation = if (reported != 1) Observation(REJECT, AvmError.ABI_VERSION_UNSUPPORTED) else Observation(ACCEPT)
        return listOf(
            fileCheck("PLUGIN-001", "specification/plugin-abi-v1/av_module_api_v1.h", AvmError.ABI_VERSION_UNSUPPORTED),
            fileCheck("PLUGIN-002", "enterprise/native-host/CMakeLists.txt", AvmError.ABI_VERSION_UNSUPPORTED),
            negative("PLUGIN-ATTACK-001", attackRelative, AvmError.ABI_VERSION_UNSUPPORTED, attackObservation),
        )
    }

    private fun runtime(): List<CheckResult> = modules("MODULE-RUNTIME") + listOf(
        fileCheck("RUNTIME-001", "enterprise/capture-android/build.gradle.kts", AvmError.COMPATIBILITY_UNSATISFIED),
        fileCheck("RUNTIME-002", "enterprise/reporter-cli/build.gradle.kts", AvmError.COMPATIBILITY_UNSATISFIED),
    )

    private fun compatibility(): List<CheckResult> {
        val relative = "specification/compatibility/compatibility-matrix.json"
        val service = CompatibilityService()
        val required = service.parse(Files.readString(root.resolve(relative)))
        val positiveObservation = if (service.evaluate(required, required).compatible) Observation(ACCEPT) else Observation(REJECT, AvmError.COMPATIBILITY_UNSATISFIED)
        val attackRelative = "conformance/negative-fixtures/compatibility-wrong-major.json"
        val evaluation = service.evaluate(required, service.parse(Files.readString(root.resolve(attackRelative))))
        val attackObservation = if (!evaluation.compatible) Observation(REJECT, evaluation.failures.first().error) else Observation(ACCEPT)
        return listOf(
            positive("COMPAT-001", relative, positiveObservation),
            negative("COMPAT-ATTACK-001", attackRelative, AvmError.COMPATIBILITY_UNSATISFIED, attackObservation),
        )
    }

    private fun packagePathTraversalAttack(): Observation {
        val attack = generatedPath("package-path-traversal.avpkg")
        ZipOutputStream(Files.newOutputStream(attack)).use { zip ->
            zip.putNextEntry(ZipEntry("../outside.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        val verification = ConfigurationPackageService().verify(attack, emptyMap())
        return if (!verification.valid) Observation(REJECT, verification.error, verification.message) else Observation(ACCEPT)
    }

    private fun packageRevocationAttack(): Observation {
        val source = generatedDirectory("package-source")
        Files.writeString(source.resolve("module.json"), "{\"module_id\":\"conformance\"}")
        val output = generatedPath("package-revoked.avpkg")
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val service = ConfigurationPackageService(clock = FIXED_CLOCK)
        service.create(source, output, "conformance-package", "1.0.0-RC2", "conformance-revoked", keys.private)
        val verification = service.verify(output, mapOf("conformance-revoked" to keys.public), setOf("conformance-revoked"))
        return if (!verification.valid) Observation(REJECT, verification.error, verification.message) else Observation(ACCEPT)
    }

    private fun backupAttacks(): Pair<Observation, Observation> {
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val complete = generatedPath("backup-complete.avbackup")
        val incomplete = generatedPath("backup-incomplete.avbackup")
        val service = EvidenceBackupService(clock = FIXED_CLOCK)
        val metadata = EvidenceBackupMetadata(
            backupId = "BACKUP-CONFORMANCE-1", tenantId = "tenant_demo", sourceDeviceId = "DEVICE-CONFORMANCE",
            roomSchemaVersion = 2, coreVersion = VERSION, moduleId = "mail_processing", moduleVersion = VERSION,
            profileId = "demo_dhl", packageFormatVersion = 1, auditHeadHash = "a".repeat(64),
        )
        service.create(complete, metadata, EvidenceBackupService.REQUIRED_FILES.associateWith { "[]".toByteArray() }, "conformance-revoked", keys.private)
        copyZipWithout(complete, incomplete, "data/audit_events.json")
        val incompleteVerification = service.verify(incomplete, mapOf("conformance-revoked" to keys.public))
        val revokedVerification = service.verify(complete, mapOf("conformance-revoked" to keys.public), setOf("conformance-revoked"))
        return Observation(REJECT, incompleteVerification.error, incompleteVerification.message) to
            Observation(REJECT, revokedVerification.error, revokedVerification.message)
    }

    private fun copyZipWithout(source: Path, target: Path, excluded: String) {
        ZipInputStream(Files.newInputStream(source)).use { input ->
            ZipOutputStream(Files.newOutputStream(target)).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.name == excluded) continue
                    output.putNextEntry(ZipEntry(entry.name))
                    input.copyTo(output)
                    output.closeEntry()
                }
            }
        }
    }

    private fun generatedDirectory(name: String): Path = root.resolve("build/conformance-evidence/$name").also(Files::createDirectories)
    private fun generatedPath(name: String): Path = generatedDirectory("generated").resolve(name)

    private fun fileCheck(id: String, relative: String, error: AvmError): CheckResult =
        positive(id, relative, if (root.resolve(relative).exists()) Observation(ACCEPT) else Observation(REJECT, error, "Pflichtartefakt fehlt"))

    private fun positive(id: String, subject: String, observation: Observation): CheckResult = checkResult(id, subject, "POSITIVE", ACCEPT, null, observation)
    private fun negative(id: String, subject: String, expectedError: AvmError, observation: Observation): CheckResult =
        checkResult(id, subject, "NEGATIVE", REJECT, expectedError, observation)

    private fun checkResult(id: String, subject: String, type: String, expected: String, expectedError: AvmError?, observation: Observation): CheckResult {
        val passed = expected == observation.outcome && expectedError == observation.error
        return CheckResult(
            id = id,
            subject = subject.replace('\\', '/'),
            subjectSha256 = subjectDigest(subject),
            status = if (passed) CheckResult.PASS else "FAIL",
            testType = type,
            expectedOutcome = expected,
            observedOutcome = observation.outcome,
            expectedErrorCode = expectedError?.code,
            observedErrorCode = observation.error?.code,
            detail = observation.detail,
        )
    }

    private fun subjectDigest(subject: String): String? {
        val path = root.resolve(subject).normalize()
        if (!path.startsWith(root) || !path.exists()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        if (Files.isRegularFile(path)) return sha256(Files.readAllBytes(path))
        Files.walk(path).use { paths ->
            paths.filter(Files::isRegularFile).sorted().forEach { file ->
                digest.update(path.relativize(file).toString().replace('\\', '/').toByteArray())
                digest.update(byteArrayOf(0))
                digest.update(Files.readAllBytes(file))
                digest.update(byteArrayOf(0))
            }
        }
        return digest.digest().toHex()
    }

    private fun requiredContracts() = listOf(
        "specification/core-model/SPECIFICATION.md",
        "specification/canonical-encoding/SPECIFICATION.md",
        "specification/event-envelope/SPECIFICATION.md",
        "specification/module-format/SPECIFICATION.md",
        "specification/customer-profile/SPECIFICATION.md",
        "specification/package-format/SPECIFICATION.md",
        "specification/work-record-v2/SPECIFICATION.md",
        "specification/backup-v1/SPECIFICATION.md",
        "specification/diagnostic-v1/SPECIFICATION.md",
        "specification/reporting-v1/SPECIFICATION.md",
        "specification/plugin-abi-v1/SPECIFICATION.md",
        "specification/compatibility/SPECIFICATION.md",
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
}

@Serializable
data class ToolIdentity(val name: String, val version: String)

@Serializable
data class ImplementationIdentity(
    val name: String,
    val version: String,
    val commit: String,
    @SerialName("build_id") val buildId: String,
    @SerialName("working_tree_state") val workingTreeState: String,
)

@Serializable
data class ExecutionEnvironment(
    val os: String,
    val architecture: String,
    val java: String,
    @SerialName("cpp_compiler") val cppCompiler: String,
)

@Serializable
data class ReportSummary(val total: Int, val passed: Int, val failed: Int, val skipped: Int)

@Serializable
data class UnsignedConformanceReport(
    val contract: String = REPORT_CONTRACT,
    val release: String = VERSION,
    @SerialName("official_release") val officialRelease: Boolean,
    @SerialName("report_id") val reportId: String,
    @SerialName("generated_at_utc") val generatedAtUtc: String,
    @SerialName("conformance_tool") val conformanceTool: ToolIdentity,
    val implementation: ImplementationIdentity,
    @SerialName("certification_profile") val certificationProfile: String,
    @SerialName("specification_contracts") val specificationContracts: List<String>,
    val environment: ExecutionEnvironment,
    val summary: ReportSummary,
    @SerialName("artifact_manifest_sha256") val artifactManifestSha256: String? = null,
    @SerialName("sbom_sha256") val sbomSha256: String? = null,
    val results: List<CheckResult>,
)

@Serializable
data class EvidenceSignature(
    val algorithm: String,
    @SerialName("key_id") val keyId: String,
    @SerialName("public_key_base64") val publicKeyBase64: String,
    @SerialName("signer_type") val signerType: String,
    @SerialName("trust_status") val trustStatus: String,
    @SerialName("official_release") val officialRelease: Boolean,
    val value: String,
)

@Serializable
data class SignedConformanceReport(
    val contract: String,
    val release: String,
    @SerialName("official_release") val officialRelease: Boolean,
    @SerialName("report_id") val reportId: String,
    @SerialName("generated_at_utc") val generatedAtUtc: String,
    @SerialName("conformance_tool") val conformanceTool: ToolIdentity,
    val implementation: ImplementationIdentity,
    @SerialName("certification_profile") val certificationProfile: String,
    @SerialName("specification_contracts") val specificationContracts: List<String>,
    val environment: ExecutionEnvironment,
    val summary: ReportSummary,
    @SerialName("artifact_manifest_sha256") val artifactManifestSha256: String? = null,
    @SerialName("sbom_sha256") val sbomSha256: String? = null,
    val results: List<CheckResult>,
    @SerialName("evidence_digest") val evidenceDigest: String,
    val signature: EvidenceSignature,
) {
    fun unsigned(): UnsignedConformanceReport = UnsignedConformanceReport(
        contract = contract,
        release = release,
        officialRelease = officialRelease,
        reportId = reportId,
        generatedAtUtc = generatedAtUtc,
        conformanceTool = conformanceTool,
        implementation = implementation,
        certificationProfile = certificationProfile,
        specificationContracts = specificationContracts,
        environment = environment,
        summary = summary,
        artifactManifestSha256 = artifactManifestSha256,
        sbomSha256 = sbomSha256,
        results = results,
    )
}

object ConformanceEvidence {
    fun create(
        root: Path,
        results: List<CheckResult>,
        artifactManifestSha256: String? = null,
        sbomSha256: String? = null,
        identity: SigningIdentity = signingIdentity(root),
    ): SignedConformanceReport {
        val now = Instant.now().toString()
        val unsigned = UnsignedConformanceReport(
            officialRelease = identity.officialRelease,
            reportId = "urn:uuid:${UUID.randomUUID()}",
            generatedAtUtc = now,
            conformanceTool = ToolIdentity("AVM Conformance CLI", VERSION),
            implementation = implementation(root, now),
            certificationProfile = "avm-enterprise-compatible-1.0",
            specificationContracts = AvmContracts.released,
            environment = ExecutionEnvironment(
                os = System.getProperty("os.name"),
                architecture = System.getProperty("os.arch"),
                java = System.getProperty("java.version"),
                cppCompiler = process(root, listOf("g++", "--version")).lineSequence().firstOrNull().orEmpty().ifBlank { "not-reported" },
            ),
            summary = ReportSummary(results.size, results.count(CheckResult::passed), results.count { !it.passed }, 0),
            artifactManifestSha256 = artifactManifestSha256,
            sbomSha256 = sbomSha256,
            results = results,
        )
        val canonical = CanonicalEncoding.json(JSON.encodeToString(unsigned)).toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical).toHex()
        val signer = Signature.getInstance("Ed25519").apply { initSign(identity.privateKey); update(canonical) }
        return SignedConformanceReport(
            contract = unsigned.contract,
            release = unsigned.release,
            officialRelease = unsigned.officialRelease,
            reportId = unsigned.reportId,
            generatedAtUtc = unsigned.generatedAtUtc,
            conformanceTool = unsigned.conformanceTool,
            implementation = unsigned.implementation,
            certificationProfile = unsigned.certificationProfile,
            specificationContracts = unsigned.specificationContracts,
            environment = unsigned.environment,
            summary = unsigned.summary,
            artifactManifestSha256 = unsigned.artifactManifestSha256,
            sbomSha256 = unsigned.sbomSha256,
            results = unsigned.results,
            evidenceDigest = digest,
            signature = EvidenceSignature(
                algorithm = "Ed25519",
                keyId = identity.keyId,
                publicKeyBase64 = Base64.getEncoder().encodeToString(identity.publicKey.encoded),
                signerType = identity.signerType,
                trustStatus = identity.trustStatus,
                officialRelease = identity.officialRelease,
                value = Base64.getEncoder().encodeToString(signer.sign()),
            ),
        )
    }

    fun verify(report: SignedConformanceReport): Boolean {
        if (report.signature.algorithm != "Ed25519") return false
        val canonical = CanonicalEncoding.json(JSON.encodeToString(report.unsigned())).toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical).toHex()
        if (digest != report.evidenceDigest) return false
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(report.signature.publicKeyBase64)),
        )
        return Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(canonical)
            verify(Base64.getDecoder().decode(report.signature.value))
        }
    }

    private fun implementation(root: Path, now: String): ImplementationIdentity {
        val source = sourceControlIdentity(root)
        val state = if (source.dirty) "dirty" else "clean"
        val buildId = System.getenv("AVM_BUILD_ID") ?: System.getenv("GITHUB_RUN_ID")?.let { "github-$it" } ?: "local-${now.replace(Regex("[^0-9]"), "")}"
        return ImplementationIdentity("AVM Enterprise", VERSION, source.commit, buildId, state)
    }

    fun signingIdentity(root: Path): SigningIdentity {
        val privatePath = System.getenv("AVM_CONFORMANCE_PRIVATE_KEY")
        val publicPath = System.getenv("AVM_CONFORMANCE_PUBLIC_KEY")
        if (privatePath == null && publicPath == null) {
            val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            return SigningIdentity(
                pair.private, pair.public, "ephemeral-${sha256(pair.public.encoded).take(24)}",
                "ephemeral-test-key", "untrusted-development-evidence", false,
            )
        }
        require(privatePath != null && publicPath != null) { "Für eine vertrauenswürdige Signatur müssen privater und öffentlicher Conformance-Schlüssel angegeben werden" }
        val pair = KeyPair(loadPublic(Path.of(publicPath)), loadPrivate(Path.of(privatePath)))
        val keyId = System.getenv("AVM_CONFORMANCE_KEY_ID") ?: "sha256-${sha256(pair.public.encoded).take(24)}"
        val official = System.getenv("AVM_OFFICIAL_RELEASE").equals("true", ignoreCase = true)
        if (official) {
            require(System.getenv("GITHUB_ACTIONS") == "true") { "Offizielle Evidence erfordert GitHub Actions" }
            require(System.getenv("GITHUB_REPOSITORY") == "kruemmel-python/AV-Erfassung") { "Unerwartetes Release-Repository" }
            require(System.getenv("GITHUB_REF") == "refs/heads/main") { "Offizielle Evidence darf nur aus main entstehen" }
            val source = sourceControlIdentity(root)
            require(!source.dirty) { "Offizielle Evidence erfordert einen sauberen Arbeitsbaum" }
            require(System.getenv("GITHUB_SHA") == source.commit) { "Geprüfte Quellassertion und Runner-Commit stimmen nicht überein" }
            val workflowRef = System.getenv("GITHUB_WORKFLOW_REF").orEmpty()
            require(workflowRef.contains("/.github/workflows/product-suite-release.yml@refs/heads/main")) {
                "Offizielle Evidence erfordert den freigegebenen Produkt- und RC2-Releaseworkflow aus main"
            }
            require(!System.getenv("GITHUB_RUN_ID").isNullOrBlank()) { "Offizielle Evidence erfordert eine externe Run-ID" }
            require(System.getenv("AVM_RELEASE_ENVIRONMENT") == "avm-release") { "Geschützte Releaseumgebung fehlt" }
            require(System.getenv("AVM_RELEASE_APPROVED") == "true") { "Releasefreigabe fehlt" }
            require(keyId.matches(Regex("[a-z0-9-]{8,64}"))) { "Ungültige Release-Key-ID" }
            val trustedKeyPath = root.resolve("specification/trust/keys/$keyId-public.pem").normalize()
            require(Files.isRegularFile(trustedKeyPath)) { "Öffentlicher Vertrauensanker ist nicht registriert: $keyId" }
            require(loadPublic(trustedKeyPath).encoded.contentEquals(pair.public.encoded)) {
                "Release-Schlüssel entspricht nicht dem registrierten Vertrauensanker"
            }
        }
        return SigningIdentity(
            pair.private, pair.public, keyId, "organization-managed-release-key",
            if (official) "trusted-official-release" else "externally-configured-development-evidence", official,
        )
    }

    private fun loadPrivate(path: Path): PrivateKey = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(readPem(path)))
    private fun loadPublic(path: Path): PublicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(readPem(path)))
    private fun readPem(path: Path): ByteArray = Base64.getMimeDecoder().decode(
        Files.readString(path).lineSequence().filterNot { it.startsWith("---") }.joinToString(""),
    )
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
}

data class SigningIdentity(
    val privateKey: PrivateKey,
    val publicKey: PublicKey,
    val keyId: String,
    val signerType: String,
    val trustStatus: String,
    val officialRelease: Boolean,
)

data class SourceControlIdentity(val commit: String, val dirty: Boolean)

fun sourceControlIdentity(root: Path): SourceControlIdentity {
    val assertedCommit = System.getenv("AVM_SOURCE_COMMIT")
    val assertedDirty = System.getenv("AVM_SOURCE_DIRTY")
    if (assertedCommit != null || assertedDirty != null) {
        require(System.getenv("GITHUB_ACTIONS") == "true") { "Quellassertionen sind ausschließlich in GitHub Actions zulässig" }
        require(assertedCommit?.matches(Regex("[0-9a-f]{40}")) == true) { "Ungültige Quell-Commit-Assertion" }
        val dirty = when (assertedDirty) {
            "true" -> true
            "false" -> false
            else -> error("Ungültige Arbeitsbaum-Assertion")
        }
        require(assertedCommit == System.getenv("GITHUB_SHA")) { "Quell-Commit-Assertion entspricht nicht GITHUB_SHA" }
        return SourceControlIdentity(assertedCommit, dirty)
    }
    val commit = process(root, listOf("git", "rev-parse", "HEAD")).ifBlank { "unavailable" }
    return SourceControlIdentity(commit, process(root, listOf("git", "status", "--porcelain")).isNotBlank())
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "release-evidence") {
        val root = Path.of(args.getOrElse(1) { "." }).toAbsolutePath().normalize()
        ReleaseEvidence.create(root)
        return
    }
    require(args.size >= 2 && args[0] == "test") {
        "Verwendung: avm-conformance test <all|module|package|work-record|backup|diagnostic|plugin|runtime|compatibility> [Projektwurzel]"
    }
    val root = Path.of(args.getOrElse(2) { "." }).toAbsolutePath().normalize()
    val results = ConformanceRunner(root).run(args[1])
    results.forEach { check ->
        println("${check.status} ${check.id} ${check.subject} expected=${check.expectedOutcome}/${check.expectedErrorCode ?: "-"} observed=${check.observedOutcome}/${check.observedErrorCode ?: "-"}")
    }
    val report = ConformanceEvidence.create(root, results)
    check(ConformanceEvidence.verify(report)) { "Der erzeugte Conformance-Evidence-Report ist kryptografisch ungültig" }
    val reportFile = root.resolve("build/reports/avm-conformance.json")
    Files.createDirectories(reportFile.parent)
    Files.writeString(reportFile, PRETTY_JSON.encodeToString(report))
    check(results.all(CheckResult::passed)) { "AVM-Konformitätsprüfung fehlgeschlagen; Bericht: ${root.relativize(reportFile)}" }
}

private fun process(root: Path, command: List<String>): String = runCatching {
    val process = ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    if (process.waitFor() == 0) output else ""
}.getOrDefault("")

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private const val ACCEPT = "ACCEPT"
private const val REJECT = "REJECT"
private const val REPORT_CONTRACT = "avm-conformance-report-1.0"
private const val VERSION = "1.0.0-RC2"
private val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC)
private val JSON = Json { encodeDefaults = true; explicitNulls = true }
private val PRETTY_JSON = Json { encodeDefaults = true; explicitNulls = true; prettyPrint = true }
