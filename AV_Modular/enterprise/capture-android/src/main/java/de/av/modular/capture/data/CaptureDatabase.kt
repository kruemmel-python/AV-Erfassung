package de.av.modular.capture.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert suspend fun insertShift(value: CaptureShiftEntity)
    @Insert suspend fun insertWorkItem(value: CaptureWorkItemEntity)
    @Insert suspend fun insertActivity(value: CaptureActivityEntity)
    @Insert suspend fun insertCorrection(value: CaptureCorrectionEntity)
    @Insert suspend fun insertAudit(value: CaptureAuditEntity)
    @Update suspend fun updateWorkItem(value: CaptureWorkItemEntity)

    @Query("SELECT * FROM capture_shifts WHERE status = 'active' LIMIT 1")
    fun observeActiveShift(): Flow<CaptureShiftEntity?>

    @Query("SELECT * FROM capture_work_items WHERE status = 'active' LIMIT 1")
    fun observeActiveWorkItem(): Flow<CaptureWorkItemEntity?>

    @Query("SELECT * FROM capture_activities WHERE work_item_id = :workItemId AND ended_at_utc IS NULL LIMIT 1")
    fun observeActiveActivity(workItemId: String): Flow<CaptureActivityEntity?>

    @Query("UPDATE capture_shifts SET status = 'completed', ended_at_utc = :endedAt WHERE id = :id AND status = 'active'")
    suspend fun finishShift(id: String, endedAt: String): Int

    @Query("UPDATE capture_work_items SET status = 'completed', ended_at_utc = :endedAt WHERE id = :id AND status = 'active'")
    suspend fun finishWorkItem(id: String, endedAt: String): Int

    @Query("UPDATE capture_activities SET ended_at_utc = :endedAt WHERE id = :id AND ended_at_utc IS NULL")
    suspend fun finishActivity(id: String, endedAt: String): Int

    @Query("SELECT * FROM capture_work_items WHERE shift_id = :shiftId ORDER BY started_at_utc")
    suspend fun workItemsForShift(shiftId: String): List<CaptureWorkItemEntity>

    @Query("SELECT * FROM capture_work_items WHERE id = :id LIMIT 1")
    suspend fun workItem(id: String): CaptureWorkItemEntity?

    @Query("SELECT * FROM capture_shifts WHERE id = :id LIMIT 1")
    suspend fun shift(id: String): CaptureShiftEntity?

    @Query("SELECT * FROM capture_activities WHERE work_item_id = :workItemId ORDER BY started_at_utc")
    suspend fun activitiesForWorkItem(workItemId: String): List<CaptureActivityEntity>

    @Query("DELETE FROM capture_activities WHERE work_item_id = :workItemId")
    suspend fun deleteActivitiesForWorkItem(workItemId: String)

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM capture_audit WHERE tenant_id = :tenantId")
    suspend fun lastAuditSequence(tenantId: String): Long

    @Query("SELECT entry_hash FROM capture_audit WHERE tenant_id = :tenantId ORDER BY sequence DESC LIMIT 1")
    suspend fun lastAuditHash(tenantId: String): String?
}

@Database(
    entities = [
        CaptureShiftEntity::class,
        CaptureWorkItemEntity::class,
        CaptureActivityEntity::class,
        CaptureCorrectionEntity::class,
        CaptureAuditEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CaptureDatabase : RoomDatabase() {
    abstract fun dao(): CaptureDao

    companion object {
        val MIGRATION_1_2 = Migration(1, 2) { database ->
            database.execSQL("ALTER TABLE capture_work_items ADD COLUMN revision_number INTEGER NOT NULL DEFAULT 1")
        }
    }
}
