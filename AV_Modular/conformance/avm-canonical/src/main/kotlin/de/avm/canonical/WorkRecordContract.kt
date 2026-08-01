package de.avm.canonical

import java.math.BigDecimal
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class WorkRecordDigestInput(
    val tenantId: String,
    val moduleId: String,
    val schemaVersion: Int,
    val recordId: String,
    val shiftId: String,
    val employeeId: String,
    val processType: String,
    val startTimestamp: String,
    val endTimestamp: String?,
    val status: String,
    val customData: String,
    val manuallyModified: Boolean,
    val deletedForAudit: Boolean,
    val netDurationSeconds: Double,
    val targetDurationSeconds: Double,
)

object WorkRecordContract {
    const val VERSION_V2 = "av-work-record-v2"

    /** Revision and export metadata are excluded: the digest identifies the semantic record payload. */
    fun payloadDigest(input: WorkRecordDigestInput): String {
        val canonical = listOf(
            CanonicalEncoding.text(input.tenantId), CanonicalEncoding.text(input.moduleId), input.schemaVersion.toString(), CanonicalEncoding.text(input.recordId),
            CanonicalEncoding.text(input.shiftId), CanonicalEncoding.text(input.employeeId), CanonicalEncoding.text(input.processType), CanonicalEncoding.timestamp(input.startTimestamp),
            input.endTimestamp?.let(CanonicalEncoding::timestamp).orEmpty(), CanonicalEncoding.text(input.status), CanonicalEncoding.json(input.customData),
            input.manuallyModified.toString(), input.deletedForAudit.toString(),
            canonicalNumber(input.netDurationSeconds), canonicalNumber(input.targetDurationSeconds),
        ).joinToString("\u001f")
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun canonicalNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

object CanonicalEncoding {
    private val json = Json { explicitNulls = true }

    fun utf8(value: String): ByteArray = text(value).toByteArray(Charsets.UTF_8)

    fun text(value: String): String = Normalizer.normalize(
        value.replace("\r\n", "\n").replace('\r', '\n'),
        Normalizer.Form.NFC,
    )

    fun timestamp(value: String): String = Instant.parse(value).toString()

    fun json(value: String): String = json.encodeToString(JsonElement.serializer(), normalize(json.parseToJsonElement(value)))

    private fun normalize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { text(it.key) to normalize(it.value) })
        is JsonArray -> JsonArray(element.map(::normalize))
        JsonNull -> JsonNull
        is JsonPrimitive -> when {
            element.isString -> JsonPrimitive(text(element.content))
            element.booleanOrNull != null -> JsonPrimitive(element.booleanOrNull!!)
            element.longOrNull != null -> JsonPrimitive(element.longOrNull!!)
            element.doubleOrNull != null -> JsonPrimitive(BigDecimal(element.content).stripTrailingZeros())
            else -> element.jsonPrimitive
        }
    }
}
