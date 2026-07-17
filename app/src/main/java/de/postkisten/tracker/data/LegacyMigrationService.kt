package de.postkisten.tracker.data

import android.content.Context
import androidx.room.withTransaction
import de.postkisten.tracker.shift.ShiftResolver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LegacyMigrationService(
    private val context: Context,
    private val database: TrackerDatabase,
    private val resolver: ShiftResolver,
) {
    private val dao = database.trackerDao()
    private val zone = ZoneId.of("Europe/Berlin")

    suspend fun migrateUnassignedData() = database.withTransaction {
        val boxes = dao.getUnassignedBoxes()
        boxes.forEach { box -> migrateBox(box) }
    }

    private suspend fun migrateBox(box: BoxEntity) {
        val knownRegistrationPlaceholder = box.displayNumber == "2026-07-16-003"
        val candidates = if (knownRegistrationPlaceholder) {
            listOf(resolver.getShiftWindow(ShiftType.NIGHT, LocalDate.of(2026, 7, 16)))
        } else resolver.resolveCandidates(box.startedAtUtc)
        val chosen = candidates.singleOrNull() ?: candidates.minByOrNull {
            kotlin.math.abs(box.startedAtUtc - it.scheduledStartAtUtc)
        } ?: run {
            val date = Instant.ofEpochMilli(box.startedAtUtc).atZone(zone).toLocalDate()
            ShiftType.entries.map { resolver.getShiftWindow(it, date) }
                .minBy { kotlin.math.abs(box.startedAtUtc - it.scheduledStartAtUtc) }
        }
        val ambiguous = !knownRegistrationPlaceholder && candidates.size != 1
        var shift = dao.getShiftByTypeAndDate(chosen.type, chosen.shiftDate.toString())
        if (shift == null) {
            val active = box.status == BoxStatus.ACTIVE || box.status == BoxStatus.SUSPENDED
            val now = System.currentTimeMillis()
            val id = dao.insertShift(
                ShiftEntity(
                    type = chosen.type,
                    shiftDate = chosen.shiftDate.toString(),
                    scheduledStartAtUtc = chosen.scheduledStartAtUtc,
                    scheduledEndAtUtc = chosen.scheduledEndAtUtc,
                    actualFirstActivityAtUtc = box.startedAtUtc,
                    actualLastActivityAtUtc = box.endedAtUtc,
                    status = if (active) ShiftStatus.ACTIVE else ShiftStatus.COMPLETED,
                    personnelNumber = box.employeeNumber,
                    createdAtUtc = now,
                    updatedAtUtc = now,
                    changeLog = if (ambiguous) "Migration: Schichtzuordnung muss vom Teamleiter geprüft werden." else "",
                ),
            )
            shift = dao.getShift(id)?.shift ?: error("Migrierte Schicht konnte nicht geladen werden.")
        }
        val assignedShift = shift ?: error("Schichtzuordnung fehlgeschlagen.")
        dao.updateBox(
            box.copy(
                shiftId = assignedShift.id,
                legacyBoxId = box.displayNumber,
                countsAsBox = !knownRegistrationPlaceholder,
                migrationAmbiguous = ambiguous,
            ),
        )
        val first = minOf(assignedShift.actualFirstActivityAtUtc ?: box.startedAtUtc, box.startedAtUtc)
        val last = listOfNotNull(assignedShift.actualLastActivityAtUtc, box.endedAtUtc).maxOrNull()
        val active = box.status == BoxStatus.ACTIVE || box.status == BoxStatus.SUSPENDED
        dao.updateShift(
            assignedShift.copy(
                actualFirstActivityAtUtc = first,
                actualLastActivityAtUtc = last,
                status = if (active) ShiftStatus.ACTIVE else assignedShift.status,
                updatedAtUtc = System.currentTimeMillis(),
            ),
        )
        if (knownRegistrationPlaceholder && dao.getProcessesForShift(assignedShift.id).none { it.legacyAggregateMillis != null }) {
            val full = dao.getBox(box.id) ?: return
            val registrationStart = full.interruptions
                .filter { it.type == InterruptionType.REGISTRATION }.minOfOrNull { it.startedAtUtc } ?: box.startedAtUtc
            val registrationEnd = full.interruptions
                .filter { it.type == InterruptionType.REGISTRATION }.mapNotNull { it.endedAtUtc }.maxOrNull() ?: box.endedAtUtc
            val end = registrationEnd ?: registrationStart
            dao.insertProcess(
                WorkProcessEntity(
                    type = ProcessType.REGISTRATION,
                    shiftId = assignedShift.id,
                    personnelNumber = box.employeeNumber,
                    startedAtUtc = registrationStart,
                    endedAtUtc = end,
                    status = ProcessStatus.MANUALLY_CORRECTED,
                    note = "Migrierter Registrierungs-Platzhalter ${box.displayNumber}",
                    relatedBoxId = box.id,
                    legacyAggregateMillis = 9_850_000L,
                    createdAtUtc = box.createdAtUtc,
                    updatedAtUtc = System.currentTimeMillis(),
                    manuallyModified = true,
                    changeLog = "Migration: historisches Registrierungsaggregat 02:44:10 erhalten.",
                ),
            )
            full.interruptions.filter { it.type == InterruptionType.PAUSE }.forEach { pause ->
                dao.insertProcess(
                    WorkProcessEntity(
                        type = ProcessType.BREAK,
                        shiftId = assignedShift.id,
                        personnelNumber = box.employeeNumber,
                        startedAtUtc = pause.startedAtUtc,
                        endedAtUtc = pause.endedAtUtc,
                        status = ProcessStatus.COMPLETED,
                        note = "Aus Registrierungs-Platzhalter migriert",
                        relatedBoxId = box.id,
                        createdAtUtc = box.createdAtUtc,
                        updatedAtUtc = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}
