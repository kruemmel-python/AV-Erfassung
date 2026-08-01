package de.av.modular.audit

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

data class AuditEntry(
    val sequence: Long,
    val tenantId: String,
    val eventType: String,
    val actorId: String,
    val subjectId: String,
    val occurredAt: String,
    val payloadDigest: String,
    val previousHash: String,
    val entryHash: String,
)

data class AuditVerification(val valid: Boolean, val failingSequence: Long? = null, val message: String)

class AuditLedger(private val clock: Clock = Clock.systemUTC()) {
    private val entries = mutableListOf<AuditEntry>()

    @Synchronized
    fun append(
        tenantId: String,
        eventType: String,
        actorId: String,
        subjectId: String,
        payload: ByteArray,
    ): AuditEntry {
        require(tenantId.isNotBlank() && eventType.isNotBlank() && actorId.isNotBlank() && subjectId.isNotBlank())
        val sequence = entries.size.toLong() + 1
        val occurredAt = Instant.now(clock).toString()
        val payloadDigest = sha256(payload)
        val previousHash = entries.lastOrNull()?.entryHash ?: GENESIS_HASH
        val entryHash = calculate(sequence, tenantId, eventType, actorId, subjectId, occurredAt, payloadDigest, previousHash)
        return AuditEntry(sequence, tenantId, eventType, actorId, subjectId, occurredAt, payloadDigest, previousHash, entryHash)
            .also(entries::add)
    }

    @Synchronized
    fun snapshot(tenantId: String): List<AuditEntry> = entries.filter { it.tenantId == tenantId }.toList()

    @Synchronized
    fun verify(): AuditVerification {
        var previous = GENESIS_HASH
        entries.forEachIndexed { index, entry ->
            if (entry.sequence != index.toLong() + 1 || entry.previousHash != previous) {
                return AuditVerification(false, entry.sequence, "Sequenz oder Vorgänger-Hash ungültig")
            }
            val expected = calculate(
                entry.sequence,
                entry.tenantId,
                entry.eventType,
                entry.actorId,
                entry.subjectId,
                entry.occurredAt,
                entry.payloadDigest,
                entry.previousHash,
            )
            if (expected != entry.entryHash) return AuditVerification(false, entry.sequence, "Eintrags-Hash ungültig")
            previous = entry.entryHash
        }
        return AuditVerification(true, message = "Audit-Kette gültig (${entries.size} Einträge)")
    }

    private fun calculate(vararg values: Any): String = sha256(values.joinToString("\u001f").toByteArray(Charsets.UTF_8))
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    companion object {
        const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
