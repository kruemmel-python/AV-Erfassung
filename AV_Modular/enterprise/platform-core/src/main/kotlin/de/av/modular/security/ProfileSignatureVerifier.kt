package de.av.modular.security

import de.av.modular.model.SignedCustomerProfile
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SignatureVerification(val accepted: Boolean, val message: String)

fun interface ProfileSignatureVerifier {
    fun verify(profile: SignedCustomerProfile): SignatureVerification
}

object DevelopmentProfileVerifier : ProfileSignatureVerifier {
    override fun verify(profile: SignedCustomerProfile): SignatureVerification =
        if (profile.profile.environment == "development") {
            SignatureVerification(true, "Unsigniertes Entwicklungsprofil zugelassen")
        } else {
            SignatureVerification(false, "Produktionsprofile benötigen eine Ed25519-Signatur")
        }
}

class Ed25519ProfileVerifier(
    private val publicKeys: Map<String, PublicKey>,
    private val json: Json = Json { encodeDefaults = true; explicitNulls = false },
) : ProfileSignatureVerifier {
    override fun verify(profile: SignedCustomerProfile): SignatureVerification {
        val block = profile.signature ?: return SignatureVerification(false, "Signatur fehlt")
        if (block.algorithm != "Ed25519") return SignatureVerification(false, "Nicht unterstützter Algorithmus")
        val key = publicKeys[block.keyId] ?: return SignatureVerification(false, "Unbekannte Schlüssel-ID")
        val payload = json.encodeToString(profile.profile).toByteArray(Charsets.UTF_8)
        val signatureBytes = runCatching { Base64.getDecoder().decode(block.valueBase64) }
            .getOrElse { return SignatureVerification(false, "Signatur ist kein gültiges Base64") }
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(key)
        verifier.update(payload)
        return if (verifier.verify(signatureBytes)) {
            SignatureVerification(true, "Signatur gültig")
        } else {
            SignatureVerification(false, "Signatur ungültig")
        }
    }
}
