package de.postkisten.tracker.data

import android.content.Context
import androidx.room.withTransaction
import de.postkisten.tracker.shift.ShiftResolver
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow

class TrackerRepository(
    private val context: Context,
    private val database: TrackerDatabase,
) {
    private val dao = database.trackerDao()
    private val shiftResolver = ShiftResolver()
    private val legacyMigration = LegacyMigrationService(context, database, shiftResolver)
    val active: Flow<BoxWithInterruptions?> = dao.observeActive()
    val history: Flow<List<BoxWithInterruptions>> = dao.observeHistory()
    val activeShift: Flow<ShiftWithData?> = dao.observeActiveShift()
    val shifts: Flow<List<ShiftWithData>> = dao.observeShifts()
    val activeProcess: Flow<WorkProcessEntity?> = dao.observeActiveProcess()

    suspend fun migrateLegacyData() = legacyMigration.migrateUnassignedData()
    fun shiftCandidates(timestampUtc: Long = System.currentTimeMillis()) = shiftResolver.resolveCandidates(timestampUtc)

    suspend fun getActiveOnce(): BoxWithInterruptions? = dao.getActive()
    suspend fun exportShifts(ids: Set<Long>? = null, type: ShiftExportType = ShiftExportType.BOTH): String {
        val selected = dao.getAllShifts().filter { ids == null || it.shift.id in ids }
        require(selected.isNotEmpty()) { "Keine Schichten für den Export ausgewählt." }
        return CsvExportService.create(selected, type)
    }

    suspend fun addManualProcess(
        shiftId: Long, type: ProcessType, startedAtUtc: Long, endedAtUtc: Long,
        note: String?, previousBoxId: Long?, nextBoxId: Long?, reason: String,
    ): ShiftWithData = database.withTransaction {
        validateManualProcess(type, startedAtUtc, endedAtUtc, note, reason)
        val data = dao.getShift(shiftId) ?: error("Schicht nicht gefunden.")
        val now = System.currentTimeMillis()
        dao.insertProcess(WorkProcessEntity(
            type = type, shiftId = shiftId, personnelNumber = data.shift.personnelNumber,
            startedAtUtc = startedAtUtc, endedAtUtc = endedAtUtc,
            status = ProcessStatus.MANUALLY_CORRECTED, note = note?.trim()?.take(100),
            previousBoxId = previousBoxId, nextBoxId = nextBoxId,
            createdAtUtc = now, updatedAtUtc = now, manuallyModified = true,
            changeLog = "${stamp(now)} hinzugefügt: ${reason.trim()}",
        ))
        auditShift(data.shift, now, "${type.label} hinzugefügt", reason)
        dao.getShift(shiftId) ?: error("Schicht konnte nicht neu geladen werden.")
    }

    suspend fun updateManualProcess(
        processId: Long, shiftId: Long, type: ProcessType, startedAtUtc: Long, endedAtUtc: Long,
        note: String?, previousBoxId: Long?, nextBoxId: Long?, reason: String,
    ): ShiftWithData = database.withTransaction {
        validateManualProcess(type, startedAtUtc, endedAtUtc, note, reason)
        val current = dao.getProcess(processId) ?: error("Prozess nicht gefunden.")
        val shift = dao.getShift(shiftId)?.shift ?: error("Schicht nicht gefunden.")
        val now = System.currentTimeMillis()
        dao.updateProcess(current.copy(
            shiftId = shiftId, personnelNumber = shift.personnelNumber,
            type = type, startedAtUtc = startedAtUtc, endedAtUtc = endedAtUtc,
            status = ProcessStatus.MANUALLY_CORRECTED, note = note?.trim()?.take(100),
            previousBoxId = previousBoxId, nextBoxId = nextBoxId,
            updatedAtUtc = now, manuallyModified = true,
            changeLog = current.changeLog.appendAudit(now, "Prozess geändert", reason),
        ))
        auditShift(shift, now, "${type.label} geändert", reason)
        dao.getShift(shiftId) ?: error("Schicht konnte nicht neu geladen werden.")
    }

    suspend fun cancelManualProcess(processId: Long, reason: String): ShiftWithData = database.withTransaction {
        require(reason.isNotBlank()) { "Bitte einen Änderungsgrund eingeben." }
        val current = dao.getProcess(processId) ?: error("Prozess nicht gefunden.")
        val shift = dao.getShift(current.shiftId)?.shift ?: error("Schicht nicht gefunden.")
        val now = System.currentTimeMillis()
        dao.updateProcess(current.copy(
            status = ProcessStatus.CANCELLED, updatedAtUtc = now, manuallyModified = true,
            changeLog = current.changeLog.appendAudit(now, "Prozess gelöscht/storniert", reason),
        ))
        auditShift(shift, now, "${current.type.label} storniert", reason)
        dao.getShift(current.shiftId) ?: error("Schicht konnte nicht neu geladen werden.")
    }

    suspend fun reassignBox(boxId: Long, shiftId: Long, reason: String): BoxWithInterruptions = database.withTransaction {
        require(reason.isNotBlank()) { "Bitte einen Änderungsgrund eingeben." }
        val box = dao.getBox(boxId) ?: error("Kiste nicht gefunden.")
        val target = dao.getShift(shiftId)?.shift ?: error("Zielschicht nicht gefunden.")
        val now = System.currentTimeMillis()
        dao.updateBox(box.box.copy(shiftId = shiftId, employeeNumber = target.personnelNumber)
            .withAudit(now, "Schichtzuordnung ${box.box.shiftId ?: "ohne"} → $shiftId", reason))
        auditShift(target, now, "Kiste ${box.box.displayNumber} zugeordnet", reason)
        dao.getBox(boxId) ?: error("Kiste konnte nicht neu geladen werden.")
    }

    suspend fun deleteBoxAsTeamLeader(boxId: Long, reason: String): ShiftWithData? = database.withTransaction {
        require(reason.isNotBlank()) { "Bitte einen Löschgrund eingeben." }
        val box = dao.getBox(boxId) ?: error("Kiste nicht gefunden.")
        check(box.box.status !in setOf(BoxStatus.ACTIVE, BoxStatus.SUSPENDED)) {
            "Eine laufende oder unterbrochene Kiste muss zuerst regulär beendet werden."
        }
        check(box.box.status != BoxStatus.CANCELLED) { "Diese Kiste wurde bereits gelöscht." }
        val shift = box.box.shiftId?.let { dao.getShift(it)?.shift }
        val now = System.currentTimeMillis()
        dao.updateBox(
            box.box.copy(status = BoxStatus.CANCELLED)
                .withAudit(now, "Kiste durch Teamleiter gelöscht", reason),
        )
        shift?.let { auditShift(it, now, "Kiste ${box.box.displayNumber} als gelöscht markiert", reason) }
        shift?.let { dao.getShift(it.id) }
    }

    private suspend fun auditShift(shift: ShiftEntity, now: Long, action: String, reason: String) {
        dao.updateShift(shift.copy(
            status = if (shift.status == ShiftStatus.ACTIVE) ShiftStatus.ACTIVE else ShiftStatus.MANUALLY_CORRECTED,
            manuallyModified = true, updatedAtUtc = now,
            changeLog = shift.changeLog.appendAudit(now, action, reason),
        ))
    }

    private fun validateManualProcess(type: ProcessType, start: Long, end: Long, note: String?, reason: String) {
        require(end > start) { "Das Ende muss nach dem Start liegen." }
        require(reason.isNotBlank()) { "Bitte einen Änderungsgrund eingeben." }
        require(type != ProcessType.OTHER || !note.isNullOrBlank()) { "Diverse benötigt eine kurze Information." }
    }

    fun boxesFor(date: LocalDate): Flow<List<BoxWithInterruptions>> {
        val (from, to) = date.utcRange()
        return dao.observeBetween(from, to)
    }

    suspend fun startBox(type: BoxType, employeeNumber: String): Long = database.withTransaction {
        require(employeeNumber.isNotBlank() && employeeNumber.all(Char::isDigit)) {
            "Bitte eine gültige Personalnummer eingeben."
        }
        check(dao.getActive() == null) { "Es läuft bereits eine oder unterbrochene Kiste." }
        val shiftData = dao.getActiveShift() ?: error("Bitte zuerst eine Schicht auswählen und bestätigen.")
        val shift = shiftData.shift
        val clock = DeviceClock.snapshot(context)
        val sequence = dao.countBoxesForShift(shift.id) + 1
        val displayNumber = "%s-%s-%03d".format(shift.type.prefix, shift.shiftDate, sequence)
        val runningChange = dao.getActiveProcess()?.takeIf { it.type == ProcessType.BOX_CHANGE }
        runningChange?.let { dao.updateProcess(it.copy(endedAtUtc = clock.utcMillis, status = ProcessStatus.COMPLETED, updatedAtUtc = clock.utcMillis)) }
        val id = dao.insertBox(
            BoxEntity(
                displayNumber = displayNumber,
                type = type,
                employeeNumber = shift.personnelNumber.ifBlank { employeeNumber },
                startedAtUtc = clock.utcMillis,
                startedElapsedRealtime = clock.elapsedMillis,
                bootCount = clock.bootCount,
                createdAtUtc = clock.utcMillis,
                updatedAtUtc = clock.utcMillis,
                shiftId = shift.id,
            ),
        )
        runningChange?.let { dao.updateProcess(it.copy(nextBoxId = id, endedAtUtc = clock.utcMillis, status = ProcessStatus.COMPLETED, updatedAtUtc = clock.utcMillis)) }
        touchShift(shift, clock.utcMillis)
        id
    }

    suspend fun startShift(type: ShiftType, shiftDate: LocalDate, personnelNumber: String): Long = database.withTransaction {
        require(personnelNumber.isNotBlank() && personnelNumber.all(Char::isDigit)) { "Bitte eine gültige Personalnummer eingeben." }
        check(dao.getActiveShift() == null) { "Es ist bereits eine Schicht aktiv." }
        val window = shiftResolver.getShiftWindow(type, shiftDate)
        val now = DeviceClock.snapshot(context).utcMillis
        val existing = dao.getShiftByTypeAndDate(type, shiftDate.toString())
        if (existing != null) {
            dao.updateShift(existing.copy(status = ShiftStatus.ACTIVE, personnelNumber = personnelNumber, updatedAtUtc = now))
            existing.id
        } else dao.insertShift(
            ShiftEntity(
                type = type,
                shiftDate = shiftDate.toString(),
                scheduledStartAtUtc = window.scheduledStartAtUtc,
                scheduledEndAtUtc = window.scheduledEndAtUtc,
                status = ShiftStatus.ACTIVE,
                personnelNumber = personnelNumber,
                createdAtUtc = now,
                updatedAtUtc = now,
            ),
        )
    }

    suspend fun startWorkProcess(type: ProcessType, note: String? = null): Long = database.withTransaction {
        require(type != ProcessType.OTHER || !note.isNullOrBlank()) { "Bitte eine kurze Information zu Diverse eingeben." }
        val shiftData = dao.getActiveShift() ?: error("Bitte zuerst eine Schicht auswählen und bestätigen.")
        val shift = shiftData.shift
        val now = DeviceClock.snapshot(context).utcMillis
        val currentProcess = dao.getActiveProcess()
        var parentProcessId: Long? = null
        if (currentProcess != null) {
            when {
                currentProcess.type == ProcessType.BOX_CHANGE -> dao.updateProcess(
                    currentProcess.copy(endedAtUtc = now, status = ProcessStatus.COMPLETED, updatedAtUtc = now),
                )
                currentProcess.type == ProcessType.REGISTRATION && type in setOf(ProcessType.BREAK, ProcessType.IMAGE, ProcessType.OTHER) -> {
                    dao.updateProcess(currentProcess.copy(status = ProcessStatus.SUSPENDED, updatedAtUtc = now))
                    parentProcessId = currentProcess.id
                }
                else -> error("Es läuft bereits ${currentProcess.type.label}.")
            }
        }
        val activeBox = dao.getActive()
        val relatedBoxId = activeBox?.box?.takeIf { it.status == BoxStatus.ACTIVE }?.id
        if (relatedBoxId != null) {
            dao.updateBox(activeBox.box.copy(status = BoxStatus.SUSPENDED, updatedAtUtc = now))
            dao.insertInterruption(
                InterruptionEntity(
                    boxId = relatedBoxId,
                    type = type.toInterruptionType(),
                    startedAtUtc = now,
                    startedElapsedRealtime = 0L,
                    bootCount = 0,
                    optionalNote = note?.trim()?.take(100),
                ),
            )
        }
        val id = dao.insertProcess(
            WorkProcessEntity(
                type = type,
                shiftId = shift.id,
                personnelNumber = shift.personnelNumber,
                startedAtUtc = now,
                status = ProcessStatus.ACTIVE,
                note = note?.trim()?.take(100),
                relatedBoxId = relatedBoxId,
                parentProcessId = parentProcessId,
                createdAtUtc = now,
                updatedAtUtc = now,
            ),
        )
        touchShift(shift, now)
        id
    }

    suspend fun endWorkProcess(action: ProcessEndAction = ProcessEndAction.RESUME_RELATED): Long? = database.withTransaction {
        val process = dao.getActiveProcess() ?: error("Kein aktiver Prozess vorhanden.")
        val now = DeviceClock.snapshot(context).utcMillis
        dao.updateProcess(process.copy(endedAtUtc = now, status = ProcessStatus.COMPLETED, updatedAtUtc = now))
        if (process.parentProcessId != null) {
            dao.getProcess(process.parentProcessId)?.let { parent ->
                dao.updateProcess(parent.copy(status = ProcessStatus.ACTIVE, updatedAtUtc = now))
            }
            return@withTransaction process.parentProcessId
        }
        process.relatedBoxId?.let { boxId ->
            val boxData = dao.getBox(boxId) ?: error("Unterbrochene Kiste wurde nicht gefunden.")
            boxData.interruptions.firstOrNull { it.endedAtUtc == null }?.let {
                dao.updateInterruption(it.copy(endedAtUtc = now))
            }
            when (action) {
                ProcessEndAction.RESUME_RELATED -> dao.updateBox(boxData.box.copy(status = BoxStatus.ACTIVE, updatedAtUtc = now))
                ProcessEndAction.KEEP_SUSPENDED -> Unit
                ProcessEndAction.FINISH_RELATED -> {
                    dao.updateBox(boxData.box.copy(status = BoxStatus.FINISHED, endedAtUtc = now, updatedAtUtc = now))
                    startBoxChange(dao.getActiveShift()?.shift ?: error("Aktive Schicht fehlt."), boxId, now)
                }
            }
        }
        dao.getActiveShift()?.shift?.let { touchShift(it, now) }
        null
    }

    suspend fun resumeSuspendedBox() = database.withTransaction {
        check(dao.getActiveProcess() == null) { "Zuerst den laufenden Prozess beenden." }
        val box = dao.getActive()?.box ?: error("Keine unterbrochene Kiste vorhanden.")
        check(box.status == BoxStatus.SUSPENDED) { "Die Kiste ist nicht unterbrochen." }
        dao.updateBox(box.copy(status = BoxStatus.ACTIVE, updatedAtUtc = System.currentTimeMillis()))
    }

    suspend fun finishShift() = database.withTransaction {
        check(dao.getActive() == null) { "Bitte die laufende oder unterbrochene Kiste zuerst beenden." }
        val shift = dao.getActiveShift()?.shift ?: error("Keine aktive Schicht.")
        val now = DeviceClock.snapshot(context).utcMillis
        dao.getActiveProcess()?.let { process ->
            dao.updateProcess(process.copy(endedAtUtc = now, status = ProcessStatus.COMPLETED, updatedAtUtc = now))
        }
        dao.updateShift(
            shift.copy(status = ShiftStatus.COMPLETED, actualLastActivityAtUtc = now, updatedAtUtc = now),
        )
    }

    suspend fun startInterruption(type: InterruptionType, note: String? = null) = database.withTransaction {
        if (type == InterruptionType.MISC) {
            require(!note.isNullOrBlank()) { "Bitte eine kurze Information zu Diverse eingeben." }
        }
        val active = dao.getActive() ?: error("Keine aktive Kiste.")
        val clock = DeviceClock.snapshot(context)
        active.interruptions.firstOrNull { it.endedAtUtc == null }?.let {
            dao.updateInterruption(it.copy(endedAtUtc = clock.utcMillis))
        }
        dao.insertInterruption(
            InterruptionEntity(
                boxId = active.box.id,
                type = type,
                startedAtUtc = clock.utcMillis,
                startedElapsedRealtime = clock.elapsedMillis,
                bootCount = clock.bootCount,
                optionalNote = note?.trim()?.take(100),
            ),
        )
    }

    suspend fun resumeWork() = database.withTransaction {
        val active = dao.getActive() ?: return@withTransaction
        val current = active.interruptions.firstOrNull { it.endedAtUtc == null } ?: return@withTransaction
        dao.updateInterruption(current.copy(endedAtUtc = DeviceClock.snapshot(context).utcMillis))
    }

    suspend fun finishBox() = database.withTransaction {
        val active = dao.getActive() ?: return@withTransaction
        check(active.box.status == BoxStatus.ACTIVE) { "Die Kiste ist unterbrochen. Bitte zuerst den laufenden Prozess beenden." }
        val now = DeviceClock.snapshot(context).utcMillis
        active.interruptions.firstOrNull { it.endedAtUtc == null }?.let {
            dao.updateInterruption(it.copy(endedAtUtc = now))
        }
        dao.updateBox(active.box.copy(endedAtUtc = now, status = BoxStatus.FINISHED, updatedAtUtc = now))
        val shift = dao.getActiveShift()?.shift ?: error("Aktive Schicht fehlt.")
        startBoxChange(shift, active.box.id, now)
    }

    suspend fun deleteActiveAsMistake() = database.withTransaction {
        dao.getActive()?.let { dao.deleteBox(it.box) }
    }

    suspend fun editFinishedBox(
        boxId: Long,
        type: BoxType,
        employeeNumber: String,
        startedAtUtc: Long,
        endedAtUtc: Long,
        reason: String,
    ): BoxWithInterruptions = database.withTransaction {
        require(employeeNumber.isNotBlank() && employeeNumber.all(Char::isDigit)) {
            "Bitte eine gültige Personalnummer eingeben."
        }
        require(endedAtUtc > startedAtUtc) { "Das Ende muss nach dem Start liegen." }
        require(reason.trim().isNotBlank()) { "Bitte einen Änderungsgrund eingeben." }
        val current = dao.getBox(boxId) ?: error("Kiste nicht gefunden.")
        check(current.box.status == BoxStatus.FINISHED) { "Nur abgeschlossene Kisten können geändert werden." }
        require(current.interruptions.all { it.startedAtUtc >= startedAtUtc && (it.endedAtUtc ?: Long.MAX_VALUE) <= endedAtUtc }) {
            "Mindestens eine Unterbrechung liegt außerhalb der neuen Kistenlaufzeit. Bitte zuerst diese Unterbrechung ändern oder löschen."
        }
        val now = DeviceClock.snapshot(context).utcMillis
        dao.updateBox(
            current.box.copy(
                type = type,
                employeeNumber = employeeNumber,
                startedAtUtc = startedAtUtc,
                endedAtUtc = endedAtUtc,
            ).withAudit(now, "Kistendaten geändert", reason),
        )
        dao.getBox(boxId) ?: error("Kiste konnte nicht neu geladen werden.")
    }

    suspend fun addManualInterruption(
        boxId: Long,
        type: InterruptionType,
        startedAtUtc: Long,
        endedAtUtc: Long,
        note: String?,
        reason: String,
    ): BoxWithInterruptions = database.withTransaction {
        require(reason.trim().isNotBlank()) { "Bitte einen Änderungsgrund eingeben." }
        if (type == InterruptionType.MISC) {
            require(!note.isNullOrBlank()) { "Diverse benötigt eine kurze Information." }
        }
        val current = dao.getBox(boxId) ?: error("Kiste nicht gefunden.")
        check(current.box.status == BoxStatus.FINISHED) { "Nur abgeschlossene Kisten können geändert werden." }
        validateManualInterruption(current.box, current.interruptions, startedAtUtc, endedAtUtc)?.let(::error)
        dao.insertInterruption(
            InterruptionEntity(
                boxId = boxId,
                type = type,
                startedAtUtc = startedAtUtc,
                startedElapsedRealtime = 0L,
                bootCount = 0,
                endedAtUtc = endedAtUtc,
                optionalNote = if (type == InterruptionType.MISC) note?.trim()?.take(100) else null,
            ),
        )
        val now = DeviceClock.snapshot(context).utcMillis
        dao.updateBox(current.box.withAudit(now, "${type.label}-Unterbrechung hinzugefügt", reason))
        dao.getBox(boxId) ?: error("Kiste konnte nicht neu geladen werden.")
    }

    suspend fun updateManualInterruption(
        boxId: Long,
        interruptionId: Long,
        type: InterruptionType,
        startedAtUtc: Long,
        endedAtUtc: Long,
        note: String?,
        reason: String,
    ): BoxWithInterruptions = database.withTransaction {
        require(reason.trim().isNotBlank()) { "Bitte einen Änderungsgrund eingeben." }
        if (type == InterruptionType.MISC) {
            require(!note.isNullOrBlank()) { "Diverse benötigt eine kurze Information." }
        }
        val current = dao.getBox(boxId) ?: error("Kiste nicht gefunden.")
        check(current.box.status == BoxStatus.FINISHED) { "Nur abgeschlossene Kisten können geändert werden." }
        val interruption = current.interruptions.firstOrNull { it.id == interruptionId }
            ?: error("Unterbrechung nicht gefunden.")
        validateManualInterruption(
            current.box, current.interruptions, startedAtUtc, endedAtUtc, interruptionId,
        )?.let(::error)
        dao.updateInterruption(
            interruption.copy(
                type = type,
                startedAtUtc = startedAtUtc,
                endedAtUtc = endedAtUtc,
                optionalNote = if (type == InterruptionType.MISC) note?.trim()?.take(100) else null,
            ),
        )
        val now = DeviceClock.snapshot(context).utcMillis
        dao.updateBox(current.box.withAudit(now, "Unterbrechung geändert (${interruption.type.label} → ${type.label})", reason))
        dao.getBox(boxId) ?: error("Kiste konnte nicht neu geladen werden.")
    }

    suspend fun deleteManualInterruption(
        boxId: Long,
        interruptionId: Long,
        reason: String,
    ): BoxWithInterruptions = database.withTransaction {
        require(reason.trim().isNotBlank()) { "Bitte einen Änderungsgrund eingeben." }
        val current = dao.getBox(boxId) ?: error("Kiste nicht gefunden.")
        check(current.box.status == BoxStatus.FINISHED) { "Nur abgeschlossene Kisten können geändert werden." }
        val interruption = current.interruptions.firstOrNull { it.id == interruptionId }
            ?: error("Unterbrechung nicht gefunden.")
        dao.deleteInterruption(interruption)
        val now = DeviceClock.snapshot(context).utcMillis
        dao.updateBox(current.box.withAudit(now, "${interruption.type.label}-Unterbrechung gelöscht", reason))
        dao.getBox(boxId) ?: error("Kiste konnte nicht neu geladen werden.")
    }

    suspend fun exportCsv(date: LocalDate): String {
        val (from, to) = date.utcRange()
        val rows = dao.getFinishedBetween(from, to)
        val time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        return buildString {
            append('\uFEFF')
            appendLine("Kiste;Kistenart;Personalnummer;Start;Ende;Brutto;Pause;Registrierung;Image;Diverse;Diverse Hinweise;Netto;Manuell geändert;Änderungsprotokoll")
            rows.forEach { item ->
                fun sum(type: InterruptionType) = item.interruptions.filter { it.type == type }.sumOf { it.durationMillis() }
                appendLine(listOf(
                    item.box.displayNumber,
                    item.box.type.label,
                    item.box.employeeNumber,
                    time.format(item.box.startedAtUtc.asInstant()),
                    time.format(item.box.endedAtUtc!!.asInstant()),
                    item.grossMillis().asDuration(),
                    sum(InterruptionType.PAUSE).asDuration(),
                    sum(InterruptionType.REGISTRATION).asDuration(),
                    sum(InterruptionType.IMAGE).asDuration(),
                    sum(InterruptionType.MISC).asDuration(),
                    item.interruptions.filter { it.type == InterruptionType.MISC }
                        .mapNotNull { it.optionalNote }
                        .joinToString(" | ") { it.replace(";", ",") },
                    item.netMillis().asDuration(),
                    item.box.manualEditedAtUtc?.let { time.format(it.asInstant()) } ?: "",
                    item.box.manualEditHistory.replace(";", ",").replace("\n", " | "),
                ).joinToString(";"))
            }
        }
    }

    private fun BoxEntity.withAudit(now: Long, action: String, reason: String): BoxEntity {
        val stamp = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault()).format(now.asInstant())
        val entry = "$stamp – $action – ${reason.trim().take(200)}"
        val history = listOf(manualEditHistory, entry).filter(String::isNotBlank).joinToString("\n")
        return copy(updatedAtUtc = now, manualEditHistory = history, manualEditedAtUtc = now)
    }

    private fun stamp(now: Long) = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault()).format(now.asInstant())

    private fun String.appendAudit(now: Long, action: String, reason: String): String =
        listOf(this, "${stamp(now)} – $action – ${reason.trim().take(200)}")
            .filter(String::isNotBlank).joinToString("\n")

    private suspend fun startBoxChange(shift: ShiftEntity, previousBoxId: Long, now: Long) {
        check(dao.getActiveProcess() == null) { "Kistenwechsel konnte nicht gestartet werden: Ein Prozess läuft bereits." }
        dao.insertProcess(
            WorkProcessEntity(
                type = ProcessType.BOX_CHANGE,
                shiftId = shift.id,
                personnelNumber = shift.personnelNumber,
                startedAtUtc = now,
                previousBoxId = previousBoxId,
                status = ProcessStatus.ACTIVE,
                createdAtUtc = now,
                updatedAtUtc = now,
            ),
        )
        touchShift(shift, now)
    }

    private suspend fun touchShift(shift: ShiftEntity, timestamp: Long) {
        dao.updateShift(
            shift.copy(
                actualFirstActivityAtUtc = shift.actualFirstActivityAtUtc ?: timestamp,
                actualLastActivityAtUtc = timestamp,
                status = ShiftStatus.ACTIVE,
                updatedAtUtc = timestamp,
            ),
        )
    }

    private fun ProcessType.toInterruptionType() = when (this) {
        ProcessType.BREAK -> InterruptionType.PAUSE
        ProcessType.REGISTRATION -> InterruptionType.REGISTRATION
        ProcessType.IMAGE -> InterruptionType.IMAGE
        ProcessType.OTHER -> InterruptionType.MISC
        else -> error("${label} kann eine Kiste nicht unterbrechen.")
    }
}

enum class ProcessEndAction { RESUME_RELATED, KEEP_SUSPENDED, FINISH_RELATED }
