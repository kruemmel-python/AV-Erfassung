package de.avm.conformance

import de.avm.canonical.CanonicalEncoding
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReleaseSource(val commit: String, val dirty: Boolean)

@Serializable
data class ReleaseBuild(
    val workflow: String,
    @SerialName("run_id") val runId: String,
    val result: String,
)

@Serializable
data class SbomComponent(val type: String, val name: String, val version: String)

@Serializable
data class AvmSbom(
    @SerialName("bomFormat") val bomFormat: String = "CycloneDX",
    @SerialName("specVersion") val specVersion: String = "1.6",
    val version: Int = 1,
    val components: List<SbomComponent>,
)

@Serializable
data class ReleaseArtifact(val path: String, val size: Long, val sha256: String)

@Serializable
data class UnsignedArtifactManifest(
    val contract: String = "avm-artifact-manifest-1.0",
    val release: String = RC_VERSION,
    @SerialName("generated_at_utc") val generatedAtUtc: String,
    @SerialName("official_release") val officialRelease: Boolean,
    val source: ReleaseSource,
    @SerialName("sbom_sha256") val sbomSha256: String,
    val artifacts: List<ReleaseArtifact>,
)

@Serializable
data class SignedArtifactManifest(
    val contract: String,
    val release: String,
    @SerialName("generated_at_utc") val generatedAtUtc: String,
    @SerialName("official_release") val officialRelease: Boolean,
    val source: ReleaseSource,
    @SerialName("sbom_sha256") val sbomSha256: String,
    val artifacts: List<ReleaseArtifact>,
    @SerialName("evidence_digest") val evidenceDigest: String,
    val signature: EvidenceSignature,
) {
    fun unsigned() = UnsignedArtifactManifest(contract, release, generatedAtUtc, officialRelease, source, sbomSha256, artifacts)
}

@Serializable
data class UnsignedReleaseEnvelope(
    val contract: String = "avm-release-envelope-1.0",
    val release: String = RC_VERSION,
    @SerialName("official_release") val officialRelease: Boolean,
    val source: ReleaseSource,
    val build: ReleaseBuild,
    @SerialName("artifact_manifest_sha256") val artifactManifestSha256: String,
    @SerialName("conformance_report_sha256") val conformanceReportSha256: String,
    @SerialName("sbom_sha256") val sbomSha256: String,
)

@Serializable
data class SignedReleaseEnvelope(
    val contract: String,
    val release: String,
    @SerialName("official_release") val officialRelease: Boolean,
    val source: ReleaseSource,
    val build: ReleaseBuild,
    @SerialName("artifact_manifest_sha256") val artifactManifestSha256: String,
    @SerialName("conformance_report_sha256") val conformanceReportSha256: String,
    @SerialName("sbom_sha256") val sbomSha256: String,
    @SerialName("evidence_digest") val evidenceDigest: String,
    val signature: EvidenceSignature,
) {
    fun unsigned() = UnsignedReleaseEnvelope(
        contract, release, officialRelease, source, build, artifactManifestSha256, conformanceReportSha256, sbomSha256,
    )
}

object ReleaseEvidence {
    fun create(root: Path) {
        val outputDirectory = root.resolve("build/distributions")
        Files.createDirectories(outputDirectory)
        val identity = ConformanceEvidence.signingIdentity(root)
        val source = source(root)
        require(!identity.officialRelease || !source.dirty) { "Offizielles Release-Envelope darf keinen dirty-Quellstand binden" }

        val sbom = AvmSbom(components = listOf(
            SbomComponent("application", "AVM Specification", RC_VERSION),
            SbomComponent("application", "AVM Conformance", RC_VERSION),
            SbomComponent("application", "AVM Enterprise Capture", RC_VERSION),
            SbomComponent("application", "AVM Enterprise Reporter", RC_VERSION),
            SbomComponent("application", "AVM Enterprise Designer", RC_VERSION),
            SbomComponent("application", "AVM Enterprise Profile Tool", RC_VERSION),
            SbomComponent("library", "AVM Native Plugin ABI", "1"),
        ))
        val sbomFile = outputDirectory.resolve("AVM-1.0.0-RC1-SBOM.cdx.json")
        Files.writeString(sbomFile, RELEASE_PRETTY_JSON.encodeToString(sbom))
        val sbomDigest = sha256(Files.readAllBytes(sbomFile))

        val artifacts = artifactPaths(identity.officialRelease).map { relative ->
            val file = root.resolve(relative)
            require(Files.isRegularFile(file)) { "RC1-Artefakt fehlt: $relative" }
            ReleaseArtifact(relative, Files.size(file), sha256(Files.readAllBytes(file)))
        } + ReleaseArtifact("build/distributions/${sbomFile.fileName}", Files.size(sbomFile), sbomDigest)

        val unsignedManifest = UnsignedArtifactManifest(
            generatedAtUtc = Instant.now().toString(), officialRelease = identity.officialRelease,
            source = source, sbomSha256 = sbomDigest, artifacts = artifacts,
        )
        val manifestSignature = sign(unsignedManifest, identity)
        val manifest = SignedArtifactManifest(
            unsignedManifest.contract, unsignedManifest.release, unsignedManifest.generatedAtUtc,
            unsignedManifest.officialRelease, unsignedManifest.source, unsignedManifest.sbomSha256,
            unsignedManifest.artifacts, manifestSignature.first, manifestSignature.second,
        )
        require(verify(manifest.unsigned(), manifest.evidenceDigest, manifest.signature)) { "Artefaktmanifest-Signatur ist ungültig" }
        val manifestFile = outputDirectory.resolve("AVM-1.0.0-RC1-SHA256SUMS.json")
        Files.writeString(manifestFile, RELEASE_PRETTY_JSON.encodeToString(manifest))
        val manifestDigest = sha256(Files.readAllBytes(manifestFile))

        val results = ConformanceRunner(root).run("all")
        check(results.all(CheckResult::passed)) { "Finaler Conformance-Lauf ist fehlgeschlagen" }
        val report = ConformanceEvidence.create(root, results, manifestDigest, sbomDigest, identity)
        require(ConformanceEvidence.verify(report)) { "Finaler Conformance-Report ist ungültig" }
        val reportFile = root.resolve("build/reports/avm-conformance.json")
        Files.createDirectories(reportFile.parent)
        Files.writeString(reportFile, RELEASE_PRETTY_JSON.encodeToString(report))
        val reportDigest = sha256(Files.readAllBytes(reportFile))

        val unsignedEnvelope = UnsignedReleaseEnvelope(
            officialRelease = identity.officialRelease,
            source = source,
            build = build(),
            artifactManifestSha256 = manifestDigest,
            conformanceReportSha256 = reportDigest,
            sbomSha256 = sbomDigest,
        )
        val envelopeSignature = sign(unsignedEnvelope, identity)
        val envelope = SignedReleaseEnvelope(
            unsignedEnvelope.contract, unsignedEnvelope.release, unsignedEnvelope.officialRelease,
            unsignedEnvelope.source, unsignedEnvelope.build, unsignedEnvelope.artifactManifestSha256,
            unsignedEnvelope.conformanceReportSha256, unsignedEnvelope.sbomSha256,
            envelopeSignature.first, envelopeSignature.second,
        )
        require(verify(envelope.unsigned(), envelope.evidenceDigest, envelope.signature)) { "Release-Envelope-Signatur ist ungültig" }
        Files.writeString(outputDirectory.resolve("AVM-1.0.0-RC1-RELEASE-ENVELOPE.json"), RELEASE_PRETTY_JSON.encodeToString(envelope))
    }

    private fun artifactPaths(officialRelease: Boolean) = listOf(
        "build/distributions/AVM-Specification-1.0.0-RC1.zip",
        "conformance/avm-conformance-cli/build/distributions/avm-conformance-1.0.0-RC1.zip",
        if (officialRelease) {
            "enterprise/capture-android/build/outputs/apk/release/capture-android-release.apk"
        } else {
            "enterprise/capture-android/build/outputs/apk/debug/capture-android-debug.apk"
        },
        "enterprise/reporter-cli/build/distributions/reporter-cli-1.0.0-RC1.zip",
        "enterprise/designer-desktop/build/distributions/designer-desktop-1.0.0-RC1.zip",
        "enterprise/profile-tool/build/distributions/profile-tool-1.0.0-RC1.zip",
        "build/native-conformance-win/avm_canonical_golden.exe",
        "build/native-host-win/av_module_host_test.exe",
    )

    private fun source(root: Path): ReleaseSource {
        val source = sourceControlIdentity(root)
        return ReleaseSource(source.commit, source.dirty)
    }

    private fun build(): ReleaseBuild = ReleaseBuild(
        workflow = System.getenv("GITHUB_WORKFLOW") ?: "local-release-candidate-build",
        runId = System.getenv("AVM_BUILD_ID") ?: System.getenv("GITHUB_RUN_ID") ?: "local",
        result = "PASS",
    )

    private inline fun <reified T> sign(value: T, identity: SigningIdentity): Pair<String, EvidenceSignature> {
        val canonical = CanonicalEncoding.json(RELEASE_JSON.encodeToString(value)).toByteArray()
        val digest = sha256(canonical)
        val signature = Signature.getInstance("Ed25519").run {
            initSign(identity.privateKey)
            update(canonical)
            sign()
        }
        return digest to EvidenceSignature(
            "Ed25519", identity.keyId, Base64.getEncoder().encodeToString(identity.publicKey.encoded),
            identity.signerType, identity.trustStatus, identity.officialRelease,
            Base64.getEncoder().encodeToString(signature),
        )
    }

    private inline fun <reified T> verify(value: T, digest: String, signature: EvidenceSignature): Boolean {
        val canonical = CanonicalEncoding.json(RELEASE_JSON.encodeToString(value)).toByteArray()
        if (sha256(canonical) != digest) return false
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(signature.publicKeyBase64)),
        )
        return Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(canonical)
            verify(Base64.getDecoder().decode(signature.value))
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

private const val RC_VERSION = "1.0.0-RC1"
private val RELEASE_JSON = Json { encodeDefaults = true; explicitNulls = true }
private val RELEASE_PRETTY_JSON = Json { encodeDefaults = true; explicitNulls = true; prettyPrint = true }
