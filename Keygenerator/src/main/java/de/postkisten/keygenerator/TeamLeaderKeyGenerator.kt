package de.postkisten.keygenerator

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class GeneratedTeamLeaderKey(
    val key: String,
    val createdAtUtc: Long,
    val expiresAtUtc: Long,
)

object TeamLeaderKeyGenerator {
    const val VALIDITY_MILLIS = 9L * 60L * 60L * 1_000L
    private const val PREFIX = "TL1"
    private const val SECRET_BASE64 = "qgNyfcOabgYHkbUoKnQjyFSuGQJww3V71Rux1KQwqlE="
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generate(nowUtc: Long = System.currentTimeMillis()): GeneratedTeamLeaderKey {
        val issuedSecond = nowUtc / 1_000L
        val issuedAt = issuedSecond * 1_000L
        val secret = Base64.getDecoder().decode(SECRET_BASE64)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val digest = mac.doFinal("$PREFIX:$issuedSecond".toByteArray(StandardCharsets.UTF_8))
        val signature = base32(digest).take(12)
        val key = "$PREFIX-${issuedSecond.toString(36).uppercase(Locale.ROOT)}-$signature"
        return GeneratedTeamLeaderKey(key, issuedAt, issuedAt + VALIDITY_MILLIS)
    }

    private fun base32(bytes: ByteArray): String {
        val output = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                output.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) output.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return output.toString()
    }
}
