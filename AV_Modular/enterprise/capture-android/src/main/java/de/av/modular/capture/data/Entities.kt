package de.av.modular.capture.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "capture_shifts", indices = [Index("tenant_id"), Index("employee_id")])
data class CaptureShiftEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "module_id") val moduleId: String,
    @ColumnInfo(name = "shift_type") val shiftType: String,
    @ColumnInfo(name = "employee_id") val employeeId: String,
    @ColumnInfo(name = "started_at_utc") val startedAtUtc: String,
    @ColumnInfo(name = "ended_at_utc") val endedAtUtc: String? = null,
    val status: String = "active",
)

@Entity(
    tableName = "capture_work_items",
    foreignKeys = [ForeignKey(
        entity = CaptureShiftEntity::class,
        parentColumns = ["id"],
        childColumns = ["shift_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("shift_id"), Index("tenant_id"), Index("process_type")],
)
data class CaptureWorkItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "module_id") val moduleId: String,
    @ColumnInfo(name = "shift_id") val shiftId: String,
    @ColumnInfo(name = "process_type") val processType: String,
    @ColumnInfo(name = "employee_id") val employeeId: String,
    @ColumnInfo(name = "started_at_utc") val startedAtUtc: String,
    @ColumnInfo(name = "ended_at_utc") val endedAtUtc: String? = null,
    val status: String = "active",
    @ColumnInfo(name = "custom_data_json") val customDataJson: String = "{}",
    @ColumnInfo(name = "revision_number") val revisionNumber: Long = 1,
    @ColumnInfo(name = "manually_modified") val manuallyModified: Boolean = false,
    @ColumnInfo(name = "deleted_for_audit") val deletedForAudit: Boolean = false,
)

@Entity(
    tableName = "capture_activities",
    foreignKeys = [ForeignKey(
        entity = CaptureWorkItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["work_item_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("work_item_id")],
)
data class CaptureActivityEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "work_item_id") val workItemId: String,
    val type: String,
    @ColumnInfo(name = "started_at_utc") val startedAtUtc: String,
    @ColumnInfo(name = "ended_at_utc") val endedAtUtc: String? = null,
    val note: String? = null,
)

@Entity(tableName = "capture_corrections", indices = [Index("work_item_id"), Index("tenant_id")])
data class CaptureCorrectionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    @ColumnInfo(name = "work_item_id") val workItemId: String,
    @ColumnInfo(name = "actor_id") val actorId: String,
    val action: String,
    val reason: String,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: String,
)

@Entity(tableName = "capture_audit", indices = [Index("tenant_id"), Index(value = ["tenant_id", "sequence"], unique = true)])
data class CaptureAuditEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tenant_id") val tenantId: String,
    val sequence: Long,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "actor_id") val actorId: String,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    @ColumnInfo(name = "occurred_at_utc") val occurredAtUtc: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "previous_hash") val previousHash: String,
    @ColumnInfo(name = "entry_hash") val entryHash: String,
)
