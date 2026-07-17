package de.postkisten.tracker.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TeamLeaderKeyValidation(
    val valid: Boolean,
    val expiresAtUtc: Long? = null,
    val message: String,
)

object TeamLeaderKey {
    const val VALIDITY_MILLIS = 9L * 60L * 60L * 1_000L
    private const val CLOCK_TOLERANCE_MILLIS = 5L * 60L * 1_000L
    private const val PREFIX = "TL1"
    private const val SECRET_BASE64 = "qgNyfcOabgYHkbUoKnQjyFSuGQJww3V71Rux1KQwqlE="
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun validate(rawKey: String, nowUtc: Long = System.currentTimeMillis()): TeamLeaderKeyValidation {
        val normalized = rawKey.trim().uppercase(Locale.ROOT).replace(" ", "")
        val parts = normalized.split('-')
        if (parts.size != 3 || parts[0] != PREFIX || parts[2].length != 12) {
            return TeamLeaderKeyValidation(false, message = "Ungültiges Schlüsselformat.")
        }
        val issuedSecond = parts[1].toLongOrNull(36)
            ?: return TeamLeaderKeyValidation(false, message = "Ungültiger Schlüssel.")
        val expected = signature(issuedSecond)
        if (!MessageDigest.isEqual(expected.toByteArray(), parts[2].toByteArray())) {
            return TeamLeaderKeyValidation(false, message = "Schlüssel ist nicht gültig.")
        }
        val issuedAt = issuedSecond * 1_000L
        val expiresAt = issuedAt + VALIDITY_MILLIS
        if (nowUtc < issuedAt - CLOCK_TOLERANCE_MILLIS) {
            return TeamLeaderKeyValidation(false, expiresAt, "Schlüssel ist noch nicht gültig. Gerätezeit prüfen.")
        }
        if (nowUtc >= expiresAt) {
            return TeamLeaderKeyValidation(false, expiresAt, "Schlüssel ist abgelaufen.")
        }
        return TeamLeaderKeyValidation(true, expiresAt, "Teamleiter-Modus freigeschaltet.")
    }

    private fun signature(issuedSecond: Long): String {
        val secret = java.util.Base64.getDecoder().decode(SECRET_BASE64)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val digest = mac.doFinal("$PREFIX:$issuedSecond".toByteArray(StandardCharsets.UTF_8))
        return base32(digest).take(12)
    }

    private fun base32(bytes: ByteArray): String {
        val output = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                output.append(alphabet[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) output.append(alphabet[(buffer shl (5 - bits)) and 31])
        return output.toString()
    }
}
