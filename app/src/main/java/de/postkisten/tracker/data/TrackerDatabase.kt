package de.postkisten.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

class Converters {
    @TypeConverter fun boxStatus(value: BoxStatus): String = value.name
    @TypeConverter fun boxStatus(value: String): BoxStatus = BoxStatus.valueOf(value)
    @TypeConverter fun boxType(value: BoxType): String = value.name
    @TypeConverter fun boxType(value: String): BoxType = BoxType.valueOf(value)
    @TypeConverter fun interruptionType(value: InterruptionType): String = value.name
    @TypeConverter fun interruptionType(value: String): InterruptionType = InterruptionType.valueOf(value)
    @TypeConverter fun shiftType(value: ShiftType): String = value.name
    @TypeConverter fun shiftType(value: String): ShiftType = ShiftType.valueOf(value)
    @TypeConverter fun shiftStatus(value: ShiftStatus): String = value.name
    @TypeConverter fun shiftStatus(value: String): ShiftStatus = ShiftStatus.valueOf(value)
    @TypeConverter fun processType(value: ProcessType): String = value.name
    @TypeConverter fun processType(value: String): ProcessType = ProcessType.valueOf(value)
    @TypeConverter fun processStatus(value: ProcessStatus): String = value.name
    @TypeConverter fun processStatus(value: String): ProcessStatus = ProcessStatus.valueOf(value)
}

@Database(
    entities = [BoxEntity::class, InterruptionEntity::class, ShiftEntity::class, WorkProcessEntity::class],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao

    companion object {
        @Volatile private var instance: TrackerDatabase? = null
        fun get(context: Context): TrackerDatabase = instance ?: synchronized(this) {
            createPreMigrationBackup(context.applicationContext)
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TrackerDatabase::class.java,
                "postkisten_tracker.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }

        private fun createPreMigrationBackup(context: Context) {
            val source = context.getDatabasePath("postkisten_tracker.db")
            if (!source.exists()) return
            val backup = File(source.parentFile, "postkisten_tracker.pre_shift_backup.db")
            if (!backup.exists()) runCatching {
                source.copyTo(backup, overwrite = false)
                listOf("-wal", "-shm").forEach { suffix ->
                    val sidecar = File(source.path + suffix)
                    if (sidecar.exists()) sidecar.copyTo(File(backup.path + suffix), overwrite = false)
                }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boxes ADD COLUMN box_type TEXT NOT NULL DEFAULT 'DAILY_MAIL'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boxes ADD COLUMN employee_number TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boxes ADD COLUMN manual_edit_history TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE boxes ADD COLUMN manual_edited_at_utc INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boxes ADD COLUMN shift_id INTEGER")
                db.execSQL("ALTER TABLE boxes ADD COLUMN legacy_box_id TEXT")
                db.execSQL("ALTER TABLE boxes ADD COLUMN counts_as_box INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE boxes ADD COLUMN migration_ambiguous INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_boxes_shift_id ON boxes(shift_id)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS shifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        shift_type TEXT NOT NULL,
                        shift_date TEXT NOT NULL,
                        scheduled_start_at_utc INTEGER NOT NULL,
                        scheduled_end_at_utc INTEGER NOT NULL,
                        actual_first_activity_at_utc INTEGER,
                        actual_last_activity_at_utc INTEGER,
                        status TEXT NOT NULL,
                        personnel_number TEXT NOT NULL,
                        created_at_utc INTEGER NOT NULL,
                        updated_at_utc INTEGER NOT NULL,
                        manually_modified INTEGER NOT NULL DEFAULT 0,
                        change_log TEXT NOT NULL DEFAULT '',
                        schema_version INTEGER NOT NULL DEFAULT 1
                    )""".trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_shifts_shift_type_shift_date ON shifts(shift_type, shift_date)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS work_processes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        process_type TEXT NOT NULL,
                        shift_id INTEGER NOT NULL,
                        personnel_number TEXT NOT NULL,
                        started_at_utc INTEGER NOT NULL,
                        ended_at_utc INTEGER,
                        status TEXT NOT NULL,
                        note TEXT,
                        related_box_id INTEGER,
                        parent_process_id INTEGER,
                        previous_box_id INTEGER,
                        next_box_id INTEGER,
                        legacy_aggregate_millis INTEGER,
                        created_at_utc INTEGER NOT NULL,
                        updated_at_utc INTEGER NOT NULL,
                        manually_modified INTEGER NOT NULL DEFAULT 0,
                        change_log TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(shift_id) REFERENCES shifts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_processes_shift_id ON work_processes(shift_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_processes_related_box_id ON work_processes(related_box_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_processes_parent_process_id ON work_processes(parent_process_id)")
            }
        }
    }
}
