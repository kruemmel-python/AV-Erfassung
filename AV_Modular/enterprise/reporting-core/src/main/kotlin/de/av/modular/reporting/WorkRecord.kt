package de.av.modular.reporting

import de.avm.canonical.WorkRecordContract
import de.avm.canonical.WorkRecordDigestInput
import de.avm.errors.AvmError
import java.security.MessageDigest

data class WorkRecord(
    val contractVersion: String,
    val recordId: String,
    val revisionNumber: Long,
    val sourceDeviceId: String,
    val exportId: String,
    val payloadDigest: String,
    val tenantId: String,
    val moduleId: String,
    val schemaVersion: Int,
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
    val shiftDate: String,
    val correctionType: String = if (deletedForAudit) "deleted" else if (manuallyModified) "modified" else "none",
)

data class ImportConflict(
    val recordId: String,
    val revisionNumber: Long,
    val firstExportId: String,
    val conflictingExportId: String,
    val firstDigest: String,
    val conflictingDigest: String,
    val message: String,
    val error: AvmError = AvmError.SAME_REVISION_DIGEST_CONFLICT,
)

data class ImportIssue(
    val source: String,
    val row: Int?,
    val error: AvmError,
    val message: String,
)

data class ImportBatch(
    val records: List<WorkRecord>,
    val warnings: List<String>,
    val issues: List<ImportIssue>,
    val conflicts: List<ImportConflict>,
    val sourceCount: Int,
    val duplicateCount: Int,
    val supersededRevisionCount: Int,
)

class WorkRecordCsvImporter {
    fun import(files: List<Pair<String, String>>): ImportBatch {
        val warnings = mutableListOf<String>()
        val conflicts = mutableListOf<ImportConflict>()
        val issues = mutableListOf<ImportIssue>()
        val accepted = linkedMapOf<String, WorkRecord>()
        val quarantinedRevision = mutableMapOf<String, Long>()
        var duplicates = 0
        var superseded = 0

        files.forEach { (source, content) ->
            val lines = content.lineSequence().filter(String::isNotBlank).toList()
            if (lines.isEmpty()) {
                warnings += "$source: Datei ist leer"
                issues += ImportIssue(source, null, AvmError.REQUIRED_FIELD_MISSING, "Datei ist leer")
                return@forEach
            }
            val header = split(lines.first()).mapIndexed { index, value -> value.removePrefix("\uFEFF") to index }.toMap()
            if ("contract_version" !in header) {
                warnings += "$source: kein AV-Modular-CSV-Vertrag"
                issues += ImportIssue(source, 1, AvmError.CONTRACT_VERSION_UNSUPPORTED, "Kein AV-Modular-CSV-Vertrag")
                return@forEach
            }
            fun value(row: List<String>, name: String): String = header[name]?.let(row::getOrNull).orEmpty()
            lines.drop(1).forEachIndexed { rowIndex, line ->
                runCatching {
                    val row = split(line)
                    val contract = value(row, "contract_version")
                    requireContract(
                        contract == CONTRACT_V2 || contract == CONTRACT_V1,
                        AvmError.CONTRACT_VERSION_UNSUPPORTED,
                        "Vertrag nicht unterstützt: $contract",
                    )
                    val start = value(row, "start_timestamp")
                    val end = value(row, "end_timestamp").ifBlank { null }
                    val net = value(row, "net_duration_seconds").toDoubleOrNull() ?: durationSeconds(start, end)
                    val target = value(row, "target_duration_seconds").toDoubleOrNull() ?: 0.0
                    val base = WorkRecord(
                        contractVersion = contract,
                        recordId = value(row, "record_id"),
                        revisionNumber = value(row, "revision_number").toLongOrNull() ?: 1L,
                        sourceDeviceId = value(row, "source_device_id"),
                        exportId = value(row, "export_id"),
                        payloadDigest = value(row, "payload_digest").lowercase(),
                        tenantId = value(row, "tenant_id"),
                        moduleId = value(row, "module_id"),
                        schemaVersion = value(row, "schema_version").toInt(),
                        shiftId = value(row, "shift_id"),
                        employeeId = value(row, "employee_id"),
                        processType = value(row, "process_type"),
                        startTimestamp = start,
                        endTimestamp = end,
                        status = value(row, "status"),
                        customData = value(row, "custom_data"),
                        manuallyModified = value(row, "manually_modified").toBooleanStrictOrNull() ?: false,
                        deletedForAudit = value(row, "deleted_for_audit").toBooleanStrictOrNull() ?: false,
                        netDurationSeconds = net,
                        targetDurationSeconds = target,
                        shiftDate = start.take(10),
                    )
                    requireContract(
                        base.tenantId.isNotBlank() && base.moduleId.isNotBlank() && base.shiftId.isNotBlank(),
                        AvmError.REQUIRED_FIELD_MISSING,
                        "Pflichtfeld fehlt",
                    )
                    val record = if (contract == CONTRACT_V2) validateV2(base) else upgradeLegacy(base, source, warnings)
                    merge(record, accepted, quarantinedRevision, conflicts).also { outcome ->
                        duplicates += if (outcome == MergeOutcome.DUPLICATE) 1 else 0
                        superseded += if (outcome == MergeOutcome.SUPERSEDED) 1 else 0
                    }
                }.onFailure { cause ->
                    val message = cause.message.orEmpty()
                    val error = (cause as? WorkRecordImportException)?.error ?: AvmError.REQUIRED_FIELD_MISSING
                    warnings += "$source, Zeile ${rowIndex + 2}: $message"
                    issues += ImportIssue(source, rowIndex + 2, error, message)
                }
            }
        }
        return ImportBatch(accepted.values.toList(), warnings, issues, conflicts, files.size, duplicates, superseded)
    }

    private fun validateV2(record: WorkRecord): WorkRecord {
        requireContract(record.recordId.matches(ID_PATTERN), AvmError.REQUIRED_FIELD_MISSING, "record_id fehlt oder ist ungültig")
        requireContract(record.revisionNumber > 0, AvmError.REVISION_CHAIN_BROKEN, "revision_number muss positiv sein")
        requireContract(record.sourceDeviceId.matches(ID_PATTERN), AvmError.REQUIRED_FIELD_MISSING, "source_device_id fehlt oder ist ungültig")
        requireContract(record.exportId.matches(ID_PATTERN), AvmError.REQUIRED_FIELD_MISSING, "export_id fehlt oder ist ungültig")
        requireContract(record.payloadDigest.matches(DIGEST_PATTERN), AvmError.PAYLOAD_DIGEST_INVALID, "payload_digest fehlt oder ist ungültig")
        val calculated = WorkRecordDigest.compute(record)
        requireContract(calculated == record.payloadDigest, AvmError.PAYLOAD_DIGEST_INVALID, "payload_digest stimmt nicht mit dem Datensatz überein")
        return record
    }

    private fun upgradeLegacy(record: WorkRecord, source: String, warnings: MutableList<String>): WorkRecord {
        val digest = WorkRecordDigest.compute(record)
        warnings += "$source: $CONTRACT_V1 ist ein Legacy-Vertrag ohne Revisionsidentität; Datensatz wurde deterministisch migriert"
        return record.copy(
            recordId = "legacy-${digest.take(32)}",
            revisionNumber = 1,
            sourceDeviceId = "legacy-import",
            exportId = "legacy-${sha256(source).take(32)}",
            payloadDigest = digest,
        )
    }

    private fun merge(
        incoming: WorkRecord,
        accepted: MutableMap<String, WorkRecord>,
        quarantinedRevision: MutableMap<String, Long>,
        conflicts: MutableList<ImportConflict>,
    ): MergeOutcome {
        val key = "${incoming.tenantId}\u001f${incoming.moduleId}\u001f${incoming.recordId}"
        val quarantine = quarantinedRevision[key]
        if (quarantine != null && incoming.revisionNumber <= quarantine) return MergeOutcome.QUARANTINED
        if (quarantine != null) quarantinedRevision.remove(key)
        val existing = accepted[key]
        if (existing == null) {
            accepted[key] = incoming
            return MergeOutcome.ACCEPTED
        }
        if (existing.payloadDigest == incoming.payloadDigest) return MergeOutcome.DUPLICATE
        return when {
            incoming.revisionNumber > existing.revisionNumber -> {
                accepted[key] = incoming
                MergeOutcome.SUPERSEDED
            }
            incoming.revisionNumber < existing.revisionNumber -> MergeOutcome.OLDER
            else -> {
                accepted.remove(key)
                quarantinedRevision[key] = incoming.revisionNumber
                conflicts += ImportConflict(
                    recordId = incoming.recordId,
                    revisionNumber = incoming.revisionNumber,
                    firstExportId = existing.exportId,
                    conflictingExportId = incoming.exportId,
                    firstDigest = existing.payloadDigest,
                    conflictingDigest = incoming.payloadDigest,
                    message = "Gleiche record_id und Revision besitzen unterschiedliche Payload-Digests",
                )
                MergeOutcome.CONFLICT
            }
        }
    }

    private fun durationSeconds(start: String, end: String?): Double {
        if (end == null) return 0.0
        return java.time.Duration.between(java.time.Instant.parse(start), java.time.Instant.parse(end)).seconds.toDouble().coerceAtLeast(0.0)
    }

    private fun split(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when {
                line[index] == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> { current.append('"'); index++ }
                line[index] == '"' -> quoted = !quoted
                line[index] == ';' && !quoted -> { values += current.toString(); current.clear() }
                else -> current.append(line[index])
            }
            index++
        }
        require(!quoted) { "Nicht abgeschlossenes CSV-Anführungszeichen" }
        values += current.toString()
        return values
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private enum class MergeOutcome { ACCEPTED, DUPLICATE, SUPERSEDED, OLDER, CONFLICT, QUARANTINED }

    private fun requireContract(condition: Boolean, error: AvmError, message: String) {
        if (!condition) throw WorkRecordImportException(error, message)
    }

    companion object {
        const val CONTRACT_V1 = "av-work-record-v1"
        const val CONTRACT_V2 = "av-work-record-v2"
        private val ID_PATTERN = Regex("^[A-Za-z0-9._:-]{3,128}$")
        private val DIGEST_PATTERN = Regex("^[a-f0-9]{64}$")
    }
}

class WorkRecordImportException(
    val error: AvmError,
    message: String,
) : IllegalArgumentException(message)

object WorkRecordDigest {
    fun compute(record: WorkRecord): String = WorkRecordContract.payloadDigest(
        WorkRecordDigestInput(
            tenantId = record.tenantId, moduleId = record.moduleId, schemaVersion = record.schemaVersion,
            recordId = record.recordId, shiftId = record.shiftId, employeeId = record.employeeId,
            processType = record.processType, startTimestamp = record.startTimestamp, endTimestamp = record.endTimestamp,
            status = record.status, customData = record.customData, manuallyModified = record.manuallyModified,
            deletedForAudit = record.deletedForAudit, netDurationSeconds = record.netDurationSeconds,
            targetDurationSeconds = record.targetDurationSeconds,
        ),
    )
}
