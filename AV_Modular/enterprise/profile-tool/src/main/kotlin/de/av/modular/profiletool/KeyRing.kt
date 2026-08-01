package de.av.modular.profiletool

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TrustedKey(
    @SerialName("key_id") val keyId: String,
    @SerialName("public_key_base64") val publicKeyBase64: String,
    val status: String = "active",
    @SerialName("created_at") val createdAt: String,
    @SerialName("revoked_at") val revokedAt: String? = null,
    @SerialName("revocation_reason") val revocationReason: String? = null,
)

@Serializable
data class KeyRing(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val keys: List<TrustedKey>,
) {
    fun activePublicKeys(): Map<String, PublicKey> = keys.filter { it.status == "active" }.associate { key ->
        key.keyId to KeyFactory.getInstance("Ed25519").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKeyBase64)),
        )
    }

    fun revokedIds(): Set<String> = keys.filter { it.status == "revoked" }.mapTo(mutableSetOf(), TrustedKey::keyId)
}

class KeyRingStore(private val json: Json = Json { prettyPrint = true; encodeDefaults = true }) {
    fun load(path: Path): KeyRing = json.decodeFromString(Files.readString(path))
    fun save(path: Path, keyRing: KeyRing) {
        path.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.writeString(path, json.encodeToString(keyRing))
    }

    fun add(path: Path, key: TrustedKey) {
        val current = if (Files.exists(path)) load(path) else KeyRing(keys = emptyList())
        require(current.keys.none { it.keyId == key.keyId }) { "Schlüssel-ID existiert bereits" }
        save(path, current.copy(keys = current.keys + key))
    }

    fun revoke(path: Path, keyId: String, reason: String, now: Instant = Instant.now()) {
        val current = load(path)
        require(current.keys.any { it.keyId == keyId }) { "Schlüssel nicht gefunden: $keyId" }
        save(path, current.copy(keys = current.keys.map {
            if (it.keyId == keyId) it.copy(status = "revoked", revokedAt = now.toString(), revocationReason = reason) else it
        }))
    }
}
