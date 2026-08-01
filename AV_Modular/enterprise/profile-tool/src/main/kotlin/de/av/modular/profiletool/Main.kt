package de.av.modular.profiletool

import de.av.modular.packages.ConfigurationPackageService
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: return usage()
    val options = Options(args.drop(1))
    when (command) {
        "keygen" -> keygen(options)
        "package" -> createPackage(options)
        "verify" -> verifyPackage(options)
        "revoke" -> revoke(options)
        else -> usage()
    }
}

private fun keygen(options: Options) {
    val keyId = options.required("--key-id")
    require(keyId.matches(Regex("^[A-Za-z0-9._-]{3,80}$"))) { "Ungültige Schlüssel-ID" }
    val output = Path.of(options.required("--out"))
    val keyRingPath = Path.of(options.required("--keyring"))
    Files.createDirectories(output)
    val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val privatePath = output.resolve("$keyId-private.pem")
    val publicPath = output.resolve("$keyId-public.pem")
    Files.writeString(privatePath, pem("PRIVATE KEY", pair.private.encoded))
    Files.writeString(publicPath, pem("PUBLIC KEY", pair.public.encoded))
    KeyRingStore().add(
        keyRingPath,
        TrustedKey(keyId, Base64.getEncoder().encodeToString(pair.public.encoded), createdAt = Instant.now().toString()),
    )
    println("Schlüsselpaar erzeugt: $privatePath")
    println("Vertrauensspeicher aktualisiert: $keyRingPath")
    println("WICHTIG: Private Schlüssel in einem HSM oder geschützten Secret Store aufbewahren.")
}

private fun createPackage(options: Options) {
    val source = Path.of(options.required("--source"))
    val output = Path.of(options.required("--out"))
    val privateKey = readPrivateKey(Path.of(options.required("--private-key")))
    val manifest = ConfigurationPackageService().create(
        sourceDirectory = source,
        output = output,
        packageId = options.required("--package-id"),
        packageVersion = options.required("--version"),
        keyId = options.required("--key-id"),
        privateKey = privateKey,
    )
    println("Paket erstellt: ${output.toAbsolutePath()}")
    println("Dateien: ${manifest.files.size}; Schlüssel: ${manifest.keyId}")
}

private fun verifyPackage(options: Options) {
    val packageFile = Path.of(options.required("--package"))
    val keyRing = KeyRingStore().load(Path.of(options.required("--keyring")))
    val result = ConfigurationPackageService().verify(packageFile, keyRing.activePublicKeys(), keyRing.revokedIds())
    println(result.message)
    if (!result.valid) throw IllegalStateException("Paketprüfung fehlgeschlagen")
}

private fun revoke(options: Options) {
    val path = Path.of(options.required("--keyring"))
    KeyRingStore().revoke(path, options.required("--key-id"), options.required("--reason"))
    println("Schlüssel wurde gesperrt und darf keine neuen Pakete mehr autorisieren.")
}

private fun readPrivateKey(path: Path): PrivateKey {
    val base64 = Files.readString(path)
        .lineSequence().filterNot { it.startsWith("---") }.joinToString("")
    return KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))
}

private fun pem(type: String, bytes: ByteArray): String = buildString {
    appendLine("-----BEGIN $type-----")
    Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes).lineSequence().forEach(::appendLine)
    appendLine("-----END $type-----")
}

private class Options(values: List<String>) {
    private val values = values
    fun required(name: String): String {
        val index = values.indexOf(name)
        require(index >= 0 && index + 1 < values.size) { "Parameter fehlt: $name" }
        return values[index + 1]
    }
}

private fun usage() {
    println(
        """
        AV Profile Tool
          keygen --key-id <id> --out <ordner> --keyring <trust.json>
          package --source <ordner> --out <paket.avpkg> --package-id <id> --version <x.y.z> --key-id <id> --private-key <pem>
          verify --package <paket.avpkg> --keyring <trust.json>
          revoke --keyring <trust.json> --key-id <id> --reason <text>
        """.trimIndent(),
    )
}
