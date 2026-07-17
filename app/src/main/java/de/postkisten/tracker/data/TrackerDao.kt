package de.postkisten.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    @Insert suspend fun insertBox(box: BoxEntity): Long
    @Update suspend fun updateBox(box: BoxEntity)
    @Delete suspend fun deleteBox(box: BoxEntity)

    @Query("""UPDATE work_processes SET
        related_box_id = CASE WHEN related_box_id = :boxId THEN NULL ELSE related_box_id END,
        previous_box_id = CASE WHEN previous_box_id = :boxId THEN NULL ELSE previous_box_id END,
        next_box_id = CASE WHEN next_box_id = :boxId THEN NULL ELSE next_box_id END,
        updated_at_utc = :updatedAtUtc,
        manually_modified = 1,
        change_log = change_log || CASE WHEN change_log = '' THEN '' ELSE char(10) END || :auditEntry
        WHERE related_box_id = :boxId OR previous_box_id = :boxId OR next_box_id = :boxId""")
    suspend fun detachBoxReferences(boxId: Long, updatedAtUtc: Long, auditEntry: String)
    @Insert suspend fun insertInterruption(value: InterruptionEntity): Long
    @Update suspend fun updateInterruption(value: InterruptionEntity)
    @Delete suspend fun deleteInterruption(value: InterruptionEntity)
    @Insert suspend fun insertShift(value: ShiftEntity): Long
    @Update suspend fun updateShift(value: ShiftEntity)
    @Insert suspend fun insertProcess(value: WorkProcessEntity): Long
    @Update suspend fun updateProcess(value: WorkProcessEntity)
    @Delete suspend fun deleteProcess(value: WorkProcessEntity)

    @Transaction
    @Query("SELECT * FROM boxes WHERE status IN ('ACTIVE', 'SUSPENDED') ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<BoxWithInterruptions?>

    @Transaction
    @Query("SELECT * FROM boxes WHERE status IN ('ACTIVE', 'SUSPENDED') ORDER BY id DESC LIMIT 1")
    suspend fun getActive(): BoxWithInterruptions?

    @Transaction
    @Query("SELECT * FROM boxes WHERE id = :id")
    fun observeBox(id: Long): Flow<BoxWithInterruptions?>

    @Transaction
    @Query("SELECT * FROM boxes WHERE id = :id")
    suspend fun getBox(id: Long): BoxWithInterruptions?

    @Transaction
    @Query("SELECT * FROM shifts WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    fun observeActiveShift(): Flow<ShiftWithData?>

    @Transaction
    @Query("SELECT * FROM shifts WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveShift(): ShiftWithData?

    @Transaction
    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getShift(id: Long): ShiftWithData?

    @Transaction
    @Query("SELECT * FROM shifts ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, scheduled_start_at_utc DESC")
    fun observeShifts(): Flow<List<ShiftWithData>>

    @Transaction
    @Query("SELECT * FROM shifts ORDER BY scheduled_start_at_utc DESC")
    suspend fun getAllShifts(): List<ShiftWithData>

    @Query("SELECT * FROM work_processes WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    fun observeActiveProcess(): Flow<WorkProcessEntity?>

    @Query("SELECT * FROM work_processes WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveProcess(): WorkProcessEntity?

    @Query("SELECT * FROM work_processes WHERE id = :id")
    suspend fun getProcess(id: Long): WorkProcessEntity?

    @Query("SELECT * FROM work_processes WHERE shift_id = :shiftId ORDER BY started_at_utc")
    suspend fun getProcessesForShift(shiftId: Long): List<WorkProcessEntity>

    @Query("SELECT * FROM boxes WHERE shift_id IS NULL ORDER BY started_at_utc")
    suspend fun getUnassignedBoxes(): List<BoxEntity>

    @Query("SELECT * FROM shifts WHERE shift_type = :type AND shift_date = :shiftDate LIMIT 1")
    suspend fun getShiftByTypeAndDate(type: ShiftType, shiftDate: String): ShiftEntity?

    @Query("SELECT COUNT(*) FROM boxes WHERE shift_id = :shiftId AND counts_as_box = 1")
    suspend fun countBoxesForShift(shiftId: Long): Int

    @Transaction
    @Query("SELECT * FROM boxes WHERE started_at_utc >= :from AND started_at_utc < :to ORDER BY started_at_utc DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<BoxWithInterruptions>>

    @Transaction
    @Query("SELECT * FROM boxes WHERE status = 'FINISHED' ORDER BY started_at_utc DESC LIMIT :limit")
    fun observeHistory(limit: Int = 200): Flow<List<BoxWithInterruptions>>

    @Transaction
    @Query("SELECT * FROM boxes WHERE started_at_utc >= :from AND started_at_utc < :to AND status = 'FINISHED' ORDER BY started_at_utc")
    suspend fun getFinishedBetween(from: Long, to: Long): List<BoxWithInterruptions>

    @Query("SELECT COUNT(*) FROM boxes WHERE display_number LIKE :datePrefix || '%'")
    suspend fun countForDate(datePrefix: String): Int
}
