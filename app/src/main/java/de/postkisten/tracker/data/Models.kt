package de.postkisten.tracker.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class BoxStatus { ACTIVE, SUSPENDED, FINISHED, CANCELLED }
enum class ShiftType(val label: String, val prefix: String) {
    EARLY("Frühschicht", "F"),
    LATE("Spätschicht", "S"),
    NIGHT("Nachtschicht", "N"),
}
enum class ShiftStatus { PLANNED, ACTIVE, COMPLETED, MANUALLY_CORRECTED }
enum class ProcessType(val label: String, val productive: Boolean) {
    BOX_CHANGE("Kistenwechsel", true),
    REGISTRATION("Registrierung", true),
    IMAGE("Image", true),
    OTHER("Diverse", true),
    BREAK("Pause", false),
    SHIFT_PREPARATION("Schichtvorbereitung", true),
    SHIFT_CLEANUP("Aufräumen / Schichtabschluss", true),
}
enum class ProcessStatus { ACTIVE, SUSPENDED, COMPLETED, CANCELLED, MANUALLY_CORRECTED }
enum class BoxType(val label: String) {
    DAILY_MAIL("Tagespost"),
    CASEWORK("Sachbearbeitung"),
    RETURNS("Rückläufer"),
    ROUTING("Routing"),
    FILING("Ablage"),
    HR_FILE("HR-Akte"),
}
enum class InterruptionType(val label: String) {
    PAUSE("Pause"), REGISTRATION("Registrierung"), IMAGE("Image"), MISC("Diverse")
}

@Entity(tableName = "boxes", indices = [Index("shift_id")])
data class BoxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "display_number") val displayNumber: String,
    @ColumnInfo(name = "box_type", defaultValue = "'DAILY_MAIL'") val type: BoxType,
    @ColumnInfo(name = "employee_number", defaultValue = "''") val employeeNumber: String,
    @ColumnInfo(name = "started_at_utc") val startedAtUtc: Long,
    @ColumnInfo(name = "started_elapsed_realtime") val startedElapsedRealtime: Long,
    @ColumnInfo(name = "boot_count") val bootCount: Int,
    @ColumnInfo(name = "ended_at_utc") val endedAtUtc: Long? = null,
    val status: BoxStatus = BoxStatus.ACTIVE,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: Long,
    @ColumnInfo(name = "manual_edit_history", defaultValue = "''") val manualEditHistory: String = "",
    @ColumnInfo(name = "manual_edited_at_utc") val manualEditedAtUtc: Long? = null,
    @ColumnInfo(name = "shift_id") val shiftId: Long? = null,
    @ColumnInfo(name = "legacy_box_id") val legacyBoxId: String? = null,
    @ColumnInfo(name = "counts_as_box", defaultValue = "1") val countsAsBox: Boolean = true,
    @ColumnInfo(name = "migration_ambiguous", defaultValue = "0") val migrationAmbiguous: Boolean = false,
)

@Entity(
    tableName = "shifts",
    indices = [Index(value = ["shift_type", "shift_date"], unique = true)],
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "shift_type") val type: ShiftType,
    @ColumnInfo(name = "shift_date") val shiftDate: String,
    @ColumnInfo(name = "scheduled_start_at_utc") val scheduledStartAtUtc: Long,
    @ColumnInfo(name = "scheduled_end_at_utc") val scheduledEndAtUtc: Long,
    @ColumnInfo(name = "actual_first_activity_at_utc") val actualFirstActivityAtUtc: Long? = null,
    @ColumnInfo(name = "actual_last_activity_at_utc") val actualLastActivityAtUtc: Long? = null,
    val status: ShiftStatus = ShiftStatus.PLANNED,
    @ColumnInfo(name = "personnel_number") val personnelNumber: String,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: Long,
    @ColumnInfo(name = "manually_modified", defaultValue = "0") val manuallyModified: Boolean = false,
    @ColumnInfo(name = "change_log", defaultValue = "''") val changeLog: String = "",
    @ColumnInfo(name = "schema_version", defaultValue = "1") val schemaVersion: Int = 1,
)

@Entity(
    tableName = "work_processes",
    foreignKeys = [ForeignKey(
        entity = ShiftEntity::class,
        parentColumns = ["id"],
        childColumns = ["shift_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("shift_id"), Index("related_box_id"), Index("parent_process_id")],
)
data class WorkProcessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "process_type") val type: ProcessType,
    @ColumnInfo(name = "shift_id") val shiftId: Long,
    @ColumnInfo(name = "personnel_number") val personnelNumber: String,
    @ColumnInfo(name = "started_at_utc") val startedAtUtc: Long,
    @ColumnInfo(name = "ended_at_utc") val endedAtUtc: Long? = null,
    val status: ProcessStatus = ProcessStatus.ACTIVE,
    val note: String? = null,
    @ColumnInfo(name = "related_box_id") val relatedBoxId: Long? = null,
    @ColumnInfo(name = "parent_process_id") val parentProcessId: Long? = null,
    @ColumnInfo(name = "previous_box_id") val previousBoxId: Long? = null,
    @ColumnInfo(name = "next_box_id") val nextBoxId: Long? = null,
    @ColumnInfo(name = "legacy_aggregate_millis") val legacyAggregateMillis: Long? = null,
    @ColumnInfo(name = "created_at_utc") val createdAtUtc: Long,
    @ColumnInfo(name = "updated_at_utc") val updatedAtUtc: Long,
    @ColumnInfo(name = "manually_modified", defaultValue = "0") val manuallyModified: Boolean = false,
    @ColumnInfo(name = "change_log", defaultValue = "''") val changeLog: String = "",
)

@Entity(
    tableName = "interruptions",
    foreignKeys = [ForeignKey(
        entity = BoxEntity::class,
        parentColumns = ["id"],
        childColumns = ["box_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("box_id")],
)
data class InterruptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "box_id") val boxId: Long,
    val type: InterruptionType,
    @ColumnInfo(name = "started_at_utc") val startedAtUtc: Long,
    @ColumnInfo(name = "started_elapsed_realtime") val startedElapsedRealtime: Long,
    @ColumnInfo(name = "boot_count") val bootCount: Int,
    @ColumnInfo(name = "ended_at_utc") val endedAtUtc: Long? = null,
    @ColumnInfo(name = "optional_note") val optionalNote: String? = null,
)

data class BoxWithInterruptions(
    @Embedded val box: BoxEntity,
    @Relation(parentColumn = "id", entityColumn = "box_id")
    val interruptions: List<InterruptionEntity>,
)

data class ShiftWithData(
    @Embedded val shift: ShiftEntity,
    @Relation(
        entity = BoxEntity::class,
        parentColumn = "id",
        entityColumn = "shift_id",
    )
    val boxes: List<BoxWithInterruptions>,
    @Relation(parentColumn = "id", entityColumn = "shift_id")
    val processes: List<WorkProcessEntity>,
)
