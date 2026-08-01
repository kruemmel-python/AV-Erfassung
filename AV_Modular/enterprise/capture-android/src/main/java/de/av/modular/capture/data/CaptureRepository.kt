package de.av.modular.capture.data

import androidx.room.withTransaction
import de.av.modular.audit.AuditLedger
import de.avm.canonical.WorkRecordContract
import de.avm.canonical.WorkRecordDigestInput
import de.av.modular.runtime.PlatformRuntime
import de.av.modular.security.AccessRequest
import de.av.modular.security.AuthorizationService
import de.av.modular.security.Principal
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class CaptureRepository(
    private val database: CaptureDatabase,
    val runtime: PlatformRuntime,
    private val clock: Clock = Clock.systemUTC(),
    private val defaultLocationId: String = "default",
    private val sourceDeviceId: String = "unmanaged_device",
) {
    private val dao = database.dao()
    val activeShift: Flow<CaptureShiftEntity?> = dao.observeActiveShift()
    val activeWorkItem: Flow<CaptureWorkItemEntity?> = dao.observeActiveWorkItem()

    suspend fun startShift(employeeId: String, shiftType: String, locationId: String = defaultLocationId) {
        require(employeeId.matches(Regex("^[0-9]+$"))) { "Personalnummer darf nur Ziffern enthalten" }
        require(runtime.module.source.processes.shiftTypes.any { it.id == shiftType }) { "Unbekannte Schichtart" }
        val now = now()
        val id = "SHIFT-${UUID.randomUUID()}"
        database.withTransaction {
            dao.insertShift(
                CaptureShiftEntity(
                    id = id,
                    tenantId = runtime.profile.profile.tenantId,
                    locationId = locationId,
                    moduleId = runtime.module.source.manifest.moduleId,
                    shiftType = shiftType,
                    employeeId = employeeId,
                    startedAtUtc = now,
                ),
            )
            audit("shift.started", employeeId, id, "{\"shift_type\":\"$shiftType\"}")
        }
    }

    suspend fun finishShift(shift: CaptureShiftEntity) {
        database.withTransaction {
            check(dao.finishShift(shift.id, now()) == 1) { "Schicht ist nicht mehr aktiv" }
            audit("shift.completed", shift.employeeId, shift.id, "{}")
        }
    }

    suspend fun startWorkItem(shift: CaptureShiftEntity, processType: String, customDataJson: String = "{}") {
        require(runtime.module.workItems.containsKey(processType)) { "Unbekannter Vorgang" }
        val id = "WI-${UUID.randomUUID()}"
        database.withTransaction {
            dao.insertWorkItem(
                CaptureWorkItemEntity(
                    id = id,
                    tenantId = shift.tenantId,
                    moduleId = shift.moduleId,
                    shiftId = shift.id,
                    processType = processType,
                    employeeId = shift.employeeId,
                    startedAtUtc = now(),
                    customDataJson = customDataJson,
                ),
            )
            audit("work_item.started", shift.employeeId, id, "{\"process_type\":\"$processType\"}")
        }
    }

    suspend fun finishWorkItem(item: CaptureWorkItemEntity) {
        database.withTransaction {
            check(dao.finishWorkItem(item.id, now()) == 1) { "Vorgang ist nicht mehr aktiv" }
            audit("work_item.completed", item.employeeId, item.id, "{\"process_type\":\"${item.processType}\"}")
        }
    }

    suspend fun startActivity(item: CaptureWorkItemEntity, type: String, note: String?) {
        val definition = runtime.module.workItems.getValue(item.processType)
        require(type in definition.allowedInterruptions) { "Unterbrechungsart ist für den Vorgang nicht zulässig" }
        val activityDefinition = runtime.module.source.processes.interruptionTypes.single { it.id == type }
        require(!activityDefinition.noteRequired || !note.isNullOrBlank()) { "Hinweis ist erforderlich" }
        val id = "ACT-${UUID.randomUUID()}"
        database.withTransaction {
            dao.insertActivity(CaptureActivityEntity(id, item.id, type, now(), note = note?.trim()))
            audit("work_item.activity_started", item.employeeId, id, "{\"type\":\"$type\",\"work_item_id\":\"${item.id}\"}")
        }
    }

    suspend fun finishActivity(item: CaptureWorkItemEntity, activity: CaptureActivityEntity) {
        database.withTransaction {
            check(dao.finishActivity(activity.id, now()) == 1) { "Aktivität ist nicht mehr aktiv" }
            audit("work_item.activity_completed", item.employeeId, activity.id, "{\"work_item_id\":\"${item.id}\"}")
        }
    }

    fun activeActivity(workItemId: String): Flow<CaptureActivityEntity?> = dao.observeActiveActivity(workItemId)

    suspend fun exportShiftCsv(shift: CaptureShiftEntity): String {
        val rows = dao.workItemsForShift(shift.id)
        val exportId = "EXP-${UUID.randomUUID()}"
        return buildString {
            appendLine("contract_version;record_id;revision_number;source_device_id;export_id;payload_digest;tenant_id;module_id;schema_version;shift_id;employee_id;process_type;start_timestamp;end_timestamp;status;custom_data;manually_modified;deleted_for_audit;net_duration_seconds;target_duration_seconds")
            rows.forEach { item ->
                val netDuration = item.endedAtUtc?.let { Duration.between(Instant.parse(item.startedAtUtc), Instant.parse(it)).seconds.toDouble() } ?: 0.0
                val targetDuration = runtime.module.workItems.getValue(item.processType).targetDurationSeconds.toDouble()
                val schemaVersion = 2
                val digest = WorkRecordContract.payloadDigest(WorkRecordDigestInput(
                    tenantId = item.tenantId, moduleId = item.moduleId, schemaVersion = schemaVersion,
                    recordId = item.id, shiftId = item.shiftId, employeeId = item.employeeId,
                    processType = item.processType, startTimestamp = item.startedAtUtc, endTimestamp = item.endedAtUtc,
                    status = item.status, customData = item.customDataJson, manuallyModified = item.manuallyModified,
                    deletedForAudit = item.deletedForAudit, netDurationSeconds = netDuration,
                    targetDurationSeconds = targetDuration,
                ))
                appendLine(listOf(
                    WorkRecordContract.VERSION_V2, item.id, item.revisionNumber.toString(), sourceDeviceId, exportId, digest,
                    item.tenantId, item.moduleId, schemaVersion.toString(), item.shiftId, item.employeeId,
                    item.processType, item.startedAtUtc, item.endedAtUtc.orEmpty(), item.status,
                    csv(item.customDataJson), item.manuallyModified.toString(), item.deletedForAudit.toString(),
                    netDuration.toString(), targetDuration.toString(),
                ).joinToString(";"))
            }
        }
    }

    /**
     * Replaces every editable part of a work item in one atomic transaction.
     * The original state remains reconstructable from the hash-chained audit event.
     */
    suspend fun supervisorReplaceWorkItem(
        principal: Principal,
        workItemId: String,
        replacement: SupervisorWorkItemReplacement,
        reason: String,
    ) {
        require(reason.isNotBlank()) { "Änderungsgrund ist erforderlich" }
        AuthorizationService(runtime.profile.profile).require(
            principal,
            AccessRequest("work_item.correct", runtime.profile.profile.tenantId),
        )
        database.withTransaction {
            val original = requireNotNull(dao.workItem(workItemId)) { "Vorgang nicht gefunden" }
            require(original.tenantId == principal.tenantId) { "Mandantengrenze verletzt" }
            val shift = requireNotNull(dao.shift(original.shiftId)) { "Schicht nicht gefunden" }
            validateReplacement(shift, replacement)
            val oldActivities = dao.activitiesForWorkItem(workItemId)
            val updated = original.copy(
                processType = replacement.processType,
                employeeId = replacement.employeeId,
                startedAtUtc = replacement.startedAtUtc,
                endedAtUtc = replacement.endedAtUtc,
                status = "completed",
                customDataJson = replacement.customDataJson,
                revisionNumber = original.revisionNumber + 1,
                manuallyModified = true,
            )
            dao.updateWorkItem(updated)
            dao.deleteActivitiesForWorkItem(workItemId)
            replacement.activities.forEach { activity ->
                dao.insertActivity(
                    CaptureActivityEntity(
                        id = activity.id ?: "ACT-${UUID.randomUUID()}",
                        workItemId = workItemId,
                        type = activity.type,
                        startedAtUtc = activity.startedAtUtc,
                        endedAtUtc = activity.endedAtUtc,
                        note = activity.note?.trim(),
                    ),
                )
            }
            dao.insertCorrection(CaptureCorrectionEntity(
                id = "COR-${UUID.randomUUID()}", tenantId = original.tenantId, workItemId = workItemId,
                actorId = principal.id, action = "replace", reason = reason.trim(), createdAtUtc = now(),
            ))
            audit(
                "work_item.supervisor_replaced", principal.id, workItemId,
                "{\"reason\":${json(reason.trim())},\"before\":${json(original.toString() + oldActivities)},\"after\":${json(updated.toString() + replacement.activities)}}",
            )
        }
    }

    /** Soft deletion: the item stays visible to history/reporting and is never cascaded away. */
    suspend fun supervisorDeleteWorkItem(principal: Principal, workItemId: String, reason: String) {
        require(reason.isNotBlank()) { "Löschgrund ist erforderlich" }
        AuthorizationService(runtime.profile.profile).require(
            principal,
            AccessRequest("work_item.delete", runtime.profile.profile.tenantId),
        )
        database.withTransaction {
            val original = requireNotNull(dao.workItem(workItemId)) { "Vorgang nicht gefunden" }
            require(original.tenantId == principal.tenantId) { "Mandantengrenze verletzt" }
            require(!original.deletedForAudit) { "Vorgang ist bereits gelöscht" }
            dao.updateWorkItem(original.copy(
                status = "deleted", deletedForAudit = true, manuallyModified = true,
                revisionNumber = original.revisionNumber + 1,
            ))
            dao.insertCorrection(CaptureCorrectionEntity(
                id = "COR-${UUID.randomUUID()}", tenantId = original.tenantId, workItemId = workItemId,
                actorId = principal.id, action = "delete", reason = reason.trim(), createdAtUtc = now(),
            ))
            audit("work_item.supervisor_deleted", principal.id, workItemId, "{\"reason\":${json(reason.trim())},\"before\":${json(original.toString())}}")
        }
    }

    private fun validateReplacement(shift: CaptureShiftEntity, replacement: SupervisorWorkItemReplacement) {
        require(runtime.module.workItems.containsKey(replacement.processType)) { "Unbekannter Vorgang" }
        require(replacement.employeeId.matches(Regex("^[0-9]+$"))) { "Personalnummer darf nur Ziffern enthalten" }
        val start = Instant.parse(replacement.startedAtUtc)
        val end = Instant.parse(replacement.endedAtUtc)
        require(start < end) { "Ende muss nach dem Start liegen" }
        require(start >= Instant.parse(shift.startedAtUtc)) { "Vorgang beginnt vor der Schicht" }
        shift.endedAtUtc?.let { require(end <= Instant.parse(it)) { "Vorgang endet nach der Schicht" } }
        val definition = runtime.module.workItems.getValue(replacement.processType)
        val intervals = replacement.activities.map { activity ->
            require(activity.type in definition.allowedInterruptions) { "Unterbrechungsart ist nicht zulässig: ${activity.type}" }
            val type = runtime.module.source.processes.interruptionTypes.single { it.id == activity.type }
            require(!type.noteRequired || !activity.note.isNullOrBlank()) { "Hinweis ist erforderlich: ${activity.type}" }
            val activityStart = Instant.parse(activity.startedAtUtc)
            val activityEnd = Instant.parse(activity.endedAtUtc)
            require(activityStart >= start && activityEnd <= end && activityStart < activityEnd) { "Unterbrechung liegt außerhalb des Vorgangs" }
            activityStart to activityEnd
        }.sortedBy { it.first }
        intervals.zipWithNext().forEach { (left, right) ->
            require(left.second <= right.first) { "Unterbrechungen dürfen sich nicht überschneiden" }
        }
    }

    private suspend fun audit(event: String, actor: String, subject: String, payload: String) {
        val tenant = runtime.profile.profile.tenantId
        val sequence = dao.lastAuditSequence(tenant) + 1
        val previous = dao.lastAuditHash(tenant) ?: AuditLedger.GENESIS_HASH
        val occurred = now()
        val payloadDigest = sha256(payload)
        val entryHash = sha256(listOf(sequence, tenant, event, actor, subject, occurred, payloadDigest, previous).joinToString("\u001f"))
        dao.insertAudit(
            CaptureAuditEntity(
                id = "AUD-${UUID.randomUUID()}",
                tenantId = tenant,
                sequence = sequence,
                eventType = event,
                actorId = actor,
                subjectId = subject,
                occurredAtUtc = occurred,
                payloadJson = payload,
                previousHash = previous,
                entryHash = entryHash,
            ),
        )
    }

    private fun now(): String = Instant.now(clock).toString()
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun json(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
}

data class SupervisorActivityReplacement(
    val id: String? = null,
    val type: String,
    val startedAtUtc: String,
    val endedAtUtc: String,
    val note: String? = null,
)

data class SupervisorWorkItemReplacement(
    val processType: String,
    val employeeId: String,
    val startedAtUtc: String,
    val endedAtUtc: String,
    val customDataJson: String = "{}",
    val activities: List<SupervisorActivityReplacement> = emptyList(),
)
