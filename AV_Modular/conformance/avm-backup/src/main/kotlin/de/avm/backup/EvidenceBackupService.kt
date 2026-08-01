package de.avm.backup

import de.avm.errors.AvmError
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val path: String,
    val sha256: String,
    val size: Long,
)

@Serializable
data class EvidenceBackupManifest(
    val contract: String = CONTRACT,
    @SerialName("backup_id") val backupId: String,
    @SerialName("created_at_utc") val createdAtUtc: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("room_schema_version") val roomSchemaVersion: Int,
    @SerialName("core_version") val coreVersion: String,
    @SerialName("module_id") val moduleId: String,
    @SerialName("module_version") val moduleVersion: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("package_format_version") val packageFormatVersion: Int,
    @SerialName("audit_head_hash") val auditHeadHash: String,
    @SerialName("key_id") val keyId: String,
    val algorithm: String = "Ed25519",
    val files: List<BackupFile>,
    @SerialName("signature_base64") val signatureBase64: String,
) {
    companion object { const val CONTRACT = "av-evidence-backup-v1" }
}

data class EvidenceBackupMetadata(
    val backupId: String,
    val tenantId: String,
    val sourceDeviceId: String,
    val roomSchemaVersion: Int,
    val coreVersion: String,
    val moduleId: String,
    val moduleVersion: String,
    val profileId: String,
    val packageFormatVersion: Int,
    val auditHeadHash: String,
)

data class EvidenceBackupVerification(
    val valid: Boolean,
    val message: String,
    val manifest: EvidenceBackupManifest? = null,
    val error: AvmError? = null,
)

class EvidenceBackupService(
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true },
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(
        output: Path,
        metadata: EvidenceBackupMetadata,
        logicalFiles: Map<String, ByteArray>,
        keyId: String,
        privateKey: PrivateKey,
    ): EvidenceBackupManifest {
        validateMetadata(metadata)
        require(REQUIRED_FILES.all(logicalFiles::containsKey)) { "Vollständige Backupdaten fehlen: ${REQUIRED_FILES - logicalFiles.keys}" }
        require(logicalFiles.keys.all(::safeLogicalPath)) { "Backup enthält einen unsicheren oder reservierten Pfad" }
        val files = logicalFiles.toSortedMap().map { (path, bytes) -> BackupFile(path, sha256(bytes), bytes.size.toLong()) }
        val createdAt = Instant.now(clock).toString()
        val unsigned = unsignedPayload(metadata, createdAt, keyId, files)
        val signer = Signature.getInstance("Ed25519").apply { initSign(privateKey); update(unsigned.toByteArray(Charsets.UTF_8)) }
        val manifest = EvidenceBackupManifest(
            backupId = metadata.backupId, createdAtUtc = createdAt, tenantId = metadata.tenantId,
            sourceDeviceId = metadata.sourceDeviceId, roomSchemaVersion = metadata.roomSchemaVersion,
            coreVersion = metadata.coreVersion, moduleId = metadata.moduleId, moduleVersion = metadata.moduleVersion,
            profileId = metadata.profileId, packageFormatVersion = metadata.packageFormatVersion,
            auditHeadHash = metadata.auditHeadHash, keyId = keyId, files = files,
            signatureBase64 = Base64.getEncoder().encodeToString(signer.sign()),
        )
        output.toAbsolutePath().parent?.let(Files::createDirectories)
        ZipOutputStream(Files.newOutputStream(output)).use { zip ->
            files.forEach { descriptor ->
                zip.putNextEntry(ZipEntry(descriptor.path))
                zip.write(logicalFiles.getValue(descriptor.path))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(MANIFEST_PATH))
            zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return manifest
    }

    fun verify(file: Path, trustedKeys: Map<String, PublicKey>, revokedKeyIds: Set<String> = emptySet()): EvidenceBackupVerification {
        val entries = runCatching { readEntries(file) }
            .getOrElse { return EvidenceBackupVerification(false, it.message ?: "Backup nicht lesbar", error = AvmError.BACKUP_MANIFEST_INVALID) }
        val manifestBytes = entries[MANIFEST_PATH]
            ?: return EvidenceBackupVerification(false, "Backupmanifest fehlt", error = AvmError.BACKUP_MANIFEST_INVALID)
        val manifest = runCatching { json.decodeFromString<EvidenceBackupManifest>(manifestBytes.toString(Charsets.UTF_8)) }
            .getOrElse { return EvidenceBackupVerification(false, "Backupmanifest ungültig: ${it.message}", error = AvmError.BACKUP_MANIFEST_INVALID) }
        if (manifest.contract != EvidenceBackupManifest.CONTRACT) return EvidenceBackupVerification(false, "Backupvertrag nicht unterstützt", manifest, AvmError.CONTRACT_VERSION_UNSUPPORTED)
        if (manifest.keyId in revokedKeyIds) return EvidenceBackupVerification(false, "Backupschlüssel ist gesperrt", manifest, AvmError.BACKUP_KEY_REVOKED)
        val key = trustedKeys[manifest.keyId]
            ?: return EvidenceBackupVerification(false, "Backupschlüssel ist nicht vertrauenswürdig", manifest, AvmError.BACKUP_SIGNATURE_INVALID)
        if (manifest.algorithm != "Ed25519") return EvidenceBackupVerification(false, "Signaturalgorithmus nicht unterstützt", manifest, AvmError.BACKUP_SIGNATURE_INVALID)
        if (REQUIRED_FILES.any { required -> manifest.files.none { it.path == required } }) return EvidenceBackupVerification(false, "Backup ist unvollständig", manifest, AvmError.BACKUP_INCOMPLETE)
        if (entries.keys.any { it != MANIFEST_PATH && it !in manifest.files.map(BackupFile::path) }) return EvidenceBackupVerification(false, "Nicht deklarierte Backupdatei", manifest, AvmError.BACKUP_INCOMPLETE)
        manifest.files.forEach { descriptor ->
            val bytes = entries[descriptor.path]
                ?: return EvidenceBackupVerification(false, "Backupdatei fehlt: ${descriptor.path}", manifest, AvmError.BACKUP_INCOMPLETE)
            if (bytes.size.toLong() != descriptor.size || sha256(bytes) != descriptor.sha256) {
                return EvidenceBackupVerification(false, "Backupdatei verändert: ${descriptor.path}", manifest, AvmError.BACKUP_SIGNATURE_INVALID)
            }
        }
        val metadata = EvidenceBackupMetadata(
            manifest.backupId, manifest.tenantId, manifest.sourceDeviceId, manifest.roomSchemaVersion,
            manifest.coreVersion, manifest.moduleId, manifest.moduleVersion, manifest.profileId,
            manifest.packageFormatVersion, manifest.auditHeadHash,
        )
        val verifier = Signature.getInstance("Ed25519").apply {
            initVerify(key)
            update(unsignedPayload(metadata, manifest.createdAtUtc, manifest.keyId, manifest.files).toByteArray(Charsets.UTF_8))
        }
        val signature = runCatching { Base64.getDecoder().decode(manifest.signatureBase64) }
            .getOrElse { return EvidenceBackupVerification(false, "Backupsignatur ist ungültiges Base64", manifest, AvmError.BACKUP_SIGNATURE_INVALID) }
        return if (verifier.verify(signature)) EvidenceBackupVerification(true, "Backup vollständig, unverändert und signiert", manifest)
        else EvidenceBackupVerification(false, "Backupsignatur ungültig", manifest, AvmError.BACKUP_SIGNATURE_INVALID)
    }

    fun extractVerified(file: Path, destination: Path, trustedKeys: Map<String, PublicKey>, revokedKeyIds: Set<String> = emptySet()) {
        val verification = verify(file, trustedKeys, revokedKeyIds)
        require(verification.valid) { verification.message }
        val root = destination.toAbsolutePath().normalize()
        Files.createDirectories(root)
        readEntries(file).filterKeys { it != MANIFEST_PATH }.forEach { (relative, bytes) ->
            val target = root.resolve(relative).normalize()
            require(target.startsWith(root)) { "Unsicherer Wiederherstellungspfad" }
            target.parent?.let(Files::createDirectories)
            Files.write(target, bytes)
        }
    }

    private fun validateMetadata(metadata: EvidenceBackupMetadata) {
        require(metadata.backupId.isNotBlank() && metadata.tenantId.isNotBlank() && metadata.sourceDeviceId.isNotBlank()) { "Backupidentität fehlt" }
        require(metadata.roomSchemaVersion > 0 && metadata.packageFormatVersion > 0) { "Backupversion ist ungültig" }
        require(metadata.auditHeadHash.matches(Regex("^[a-f0-9]{64}$"))) { "Audit-Head-Hash ist ungültig" }
    }

    private fun readEntries(file: Path): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(Files.newInputStream(file)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                require(safeZipPath(name)) { "Unsicherer ZIP-Pfad: $name" }
                require(name !in entries) { "Doppelter ZIP-Eintrag: $name" }
                entries[name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun unsignedPayload(metadata: EvidenceBackupMetadata, createdAt: String, keyId: String, files: List<BackupFile>): String = listOf(
        EvidenceBackupManifest.CONTRACT, metadata.backupId, createdAt, metadata.tenantId, metadata.sourceDeviceId,
        metadata.roomSchemaVersion, metadata.coreVersion, metadata.moduleId, metadata.moduleVersion, metadata.profileId,
        metadata.packageFormatVersion, metadata.auditHeadHash, keyId,
        files.joinToString("|") { "${it.path}:${it.sha256}:${it.size}" },
    ).joinToString("\n")

    private fun safeLogicalPath(path: String): Boolean = path.startsWith("data/") && path.endsWith(".json") && safeZipPath(path)
    private fun safeZipPath(path: String): Boolean = !path.startsWith('/') && ".." !in path.split('/')
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val MANIFEST_PATH = "META-INF/av-evidence-backup.json"
        val REQUIRED_FILES = setOf(
            "data/shifts.json", "data/work_items.json", "data/activities.json", "data/corrections.json",
            "data/audit_events.json", "data/configuration_state.json",
        )
    }
}
