package de.postkisten.tracker

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.postkisten.tracker.data.BoxWithInterruptions
import de.postkisten.tracker.data.BoxType
import de.postkisten.tracker.data.ClockSnapshot
import de.postkisten.tracker.data.DeviceClock
import de.postkisten.tracker.data.InterruptionType
import de.postkisten.tracker.data.InterruptionEntity
import de.postkisten.tracker.data.ProcessEndAction
import de.postkisten.tracker.data.ProcessType
import de.postkisten.tracker.data.ShiftType
import de.postkisten.tracker.data.ShiftWithData
import de.postkisten.tracker.data.WorkProcessEntity
import de.postkisten.tracker.data.ShiftExportType
import de.postkisten.tracker.data.TrackerRepository
import de.postkisten.tracker.security.TeamLeaderKey
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen { HOME, HISTORY, SHIFTS, INFO }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TrackerRepository = (application as TrackerApplication).repository
    val active = repository.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val today = repository.boxesFor(LocalDate.now()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeShift = repository.activeShift.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val shifts = repository.shifts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeProcess = repository.activeProcess.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val storedShiftCount = repository.shiftCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val storedBoxCount = repository.boxCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val preferences = application.getSharedPreferences("av_erfassung_status", Context.MODE_PRIVATE)
    private val _lastSuccessfulExport = MutableStateFlow(preferences.getLong("last_successful_export", 0L))
    val lastSuccessfulExport = _lastSuccessfulExport.asStateFlow()

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen = _screen.asStateFlow()
    private val _selected = MutableStateFlow<BoxWithInterruptions?>(null)
    val selected = _selected.asStateFlow()
    private val _selectedShift = MutableStateFlow<ShiftWithData?>(null)
    val selectedShift = _selectedShift.asStateFlow()
    private val _clock = MutableStateFlow(DeviceClock.snapshot(application))
    val clock: StateFlow<ClockSnapshot> = _clock.asStateFlow()
    private val _recoveryNeeded = MutableStateFlow(false)
    val recoveryNeeded = _recoveryNeeded.asStateFlow()
    private val _errors = MutableSharedFlow<String>()
    val errors = _errors.asSharedFlow()
    private val _teamLeaderValidUntil = MutableStateFlow(0L)
    val teamLeaderValidUntil = _teamLeaderValidUntil.asStateFlow()

    init {
        viewModelScope.launch {
            repository.migrateLegacyData()
            val existing = repository.getActiveOnce() != null
            _recoveryNeeded.value = existing
            if (existing) TrackingServiceBridge.start(application)
        }
        viewModelScope.launch {
            while (isActive) {
                _clock.value = DeviceClock.snapshot(application)
                delay(1_000)
            }
        }
    }

    fun navigate(screen: Screen) { _selected.value = null; _selectedShift.value = null; _screen.value = screen }
    fun showDetail(item: BoxWithInterruptions) { _selected.value = item }
    fun showShift(item: ShiftWithData) { _selectedShift.value = item }
    fun closeDetail() { _selected.value = null }
    fun closeShift() { _selectedShift.value = null }
    fun dismissRecovery() { _recoveryNeeded.value = false }

    fun startBox(type: BoxType, employeeNumber: String) = action {
        repository.startBox(type, employeeNumber); buzz(); TrackingServiceBridge.start(getApplication())
    }
    fun startShift(type: ShiftType, shiftDate: LocalDate, employeeNumber: String) = action {
        repository.startShift(type, shiftDate, employeeNumber); buzz(); TrackingServiceBridge.start(getApplication())
    }
    fun startShiftAndBox(shiftType: ShiftType, shiftDate: LocalDate, employeeNumber: String, boxType: BoxType) = action {
        repository.startShift(shiftType, shiftDate, employeeNumber)
        repository.startBox(boxType, employeeNumber)
        buzz(); TrackingServiceBridge.start(getApplication())
    }
    fun startShiftAndProcess(
        shiftType: ShiftType,
        shiftDate: LocalDate,
        employeeNumber: String,
        processType: ProcessType,
        note: String? = null,
    ) = action {
        repository.startShift(shiftType, shiftDate, employeeNumber)
        repository.startWorkProcess(processType, note)
        buzz(); TrackingServiceBridge.start(getApplication())
    }
    fun shiftCandidates() = repository.shiftCandidates()
    fun startProcess(type: ProcessType, note: String? = null) = action {
        repository.startWorkProcess(type, note); buzz(); TrackingServiceBridge.start(getApplication())
    }
    fun interrupt(type: InterruptionType, note: String? = null) = startProcess(
        when (type) {
            InterruptionType.PAUSE -> ProcessType.BREAK
            InterruptionType.REGISTRATION -> ProcessType.REGISTRATION
            InterruptionType.IMAGE -> ProcessType.IMAGE
            InterruptionType.MISC -> ProcessType.OTHER
        },
        note,
    )
    fun resume() = endProcess(ProcessEndAction.RESUME_RELATED)
    fun endProcess(endAction: ProcessEndAction = ProcessEndAction.RESUME_RELATED) = action {
        repository.endWorkProcess(endAction); buzz()
    }
    fun resumeSuspendedBox() = action { repository.resumeSuspendedBox(); buzz() }
    fun finish() = action {
        repository.finishBox(); buzz(); TrackingServiceBridge.start(getApplication())
    }
    fun finishShift() = action {
        repository.finishShift(); buzz(); TrackingServiceBridge.stop(getApplication())
    }
    fun deleteMistake() = action {
        repository.deleteActiveAsMistake(); TrackingServiceBridge.stop(getApplication()); _recoveryNeeded.value = false
    }

    fun unlockTeamLeader(key: String): String? {
        val result = TeamLeaderKey.validate(key)
        if (result.valid) _teamLeaderValidUntil.value = result.expiresAtUtc ?: 0L
        return if (result.valid) null else result.message
    }

    fun editFinishedBox(
        item: BoxWithInterruptions,
        type: BoxType,
        employeeNumber: String,
        startedAtUtc: Long,
        endedAtUtc: Long,
        reason: String,
    ) = action {
        checkTeamLeaderSession()
        _selected.value = repository.editFinishedBox(
            item.box.id, type, employeeNumber, startedAtUtc, endedAtUtc, reason,
        )
        buzz()
    }

    fun addManualInterruption(
        item: BoxWithInterruptions,
        type: InterruptionType,
        startedAtUtc: Long,
        endedAtUtc: Long,
        note: String?,
        reason: String,
    ) = action {
        checkTeamLeaderSession()
        _selected.value = repository.addManualInterruption(
            item.box.id, type, startedAtUtc, endedAtUtc, note, reason,
        )
        buzz()
    }

    fun updateManualInterruption(
        item: BoxWithInterruptions,
        interruption: InterruptionEntity,
        type: InterruptionType,
        startedAtUtc: Long,
        endedAtUtc: Long,
        note: String?,
        reason: String,
    ) = action {
        checkTeamLeaderSession()
        _selected.value = repository.updateManualInterruption(
            item.box.id, interruption.id, type, startedAtUtc, endedAtUtc, note, reason,
        )
        buzz()
    }

    fun deleteManualInterruption(
        item: BoxWithInterruptions,
        interruption: InterruptionEntity,
        reason: String,
    ) = action {
        checkTeamLeaderSession()
        _selected.value = repository.deleteManualInterruption(item.box.id, interruption.id, reason)
        buzz()
    }

    fun addManualProcess(item: ShiftWithData, type: ProcessType, start: Long, end: Long, note: String?, previous: Long?, next: Long?, reason: String) = action {
        checkTeamLeaderSession(); _selectedShift.value = repository.addManualProcess(item.shift.id, type, start, end, note, previous, next, reason); buzz()
    }

    fun updateManualProcess(process: WorkProcessEntity, shiftId: Long, type: ProcessType, start: Long, end: Long, note: String?, previous: Long?, next: Long?, reason: String) = action {
        checkTeamLeaderSession(); _selectedShift.value = repository.updateManualProcess(process.id, shiftId, type, start, end, note, previous, next, reason); buzz()
    }

    fun cancelManualProcess(process: WorkProcessEntity, reason: String) = action {
        checkTeamLeaderSession(); _selectedShift.value = repository.cancelManualProcess(process.id, reason); buzz()
    }

    fun reassignBox(item: BoxWithInterruptions, shiftId: Long, reason: String) = action {
        checkTeamLeaderSession(); _selected.value = repository.reassignBox(item.box.id, shiftId, reason); buzz()
    }

    fun deleteBoxAsTeamLeader(item: BoxWithInterruptions, reason: String) = action {
        checkTeamLeaderSession()
        _selectedShift.value = repository.deleteBoxAsTeamLeader(item.box.id, reason)
        _selected.value = null
        buzz()
    }

    suspend fun csv(): String = repository.exportCsv(LocalDate.now())
    suspend fun shiftCsv(ids: Set<Long>? = null, type: ShiftExportType = ShiftExportType.BOTH): String =
        repository.exportShifts(ids, type)

    fun recordSuccessfulExport(timestamp: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("last_successful_export", timestamp).apply()
        _lastSuccessfulExport.value = timestamp
    }

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _errors.emit(it.message ?: "Aktion fehlgeschlagen") }
    }

    private fun checkTeamLeaderSession() {
        check(System.currentTimeMillis() < _teamLeaderValidUntil.value) {
            "Teamleiter-Schlüssel ist nicht mehr gültig."
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        val context = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

private object TrackingServiceBridge {
    fun start(context: Context) = de.postkisten.tracker.service.TrackingService.start(context)
    fun stop(context: Context) = de.postkisten.tracker.service.TrackingService.stop(context)
}
