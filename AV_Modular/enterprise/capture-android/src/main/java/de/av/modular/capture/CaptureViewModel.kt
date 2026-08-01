package de.av.modular.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.av.modular.capture.data.CaptureActivityEntity
import de.av.modular.capture.data.CaptureShiftEntity
import de.av.modular.capture.data.CaptureWorkItemEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CaptureApplication
    val runtime = app.runtime
    private val repository = app.repository
    val activeShift = repository.activeShift.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val activeWorkItem = repository.activeWorkItem.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val activeActivity = repository.activeWorkItem.flatMapLatest { item ->
        item?.let { repository.activeActivity(it.id) } ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun startShift(employeeId: String, shiftType: String) = action { repository.startShift(employeeId, shiftType) }
    fun finishShift(shift: CaptureShiftEntity) = action { repository.finishShift(shift) }
    fun startWorkItem(shift: CaptureShiftEntity, processType: String) = action { repository.startWorkItem(shift, processType) }
    fun finishWorkItem(item: CaptureWorkItemEntity) = action { repository.finishWorkItem(item) }
    fun startActivity(item: CaptureWorkItemEntity, type: String, note: String?) = action { repository.startActivity(item, type, note) }
    fun finishActivity(item: CaptureWorkItemEntity, activity: CaptureActivityEntity) = action { repository.finishActivity(item, activity) }

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { _messages.emit(it.message ?: "Aktion fehlgeschlagen") }
    }
}
