package de.av.modular.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.av.modular.capture.data.CaptureActivityEntity
import de.av.modular.capture.data.CaptureShiftEntity
import de.av.modular.capture.data.CaptureWorkItemEntity
import de.av.modular.model.InterruptionDefinition
import de.av.modular.model.WorkItemDefinition
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

private val AvRed = Color(0xFFD40511)
private val AvYellow = Color(0xFFFFCC00)

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<CaptureViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CaptureApp(viewModel) } }
    }
}

@Composable
private fun CaptureApp(vm: CaptureViewModel) {
    val shift by vm.activeShift.collectAsStateWithLifecycle()
    val item by vm.activeWorkItem.collectAsStateWithLifecycle()
    val activity by vm.activeActivity.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().background(AvYellow).padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("AV CAPTURE", color = AvRed, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(vm.runtime.module.source.manifest.displayName, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            when {
                shift == null -> ShiftStart(vm)
                item == null -> WorkItemSelection(vm, shift!!)
                else -> ActiveWorkItem(vm, item!!, activity)
            }
        }
    }
}

@Composable
private fun ShiftStart(vm: CaptureViewModel) {
    var employee by remember { mutableStateOf("") }
    var selectedShift by remember { mutableStateOf(vm.runtime.module.source.processes.shiftTypes.first().id) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Schicht und Personalnummer", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = employee,
                onValueChange = { employee = it.filter(Char::isDigit) },
                label = { Text("Personalnummer") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            vm.runtime.module.source.processes.shiftTypes.forEach { shift ->
                ActionButton(
                    text = shift.displayName,
                    onClick = { selectedShift = shift.id },
                    filled = selectedShift == shift.id,
                )
            }
            ActionButton("SCHICHT STARTEN", { vm.startShift(employee, selectedShift) }, enabled = employee.isNotBlank())
        }
    }
}

@Composable
private fun WorkItemSelection(vm: CaptureViewModel, shift: CaptureShiftEntity) {
    Text("${shift.employeeId} · ${shift.shiftType}", fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        items(vm.runtime.module.workItems.values.toList(), key = WorkItemDefinition::id) { definition ->
            ActionButton(definition.displayName.uppercase(), { vm.startWorkItem(shift, definition.id) })
        }
    }
    Spacer(Modifier.height(10.dp))
    ActionButton("SCHICHT BEENDEN", { vm.finishShift(shift) }, filled = false)
}

@Composable
private fun ActiveWorkItem(vm: CaptureViewModel, item: CaptureWorkItemEntity, activity: CaptureActivityEntity?) {
    val definition = vm.runtime.module.workItems.getValue(item.processType)
    var now by remember { mutableStateOf(Instant.now()) }
    var pendingOther by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(item.id) { while (true) { now = Instant.now(); delay(1_000) } }
    val duration = Duration.between(Instant.parse(item.startedAtUtc), now).coerceAtLeast(Duration.ZERO)
    Text(definition.displayName.uppercase(), color = AvRed, fontSize = 25.sp, fontWeight = FontWeight.Black)
    Text("Aktive Zeit", fontSize = 18.sp)
    Text(format(duration), fontSize = 46.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(14.dp))
    if (activity != null) {
        val activityDefinition = vm.runtime.module.source.processes.interruptionTypes.single { it.id == activity.type }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AKTIV: ${activityDefinition.displayName}", fontWeight = FontWeight.Black, color = AvRed)
                activity.note?.let { Text(it) }
                ActionButton("AKTIVITÄT BEENDEN", { vm.finishActivity(item, activity) })
            }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            items(definition.allowedInterruptions) { type ->
                val interrupt = vm.runtime.module.source.processes.interruptionTypes.single { it.id == type }
                ActionButton(interrupt.displayName.uppercase(), {
                    if (interrupt.noteRequired) pendingOther = true else vm.startActivity(item, interrupt.id, null)
                })
            }
        }
        ActionButton("VORGANG BEENDEN", { vm.finishWorkItem(item) }, filled = false)
    }
    if (pendingOther) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kurzinformation erforderlich", fontWeight = FontWeight.Bold)
                OutlinedTextField(note, { note = it.take(160) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("ABBRECHEN", { pendingOther = false }, Modifier.weight(1f), filled = false)
                    ActionButton("STARTEN", {
                        val other = vm.runtime.module.source.processes.interruptionTypes.single(InterruptionDefinition::noteRequired)
                        vm.startActivity(item, other.id, note)
                        pendingOther = false
                        note = ""
                    }, Modifier.weight(1f), enabled = note.isNotBlank())
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(58.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) AvRed else Color.White,
            contentColor = if (filled) Color.White else AvRed,
        ),
        shape = RoundedCornerShape(10.dp),
    ) { Text(text, fontWeight = FontWeight.Black, textAlign = TextAlign.Center) }
}

private fun format(duration: Duration): String {
    val seconds = duration.seconds
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}
