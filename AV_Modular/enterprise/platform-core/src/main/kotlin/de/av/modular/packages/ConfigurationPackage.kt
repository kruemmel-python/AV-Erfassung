package de.av.modular.packages

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
data class PackageFile(
    val path: String,
    @SerialName("sha256") val sha256: String,
    val size: Long,
)

@Serializable
data class ConfigurationPackageManifest(
    val contract: String = CONTRACT,
    @SerialName("package_id") val packageId: String,
    @SerialName("package_version") val packageVersion: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("key_id") val keyId: String,
    val algorithm: String = "Ed25519",
    val files: List<PackageFile>,
    @SerialName("signature_base64") val signatureBase64: String,
) {
    companion object { const val CONTRACT = "avm-package-1.0" }
}

data class PackageVerification(
    val valid: Boolean,
    val message: String,
    val manifest: ConfigurationPackageManifest? = null,
    val error: AvmError? = null,
)

class ConfigurationPackageService(
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true },
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(
        sourceDirectory: Path,
        output: Path,
        packageId: String,
        packageVersion: String,
        keyId: String,
        privateKey: PrivateKey,
    ): ConfigurationPackageManifest {
        val root = sourceDirectory.toAbsolutePath().normalize()
        require(Files.isDirectory(root)) { "Paketquelle fehlt: $root" }
        val sourceFiles = Files.walk(root).use { stream ->
            stream.filter(Files::isRegularFile).sorted().toList()
        }
        require(sourceFiles.isNotEmpty()) { "Leeres Paket ist nicht zulässig" }
        val files = sourceFiles.map { path ->
            val relative = root.relativize(path).toString().replace('\\', '/')
            require(!relative.startsWith("META-INF/")) { "META-INF ist reserviert" }
            val bytes = Files.readAllBytes(path)
            PackageFile(relative, sha256(bytes), bytes.size.toLong())
        }
        val createdAt = Instant.now(clock).toString()
        val unsigned = unsignedPayload(packageId, packageVersion, createdAt, keyId, files)
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(unsigned.toByteArray(Charsets.UTF_8))
        val manifest = ConfigurationPackageManifest(
            packageId = packageId,
            packageVersion = packageVersion,
            createdAt = createdAt,
            keyId = keyId,
            files = files,
            signatureBase64 = Base64.getEncoder().encodeToString(signer.sign()),
        )
        output.toAbsolutePath().parent?.let(Files::createDirectories)
        ZipOutputStream(Files.newOutputStream(output)).use { zip ->
            sourceFiles.zip(files).forEach { (path, descriptor) ->
                zip.putNextEntry(ZipEntry(descriptor.path))
                Files.copy(path, zip)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(MANIFEST_PATH))
            zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return manifest
    }

    fun verify(packageFile: Path, trustedKeys: Map<String, PublicKey>, revokedKeyIds: Set<String> = emptySet()): PackageVerification {
        val entries = runCatching { readEntries(packageFile) }
            .getOrElse { return PackageVerification(false, it.message ?: "Paket nicht lesbar", error = AvmError.PACKAGE_PATH_UNSAFE) }
        val manifestBytes = entries[MANIFEST_PATH]
            ?: return PackageVerification(false, "Paketmanifest fehlt", error = AvmError.PACKAGE_SCHEMA_INVALID)
        val manifest = runCatching { json.decodeFromString<ConfigurationPackageManifest>(manifestBytes.toString(Charsets.UTF_8)) }
            .getOrElse { return PackageVerification(false, "Paketmanifest ungültig: ${it.message}", error = AvmError.PACKAGE_SCHEMA_INVALID) }
        if (manifest.contract != ConfigurationPackageManifest.CONTRACT) return PackageVerification(false, "Paketvertrag nicht unterstützt", manifest, AvmError.CONTRACT_VERSION_UNSUPPORTED)
        if (manifest.keyId in revokedKeyIds) return PackageVerification(false, "Signaturschlüssel ist gesperrt", manifest, AvmError.PACKAGE_KEY_REVOKED)
        val key = trustedKeys[manifest.keyId]
            ?: return PackageVerification(false, "Signaturschlüssel ist nicht vertrauenswürdig", manifest, AvmError.PACKAGE_SIGNATURE_INVALID)
        if (manifest.algorithm != "Ed25519") return PackageVerification(false, "Nicht unterstützter Signaturalgorithmus", manifest, AvmError.PACKAGE_SIGNATURE_INVALID)
        if (entries.keys.any { it != MANIFEST_PATH && it !in manifest.files.map(PackageFile::path) }) {
            return PackageVerification(false, "Paket enthält nicht deklarierte Dateien", manifest, AvmError.PACKAGE_SCHEMA_INVALID)
        }
        manifest.files.forEach { descriptor ->
            val bytes = entries[descriptor.path]
                ?: return PackageVerification(false, "Datei fehlt: ${descriptor.path}", manifest, AvmError.PACKAGE_SCHEMA_INVALID)
            if (bytes.size.toLong() != descriptor.size || sha256(bytes) != descriptor.sha256) {
                return PackageVerification(false, "Prüfsumme ungültig: ${descriptor.path}", manifest, AvmError.PACKAGE_SIGNATURE_INVALID)
            }
        }
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(key)
        verifier.update(unsignedPayload(manifest.packageId, manifest.packageVersion, manifest.createdAt, manifest.keyId, manifest.files).toByteArray(Charsets.UTF_8))
        val signature = runCatching { Base64.getDecoder().decode(manifest.signatureBase64) }
            .getOrElse { return PackageVerification(false, "Signatur ist kein gültiges Base64", manifest, AvmError.PACKAGE_SIGNATURE_INVALID) }
        return if (verifier.verify(signature)) PackageVerification(true, "Paket und Signatur gültig", manifest)
        else PackageVerification(false, "Paketsignatur ungültig", manifest, AvmError.PACKAGE_SIGNATURE_INVALID)
    }

    fun extractVerified(packageFile: Path, destination: Path, trustedKeys: Map<String, PublicKey>, revokedKeyIds: Set<String> = emptySet()) {
        val verification = verify(packageFile, trustedKeys, revokedKeyIds)
        require(verification.valid) { verification.message }
        val root = destination.toAbsolutePath().normalize()
        Files.createDirectories(root)
        readEntries(packageFile).filterKeys { it != MANIFEST_PATH }.forEach { (relative, bytes) ->
            val target = root.resolve(relative).normalize()
            require(target.startsWith(root)) { "Unsicherer Paketpfad: $relative" }
            target.parent?.let(Files::createDirectories)
            Files.write(target, bytes)
        }
    }

    private fun readEntries(packageFile: Path): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(Files.newInputStream(packageFile)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/')
                require(!name.startsWith("/") && ".." !in name.split('/')) { "Unsicherer ZIP-Pfad: $name" }
                require(name !in result) { "Doppelter ZIP-Eintrag: $name" }
                result[name] = zip.readBytes()
            }
        }
        return result
    }

    private fun unsignedPayload(packageId: String, version: String, createdAt: String, keyId: String, files: List<PackageFile>): String =
        listOf(ConfigurationPackageManifest.CONTRACT, packageId, version, createdAt, keyId, files.joinToString("|") { "${it.path}:${it.sha256}:${it.size}" }).joinToString("\n")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object { const val MANIFEST_PATH = "META-INF/av-package.json" }
}
