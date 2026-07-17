package de.postkisten.tracker

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.postkisten.tracker.data.BoxWithInterruptions
import de.postkisten.tracker.data.BoxType
import de.postkisten.tracker.data.ClockSnapshot
import de.postkisten.tracker.data.InterruptionType
import de.postkisten.tracker.data.InterruptionEntity
import de.postkisten.tracker.data.ProcessEndAction
import de.postkisten.tracker.data.ProcessType
import de.postkisten.tracker.data.ShiftStatisticsService
import de.postkisten.tracker.data.ShiftType
import de.postkisten.tracker.data.ShiftExportType
import de.postkisten.tracker.data.ShiftWithData
import de.postkisten.tracker.data.WorkProcessEntity
import de.postkisten.tracker.data.BoxStatus
import de.postkisten.tracker.shift.ShiftWindow
import de.postkisten.tracker.data.grossMillis
import de.postkisten.tracker.data.asDuration
import de.postkisten.tracker.data.asInstant
import de.postkisten.tracker.data.durationMillis
import de.postkisten.tracker.data.interruptionMillis
import de.postkisten.tracker.data.netMillis
import de.postkisten.tracker.data.validateManualInterruption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.ResolverStyle
import kotlinx.coroutines.launch

private val DhlRed = Color(0xFFD40511)
private val DhlYellow = Color(0xFFFFCC00)
private val DhlDarkRed = Color(0xFF9B0008)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        setContent { TrackerTheme { TrackerApp(viewModel) } }
    }

    fun keepScreenOn(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun TrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = DhlRed,
            secondary = DhlDarkRed,
            background = DhlYellow,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Color.Black,
        ),
        typography = MaterialTheme.typography,
        content = content,
    )
}

@Composable
private fun TrackerApp(vm: MainViewModel) {
    val active by vm.active.collectAsState()
    val activeProcess by vm.activeProcess.collectAsState()
    val activeShift by vm.activeShift.collectAsState()
    val screen by vm.screen.collectAsState()
    val selected by vm.selected.collectAsState()
    val selectedShift by vm.selectedShift.collectAsState()
    val clock by vm.clock.collectAsState()
    val recovery by vm.recoveryNeeded.collectAsState()
    val activity = androidx.activity.compose.LocalActivity.current as MainActivity
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(active, activeProcess, activeShift) {
        activity.keepScreenOn(active != null || activeProcess != null || activeShift != null)
    }
    LaunchedEffect(Unit) { vm.errors.collect { snack.showSnackbar(it) } }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(DhlYellow)) {
            when {
                selected != null -> DetailScreen(selected!!, vm, vm::closeDetail)
                selectedShift != null -> ShiftReportScreen(selectedShift!!, vm)
                activeProcess != null -> ProcessScreen(activeProcess!!, active, activeShift, clock, vm)
                active != null -> ActiveScreen(active!!, activeShift, clock, vm)
                screen == Screen.HOME -> HomeScreen(vm)
                screen == Screen.HISTORY -> HistoryScreen(vm)
                else -> ShiftsScreen(vm)
            }
        }
    }
    if (recovery && active != null) RecoveryDialog(active!!, vm)
}

@Composable
private fun HomeScreen(vm: MainViewModel) {
    val activeShift by vm.activeShift.collectAsState()
    var chooseType by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf<BoxType?>(null) }
    var pendingProcess by remember { mutableStateOf<ProcessType?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AV-ERFASSUNG", fontWeight = FontWeight.Black, fontSize = 27.sp, color = DhlRed)
            Spacer(Modifier.height(36.dp))
            Text(activeShift?.let { "${it.shift.type.label} · ${it.shift.shiftDate}" } ?: "Keine aktive Schicht", fontSize = 20.sp)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            BigButton("NEUE KISTE STARTEN", { chooseType = true }, Modifier.fillMaxWidth().height(76.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BigButton("REGISTRIERUNG", { pendingProcess = ProcessType.REGISTRATION }, Modifier.weight(1f).height(62.dp), filled = false)
                BigButton("VORBEREITUNG", { pendingProcess = ProcessType.SHIFT_PREPARATION }, Modifier.weight(1f).height(62.dp), filled = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BigButton("PAUSE", { pendingProcess = ProcessType.BREAK }, Modifier.weight(1f).height(62.dp), filled = false)
                BigButton("AUFRÄUMEN", { pendingProcess = ProcessType.SHIFT_CLEANUP }, Modifier.weight(1f).height(62.dp), filled = false)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val stats = activeShift?.let { ShiftStatisticsService.calculate(it) }
                Text("Aktuelle Schicht", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(stats?.let { "${it.boxCount} Kisten · produktiv ${it.productiveMillis.asDuration()}" } ?: "Noch nicht gestartet", fontSize = 17.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("VERLAUF", { vm.navigate(Screen.HISTORY) }, Modifier.weight(1f).height(62.dp), filled = false)
            BigButton("SCHICHTEN", { vm.navigate(Screen.SHIFTS) }, Modifier.weight(1f).height(62.dp), filled = false)
        }
        TextButton(onClick = { showInfo = true }) { Text("INFO", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
    if (chooseType) TypeSelectionDialog(
        close = { chooseType = false },
        select = { chooseType = false; pendingType = it },
    )
    pendingType?.let { type ->
        if (activeShift != null) {
            LaunchedEffect(type) { vm.startBox(type, activeShift!!.shift.personnelNumber); pendingType = null }
        } else ShiftSelectionDialog(vm.shiftCandidates(), { pendingType = null }) { window, number ->
            pendingType = null; vm.startShiftAndBox(window.type, window.shiftDate, number, type)
        }
    }
    pendingProcess?.let { process ->
        if (activeShift != null) {
            LaunchedEffect(process) { vm.startProcess(process); pendingProcess = null }
        } else ShiftSelectionDialog(vm.shiftCandidates(), { pendingProcess = null }) { window, number ->
            pendingProcess = null; vm.startShiftAndProcess(window.type, window.shiftDate, number, process)
        }
    }
    if (showInfo) InfoDialog { showInfo = false }
}

@Composable
private fun ShiftSelectionDialog(
    candidates: List<ShiftWindow>,
    close: () -> Unit,
    start: (ShiftWindow, String) -> Unit,
) {
    val choices = candidates.ifEmpty {
        ShiftType.entries.map { type -> de.postkisten.tracker.shift.ShiftResolver().getShiftWindow(type, LocalDate.now()) }
    }
    var selected by remember { mutableStateOf(choices.first()) }
    var number by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Schicht und Personalnummer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Die Personalnummer darf nur aus Zahlen bestehen.")
                choices.forEach { choice ->
                    BigButton(
                        "${choice.type.label} · ${choice.shiftDate}",
                        { selected = choice },
                        Modifier.fillMaxWidth().height(52.dp),
                        filled = choice == selected,
                    )
                }
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it.filter(Char::isDigit) },
                    label = { Text("Personalnummer") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(enabled = number.isNotBlank(), onClick = { start(selected, number) }) { Text("STARTEN") } },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun InfoDialog(close: () -> Unit) = AlertDialog(
    onDismissRequest = close,
    title = { Text("AV-Erfassung", fontWeight = FontWeight.Black, color = DhlRed) },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Version 2.0.2", fontWeight = FontWeight.Bold)
            HorizontalDivider()
            Text("Developer", fontWeight = FontWeight.Bold)
            Text("Ralf Krümmel")
            Text("ralf.kruemmel@outlook.de")
        }
    },
    confirmButton = { TextButton(onClick = close) { Text("SCHLIESSEN") } },
)

@Composable
private fun ActiveScreen(item: BoxWithInterruptions, shift: ShiftWithData?, clock: ClockSnapshot, vm: MainViewModel) {
    val current = item.interruptions.firstOrNull { it.endedAtUtc == null }
    var confirmFinish by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("KISTE ${item.box.displayNumber}", fontSize = 19.sp, fontWeight = FontWeight.Black, color = DhlRed)
        Text(item.box.type.label.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        shift?.let { Text("${it.shift.type.label} · ${it.shift.shiftDate}", fontSize = 15.sp) }
        Text("Personalnr. ${item.box.employeeNumber}", fontSize = 15.sp)
        Text("Gestartet: ${time(item.box.startedAtUtc)}", fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        if (item.box.status == BoxStatus.SUSPENDED && current == null) {
            Text("KISTE UNTERBROCHEN", fontSize = 23.sp, fontWeight = FontWeight.Black, color = DhlDarkRed)
            Spacer(Modifier.weight(1f))
            BigButton("KISTE FORTSETZEN", vm::resumeSuspendedBox, Modifier.fillMaxWidth().height(82.dp))
        } else if (current != null) {
            Text("UNTERBRECHUNG", fontSize = 22.sp, fontWeight = FontWeight.Black, color = DhlDarkRed)
            Text(current.type.label.uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Black)
            current.optionalNote?.let { Text(it, fontSize = 17.sp, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(12.dp))
            Text("Gestartet: ${time(current.startedAtUtc)}", fontSize = 17.sp)
            Text("Dauer", fontSize = 16.sp)
            Text(current.durationMillis(clock).asDuration(), fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            BigButton("ARBEIT FORTSETZEN", vm::resume, Modifier.fillMaxWidth().height(88.dp))
        } else {
            Text("Aktive Nettozeit", fontSize = 18.sp)
            Text(item.netMillis(clock).asDuration(), fontSize = 45.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            InterruptionGrid(vm)
            Spacer(Modifier.weight(1f))
            BigButton("KISTE BEENDEN", { confirmFinish = true }, Modifier.fillMaxWidth().height(72.dp), danger = true)
        }
    }
    if (confirmFinish) FinishDialog(item, clock, { confirmFinish = false }, { confirmFinish = false; vm.finish() })
}

@Composable
private fun InterruptionGrid(vm: MainViewModel) {
    var miscNote by remember { mutableStateOf(false) }
    var confirmRegistration by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryButton("PAUSE", { vm.interrupt(InterruptionType.PAUSE) }, Modifier.weight(1f))
            CategoryButton("REGISTRIER.", { confirmRegistration = true }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryButton("IMAGE", { vm.interrupt(InterruptionType.IMAGE) }, Modifier.weight(1f))
            CategoryButton("DIVERSE", { miscNote = true }, Modifier.weight(1f))
        }
    }
    if (miscNote) MiscNoteDialog(
        close = { miscNote = false },
        start = { note -> miscNote = false; vm.interrupt(InterruptionType.MISC, note) },
    )
    if (confirmRegistration) AlertDialog(
        onDismissRequest = { confirmRegistration = false },
        title = { Text("Kiste unterbrechen und Registrierung starten?") },
        text = { Text("Die Kistenzeit wird pausiert. Die Registrierungszeit wird separat als produktive Schichtzeit erfasst.") },
        confirmButton = { TextButton(onClick = { confirmRegistration = false; vm.interrupt(InterruptionType.REGISTRATION) }) { Text("REGISTRIERUNG STARTEN") } },
        dismissButton = { TextButton(onClick = { confirmRegistration = false }) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun CategoryButton(label: String, click: () -> Unit, modifier: Modifier) =
    BigButton(label, click, modifier.height(76.dp), filled = false)

@Composable
private fun ProcessScreen(
    process: WorkProcessEntity,
    activeBox: BoxWithInterruptions?,
    shift: ShiftWithData?,
    clock: ClockSnapshot,
    vm: MainViewModel,
) {
    var chooseBox by remember { mutableStateOf(false) }
    var miscNote by remember { mutableStateOf(false) }
    var endRelated by remember { mutableStateOf(false) }
    val duration = process.grossMillis(clock.utcMillis)
    Column(
        Modifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AV-ERFASSUNG", fontWeight = FontWeight.Black, fontSize = 25.sp, color = DhlRed)
        shift?.let { Text("${it.shift.type.label} · ${it.shift.shiftDate}", fontWeight = FontWeight.Bold) }
        Text(process.type.label.uppercase(), fontWeight = FontWeight.Black, fontSize = 27.sp)
        process.note?.let { Text(it, textAlign = TextAlign.Center) }
        Text("Start: ${date(process.startedAtUtc)} ${time(process.startedAtUtc)}")
        Text(duration.asDuration(), fontSize = 44.sp, fontWeight = FontWeight.Bold)
        if (process.type == ProcessType.BOX_CHANGE && duration >= 20 * 60_000L) {
            Text("Hinweis: Der Kistenwechsel dauert länger als 20 Minuten.", color = DhlDarkRed, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        when (process.type) {
            ProcessType.BOX_CHANGE -> {
                BigButton("NÄCHSTE KISTE", { chooseBox = true }, Modifier.fillMaxWidth().height(68.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("REGISTRIERUNG", { vm.startProcess(ProcessType.REGISTRATION) }, Modifier.weight(1f).height(58.dp), filled = false)
                    BigButton("PAUSE", { vm.startProcess(ProcessType.BREAK) }, Modifier.weight(1f).height(58.dp), filled = false)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("DIVERSE", { miscNote = true }, Modifier.weight(1f).height(58.dp), filled = false)
                    BigButton("AUFRÄUMEN", { vm.startProcess(ProcessType.SHIFT_CLEANUP) }, Modifier.weight(1f).height(58.dp), filled = false)
                }
            }
            ProcessType.REGISTRATION -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryButton("PAUSE", { vm.startProcess(ProcessType.BREAK) }, Modifier.weight(1f))
                    CategoryButton("IMAGE", { vm.startProcess(ProcessType.IMAGE) }, Modifier.weight(1f))
                }
                CategoryButton("DIVERSE", { miscNote = true }, Modifier.fillMaxWidth())
                BigButton("REGISTRIERUNG BEENDEN", { if (process.relatedBoxId != null) endRelated = true else vm.endProcess() }, Modifier.fillMaxWidth().height(68.dp), danger = true)
            }
            ProcessType.SHIFT_CLEANUP -> BigButton("SCHICHT BEENDEN", vm::finishShift, Modifier.fillMaxWidth().height(72.dp), danger = true)
            else -> BigButton("PROZESS BEENDEN", { if (process.relatedBoxId != null && process.parentProcessId == null) endRelated = true else vm.endProcess() }, Modifier.fillMaxWidth().height(72.dp))
        }
    }
    if (chooseBox) TypeSelectionDialog({ chooseBox = false }) { type ->
        chooseBox = false
        vm.startBox(type, shift?.shift?.personnelNumber.orEmpty())
    }
    if (miscNote) MiscNoteDialog({ miscNote = false }) { note -> miscNote = false; vm.startProcess(ProcessType.OTHER, note) }
    if (endRelated) AlertDialog(
        onDismissRequest = { endRelated = false },
        title = { Text("Was soll mit der Kiste geschehen?") },
        text = { Text(activeBox?.box?.displayNumber ?: "Verknüpfte Kiste") },
        confirmButton = { TextButton(onClick = { endRelated = false; vm.endProcess(ProcessEndAction.RESUME_RELATED) }) { Text("FORTSETZEN") } },
        dismissButton = {
            Column {
                TextButton(onClick = { endRelated = false; vm.endProcess(ProcessEndAction.KEEP_SUSPENDED) }) { Text("UNTERBROCHEN LASSEN") }
                TextButton(onClick = { endRelated = false; vm.endProcess(ProcessEndAction.FINISH_RELATED) }) { Text("KISTE BEENDEN") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(vm: MainViewModel) {
    val shifts by vm.shifts.collectAsState()
    var expanded by remember { mutableStateOf(emptySet<Long>()) }
    Column(Modifier.fillMaxSize()) {
        AppBar("VERLAUF") { vm.navigate(Screen.HOME) }
        if (shifts.isEmpty()) Empty("Noch keine Schichten") else LazyColumn {
            items(shifts, key = { it.shift.id }) { shift ->
                val stats = ShiftStatisticsService.calculate(shift)
                val deletedCount = shift.boxes.count { it.box.status == BoxStatus.CANCELLED }
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column {
                        Column(Modifier.fillMaxWidth().clickable {
                            expanded = if (shift.shift.id in expanded) expanded - shift.shift.id else expanded + shift.shift.id
                        }.padding(14.dp)) {
                            Text("${shift.shift.type.label} · ${shift.shift.shiftDate}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${stats.boxCount} Kisten · Registrierung ${stats.registrationMillis.asDuration()}")
                            Text("Produktiv ${stats.productiveMillis.asDuration()}", color = DhlRed, fontWeight = FontWeight.Bold)
                            if (deletedCount > 0) Text("$deletedCount gelöschte Kiste(n)", color = DhlDarkRed, fontWeight = FontWeight.Black)
                        }
                        if (shift.shift.id in expanded) {
                            HorizontalDivider()
                            val timeline = (shift.boxes.map { Triple(it.box.startedAtUtc, "box", it) } +
                                shift.processes.map { Triple(it.startedAtUtc, "process", it) }).sortedBy { it.first }
                            timeline.forEach { (_, kind, value) ->
                                when (kind) {
                                    "box" -> {
                                        val box = value as BoxWithInterruptions
                                        val deleted = box.box.status == BoxStatus.CANCELLED
                                        Column(
                                            Modifier.fillMaxWidth()
                                                .background(if (deleted) Color(0xFFFFCDD2) else Color.Transparent)
                                                .clickable { vm.showDetail(box) }
                                                .padding(horizontal = 14.dp, vertical = 9.dp),
                                        ) {
                                            Text("Kiste ${box.box.displayNumber} · ${box.box.type.label}", fontWeight = FontWeight.Bold)
                                            Text("${time(box.box.startedAtUtc)}–${box.box.endedAtUtc?.let(::time) ?: "läuft"} · ${box.netMillis().asDuration()}")
                                            if (deleted) Text("GELÖSCHT", color = DhlDarkRed, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                        }
                                    }
                                    else -> {
                                        val process = value as WorkProcessEntity
                                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
                                            Text(process.type.label, fontWeight = FontWeight.Bold)
                                            Text("${time(process.startedAtUtc)}–${process.endedAtUtc?.let(::time) ?: "läuft"} · ${process.grossMillis().asDuration()}")
                                            process.note?.let { Text(it) }
                                        }
                                    }
                                }
                            }
                            TextButton(onClick = { vm.showShift(shift) }, modifier = Modifier.align(Alignment.End)) { Text("SCHICHTBERICHT") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftsScreen(vm: MainViewModel) {
    val shifts by vm.shifts.collectAsState()
    var preview by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var exportIds by remember { mutableStateOf<Set<Long>?>(null) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val csv = vm.shiftCsv(exportIds, ShiftExportType.BOTH)
            vm.getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(csv) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        AppBar("SCHICHTEN") { vm.navigate(Screen.HOME) }
        LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (shifts.isEmpty()) item { Empty("Noch keine Schichten erfasst") }
            items(shifts, key = { it.shift.id }) { item ->
                val stats = ShiftStatisticsService.calculate(item)
                val deletedCount = item.boxes.count { it.box.status == BoxStatus.CANCELLED }
                Card(
                    Modifier.fillMaxWidth().clickable { vm.showShift(item) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${item.shift.type.label} · ${item.shift.shiftDate}", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("Personalnr. ${item.shift.personnelNumber} · ${item.shift.status}")
                        Text("${stats.boxCount} Kisten · Produktiv ${stats.productiveMillis.asDuration()}", color = DhlRed, fontWeight = FontWeight.Bold)
                        if (deletedCount > 0) Text("$deletedCount gelöschte Kiste(n)", color = DhlDarkRed, fontWeight = FontWeight.Black)
                        TextButton(onClick = {
                            selectedIds = if (item.shift.id in selectedIds) selectedIds - item.shift.id else selectedIds + item.shift.id
                        }, modifier = Modifier.align(Alignment.End)) {
                            Text(if (item.shift.id in selectedIds) "AUSGEWÄHLT ✓" else "FÜR EXPORT AUSWÄHLEN")
                        }
                    }
                }
            }
            if (shifts.isNotEmpty()) item {
                BigButton(if (selectedIds.isEmpty()) "ALLE SCHICHTEN EXPORTIEREN" else "${selectedIds.size} SCHICHT(EN) EXPORTIEREN", {
                    exportIds = selectedIds.takeIf { it.isNotEmpty() }; preview = true
                }, Modifier.fillMaxWidth().height(62.dp))
            }
        }
    }
    if (preview) ExportPreviewDialog(shifts.filter { exportIds == null || it.shift.id in exportIds!! }, { preview = false }) {
        preview = false; launcher.launch("AV-Alle-Schichten_Export_${LocalDate.now()}.csv")
    }
}

@Composable
private fun ShiftReportScreen(item: ShiftWithData, vm: MainViewModel) {
    val allShifts by vm.shifts.collectAsState()
    val stats = ShiftStatisticsService.calculate(item)
    val validUntil by vm.teamLeaderValidUntil.collectAsState()
    val now by vm.clock.collectAsState()
    val teamLeader = now.utcMillis < validUntil
    var preview by remember { mutableStateOf(false) }
    var requestKey by remember { mutableStateOf(false) }
    var editProcess by remember { mutableStateOf<WorkProcessEntity?>(null) }
    var addProcess by remember { mutableStateOf(false) }
    var cancelProcess by remember { mutableStateOf<WorkProcessEntity?>(null) }
    var deleteShiftBox by remember { mutableStateOf<BoxWithInterruptions?>(null) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val csv = vm.shiftCsv(setOf(item.shift.id), ShiftExportType.BOTH)
            vm.getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(csv) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        AppBar("SCHICHTBERICHT") { vm.closeShift() }
        LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            item { Text("${item.shift.type.label} · ${item.shift.shiftDate}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = DhlRed) }
            item { Stat("Personalnummer", item.shift.personnelNumber) }
            item { Stat("Schichtstatus", item.shift.status.toString()) }
            item { Stat("Schichtfenster", "${date(item.shift.scheduledStartAtUtc)} ${time(item.shift.scheduledStartAtUtc)} – ${date(item.shift.scheduledEndAtUtc)} ${time(item.shift.scheduledEndAtUtc)}") }
            item { HorizontalDivider() }
            item { Stat("Kisten", stats.boxCount.toString(), strong = true) }
            item { Stat("Kisten-Nettozeit", stats.boxNetMillis.asDuration()) }
            item { Stat("Ø je Kiste", stats.averageBoxMillis?.asDuration() ?: "–") }
            item { Stat("Abweichung zum Ziel 20:00", stats.deviationPerBoxMillis?.let { signedDuration(it) } ?: "–") }
            item { Stat("Registrierung", stats.registrationMillis.asDuration()) }
            item { Stat("Image", stats.imageMillis.asDuration()) }
            item { Stat("Diverse", stats.otherMillis.asDuration()) }
            item { Stat("Kistenwechsel", stats.boxChangeMillis.asDuration()) }
            item { Stat("Vorbereitung", stats.preparationMillis.asDuration()) }
            item { Stat("Aufräumen", stats.cleanupMillis.asDuration()) }
            item { Stat("Pause", stats.breakMillis.asDuration()) }
            item { Stat("Produktive Zeit", stats.productiveMillis.asDuration(), strong = true) }
            if (stats.unclassifiedMillis > 0) item { Stat("Nicht klassifiziert", stats.unclassifiedMillis.asDuration(), strong = true) }
            item {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Chronologie", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (teamLeader) Text("Teamleiter-Modus: Kisten können hier direkt bearbeitet oder vollständig gelöscht werden.", color = DhlDarkRed, fontWeight = FontWeight.Bold)
                }
            }
            items(item.boxes.sortedBy { it.box.startedAtUtc }, key = { "b${it.box.id}" }) { box ->
                val deleted = box.box.status == BoxStatus.CANCELLED
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (deleted) Color(0xFFFFCDD2) else Color.White),
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Text("Kiste ${box.box.displayNumber} · ${box.box.type.label}", fontWeight = FontWeight.Bold)
                        Text("${time(box.box.startedAtUtc)}–${box.box.endedAtUtc?.let(::time) ?: "läuft"} · Netto ${box.netMillis().asDuration()}")
                        if (deleted) Text("GELÖSCHT", color = DhlDarkRed, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { vm.showDetail(box) }) { Text(if (teamLeader) "BEARBEITEN" else "ÖFFNEN") }
                            if (teamLeader && box.box.endedAtUtc != null && !deleted) {
                                TextButton(onClick = { deleteShiftBox = box }) { Text("GESAMTE KISTE LÖSCHEN", color = DhlDarkRed) }
                            }
                        }
                    }
                }
            }
            items(item.processes.sortedBy { it.startedAtUtc }, key = { "p${it.id}" }) { process ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(11.dp)) {
                        Text(process.type.label, fontWeight = FontWeight.Bold)
                        Text("${time(process.startedAtUtc)}–${process.endedAtUtc?.let(::time) ?: "läuft"} · ${process.grossMillis().asDuration()}")
                        process.note?.let { Text(it) }
                        if (teamLeader) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editProcess = process }) { Text("ÄNDERN") }
                            TextButton(onClick = { cancelProcess = process }) { Text("LÖSCHEN", color = DhlDarkRed) }
                        }
                    }
                }
            }
            item {
                if (teamLeader) BigButton("PROZESS MANUELL HINZUFÜGEN", { addProcess = true }, Modifier.fillMaxWidth().height(58.dp), filled = false)
                else BigButton("TEAMLEITER-BEARBEITUNG", { requestKey = true }, Modifier.fillMaxWidth().height(58.dp), filled = false)
            }
            item { BigButton("SCHICHT ALS CSV EXPORTIEREN", { preview = true }, Modifier.fillMaxWidth().height(62.dp)) }
        }
    }
    if (preview) ExportPreviewDialog(listOf(item), { preview = false }) {
        preview = false; launcher.launch("AV-${item.shift.type.label}_${item.shift.shiftDate}.csv")
    }
    if (requestKey) TeamLeaderKeyDialog({ requestKey = false }) { key -> vm.unlockTeamLeader(key).also { if (it == null) requestKey = false } }
    if (addProcess) ManualProcessDialog(null, item.shift.id, allShifts, { addProcess = false }) { shiftId, type, start, end, note, previous, next, reason ->
        addProcess = false
        val target = allShifts.firstOrNull { it.shift.id == shiftId } ?: item
        vm.addManualProcess(target, type, start, end, note, previous, next, reason)
    }
    editProcess?.let { process -> ManualProcessDialog(process, process.shiftId, allShifts, { editProcess = null }) { shiftId, type, start, end, note, previous, next, reason ->
        editProcess = null; vm.updateManualProcess(process, shiftId, type, start, end, note, previous, next, reason)
    } }
    cancelProcess?.let { process -> ReasonDialog("Prozess wirklich löschen?", { cancelProcess = null }) { reason ->
        cancelProcess = null; vm.cancelManualProcess(process, reason)
    } }
    deleteShiftBox?.let { box -> ReasonDialog(
        "Gesamte Kiste ${box.box.displayNumber} löschen?",
        { deleteShiftBox = null },
    ) { reason ->
        deleteShiftBox = null
        vm.deleteBoxAsTeamLeader(box, reason)
    } }
}

@Composable
private fun ExportPreviewDialog(items: List<ShiftWithData>, close: () -> Unit, export: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("CSV-Exportvorschau") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Exportumfang: ${items.size} Schicht(en)")
                Text("Kisten: ${items.sumOf { ShiftStatisticsService.calculate(it).boxCount }}")
                Text("Kistenwechsel: ${items.sumOf { ShiftStatisticsService.calculate(it).boxChangeCount }}")
                Text("Registrierungen: ${items.sumOf { it.processes.count { p -> p.type == ProcessType.REGISTRATION } }}")
                Text("Exportart: Detaildaten und Zusammenfassung")
                Text("Format: CSV, Semikolon, UTF-8")
            }
        },
        confirmButton = { TextButton(onClick = export) { Text("CSV EXPORTIEREN") } },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

private fun signedDuration(value: Long): String = (if (value >= 0) "+" else "−") + kotlin.math.abs(value).asDuration()

@Composable
private fun DetailScreen(item: BoxWithInterruptions, vm: MainViewModel, back: () -> Unit) {
    val shifts by vm.shifts.collectAsState()
    val teamLeaderValidUntil by vm.teamLeaderValidUntil.collectAsState()
    val detailClock by vm.clock.collectAsState()
    val teamLeaderActive = detailClock.utcMillis < teamLeaderValidUntil
    var requestKey by remember { mutableStateOf(false) }
    var editBox by remember { mutableStateOf(false) }
    var addInterruption by remember { mutableStateOf(false) }
    var editInterruption by remember { mutableStateOf<InterruptionEntity?>(null) }
    var deleteInterruption by remember { mutableStateOf<InterruptionEntity?>(null) }
    var reassign by remember { mutableStateOf(false) }
    var deleteBox by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        AppBar("KISTE ${item.box.displayNumber}", back)
        LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (item.box.status == BoxStatus.CANCELLED) item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2)), modifier = Modifier.fillMaxWidth()) {
                    Text("GELÖSCHTE KISTE – wird in Statistiken nicht mitgezählt", modifier = Modifier.padding(14.dp), color = DhlDarkRed, fontWeight = FontWeight.Black)
                }
            }
            item { Stat("Kistenart", item.box.type.label, strong = true) }
            item { Stat("Personalnummer", item.box.employeeNumber.ifBlank { "–" }) }
            item { Stat("Start", "${date(item.box.startedAtUtc)} ${time(item.box.startedAtUtc)}") }
            item { Stat("Ende", item.box.endedAtUtc?.let { time(it) } ?: "läuft") }
            item { Stat("Bruttozeit", item.grossMillis().asDuration()) }
            item { Text("Unterbrechungen", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
            items(item.interruptions.sortedBy { it.startedAtUtc }, key = { it.id }) { interruption ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(interruption.type.label, fontWeight = FontWeight.Bold)
                        interruption.optionalNote?.let { Text("Hinweis: $it", color = DhlDarkRed) }
                        Text("${time(interruption.startedAtUtc)}–${interruption.endedAtUtc?.let(::time) ?: "läuft"}")
                        Text("Dauer: ${interruption.durationMillis().asDuration()}")
                        if (teamLeaderActive && item.box.status != BoxStatus.CANCELLED) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editInterruption = interruption }) { Text("ÄNDERN") }
                                TextButton(onClick = { deleteInterruption = interruption }) {
                                    Text("LÖSCHEN", color = DhlDarkRed)
                                }
                            }
                        }
                    }
                }
            }
            item { Stat("Unterbrechungen gesamt", item.interruptionMillis().asDuration()) }
            item { Stat("Netto-Bearbeitungszeit", item.netMillis().asDuration(), strong = true) }
            if (item.box.manualEditHistory.isNotBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3B0)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Manuelle Änderungen", fontWeight = FontWeight.Bold, color = DhlDarkRed)
                            Text(item.box.manualEditHistory, fontSize = 14.sp)
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                if (teamLeaderActive && item.box.status != BoxStatus.CANCELLED) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3B0)), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Teamleiter-Modus aktiv bis ${time(teamLeaderValidUntil)} Uhr",
                            modifier = Modifier.padding(12.dp),
                            fontWeight = FontWeight.Bold,
                            color = DhlDarkRed,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    BigButton(
                        "KISTENDATEN BEARBEITEN", { editBox = true },
                        Modifier.fillMaxWidth().height(62.dp), filled = false,
                    )
                    Spacer(Modifier.height(9.dp))
                    BigButton(
                        "SCHICHTZUORDNUNG ÄNDERN", { reassign = true },
                        Modifier.fillMaxWidth().height(62.dp), filled = false,
                    )
                    Spacer(Modifier.height(9.dp))
                    BigButton(
                        "UNTERBRECHUNG HINZUFÜGEN", { addInterruption = true },
                        Modifier.fillMaxWidth().height(62.dp),
                    )
                    Spacer(Modifier.height(9.dp))
                    BigButton(
                        "GESAMTE KISTE LÖSCHEN", { deleteBox = true },
                        Modifier.fillMaxWidth().height(62.dp), danger = true,
                    )
                } else if (item.box.status != BoxStatus.CANCELLED) {
                    BigButton(
                        "TEAMLEITER-MODUS FREISCHALTEN", { requestKey = true },
                        Modifier.fillMaxWidth().height(64.dp), filled = false,
                    )
                }
            }
        }
    }
    if (requestKey) TeamLeaderKeyDialog(
        close = { requestKey = false },
        unlock = { key ->
            vm.unlockTeamLeader(key).also { error ->
                if (error == null) {
                    requestKey = false
                }
            }
        },
    )
    if (editBox) EditBoxDialog(
        item = item,
        close = { editBox = false },
        save = { type, employee, start, end, reason ->
            editBox = false
            vm.editFinishedBox(item, type, employee, start, end, reason)
        },
    )
    if (reassign) ReassignBoxDialog(item, shifts, { reassign = false }) { shiftId, reason ->
        reassign = false; vm.reassignBox(item, shiftId, reason)
    }
    if (deleteBox) ReasonDialog(
        "Gesamte Kiste ${item.box.displayNumber} löschen?",
        { deleteBox = false },
    ) { reason ->
        deleteBox = false
        vm.deleteBoxAsTeamLeader(item, reason)
    }
    if (addInterruption) EditInterruptionDialog(
        item = item,
        interruption = null,
        close = { addInterruption = false },
        save = { type, start, end, note, reason ->
            addInterruption = false
            vm.addManualInterruption(item, type, start, end, note, reason)
        },
    )
    editInterruption?.let { interruption ->
        EditInterruptionDialog(
            item = item,
            interruption = interruption,
            close = { editInterruption = null },
            save = { type, start, end, note, reason ->
                editInterruption = null
                vm.updateManualInterruption(item, interruption, type, start, end, note, reason)
            },
        )
    }
    deleteInterruption?.let { interruption ->
        DeleteInterruptionDialog(
            interruption = interruption,
            close = { deleteInterruption = null },
            delete = { reason ->
                deleteInterruption = null
                vm.deleteManualInterruption(item, interruption, reason)
            },
        )
    }
}

@Composable
private fun TeamLeaderKeyDialog(close: () -> Unit, unlock: (String) -> String?) {
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Teamleiter-Freigabe", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Geben Sie einen gültigen Teamleiter-Schlüssel ein. Er ist ab Erstellung neun Stunden gültig.")
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.uppercase().filterNot(Char::isWhitespace); error = null },
                    label = { Text("Teamleiter-Schlüssel") },
                    placeholder = { Text("TL1-…-…") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { error = unlock(key) }, enabled = key.isNotBlank()) { Text("FREISCHALTEN") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun ReasonDialog(title: String, close: () -> Unit, confirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(title) },
        text = { OutlinedTextField(reason, { reason = it }, label = { Text("Änderungsgrund") }, minLines = 2) },
        confirmButton = { TextButton(enabled = reason.isNotBlank(), onClick = { confirm(reason) }) { Text("BESTÄTIGEN") } },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun ManualProcessDialog(
    process: WorkProcessEntity?,
    initialShiftId: Long,
    shifts: List<ShiftWithData>,
    close: () -> Unit,
    save: (Long, ProcessType, Long, Long, String?, Long?, Long?, String) -> Unit,
) {
    val now = System.currentTimeMillis()
    var type by remember(process?.id) { mutableStateOf(process?.type ?: ProcessType.REGISTRATION) }
    var shiftId by remember(process?.id) { mutableStateOf(initialShiftId) }
    var start by remember(process?.id) { mutableStateOf(editDateTimeFormatter.format((process?.startedAtUtc ?: now).asInstant())) }
    var end by remember(process?.id) { mutableStateOf(editDateTimeFormatter.format((process?.endedAtUtc ?: (now + 60_000)).asInstant())) }
    var note by remember(process?.id) { mutableStateOf(process?.note.orEmpty()) }
    var previous by remember(process?.id) { mutableStateOf(process?.previousBoxId?.toString().orEmpty()) }
    var next by remember(process?.id) { mutableStateOf(process?.nextBoxId?.toString().orEmpty()) }
    var reason by remember(process?.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (process == null) "Prozess hinzufügen" else "Prozess vollständig ändern") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item { Text("Prozessart", fontWeight = FontWeight.Bold) }
                items(ProcessType.entries) { choice ->
                    BigButton(choice.label, { type = choice }, Modifier.fillMaxWidth().height(46.dp), filled = type == choice)
                }
                item { Text("Schichtzuordnung", fontWeight = FontWeight.Bold) }
                items(shifts) { shift ->
                    BigButton("${shift.shift.type.label} · ${shift.shift.shiftDate}", { shiftId = shift.shift.id }, Modifier.fillMaxWidth().height(46.dp), filled = shiftId == shift.shift.id)
                }
                item { OutlinedTextField(start, { start = it }, label = { Text("Start (TT.MM.JJJJ HH:MM:SS)") }) }
                item { OutlinedTextField(end, { end = it }, label = { Text("Ende (TT.MM.JJJJ HH:MM:SS)") }) }
                item { OutlinedTextField(note, { note = it.take(100) }, label = { Text(if (type == ProcessType.OTHER) "Hinweis (Pflicht)" else "Hinweis") }) }
                if (type == ProcessType.BOX_CHANGE) {
                    item { OutlinedTextField(previous, { previous = it.filter(Char::isDigit) }, label = { Text("Vorherige Kisten-Datenbank-ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                    item { OutlinedTextField(next, { next = it.filter(Char::isDigit) }, label = { Text("Nächste Kisten-Datenbank-ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                }
                item { OutlinedTextField(reason, { reason = it }, label = { Text("Änderungsgrund") }, minLines = 2) }
                error?.let { item { Text(it, color = DhlDarkRed) } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val startMs = parseEditDateTime(start)
                val endMs = parseEditDateTime(end)
                error = when {
                    startMs == null || endMs == null -> "Bitte Datum und Uhrzeit im angegebenen Format eingeben."
                    endMs <= startMs -> "Das Ende muss nach dem Start liegen."
                    reason.isBlank() -> "Bitte einen Änderungsgrund eingeben."
                    type == ProcessType.OTHER && note.isBlank() -> "Diverse benötigt einen Hinweis."
                    else -> null
                }
                if (error == null) save(shiftId, type, startMs!!, endMs!!, note.ifBlank { null }, previous.toLongOrNull(), next.toLongOrNull(), reason)
            }) { Text("SPEICHERN") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun ReassignBoxDialog(
    item: BoxWithInterruptions,
    shifts: List<ShiftWithData>,
    close: () -> Unit,
    save: (Long, String) -> Unit,
) {
    var selected by remember { mutableStateOf(item.box.shiftId) }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Schichtzuordnung ändern") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(shifts) { shift ->
                    BigButton("${shift.shift.type.label} · ${shift.shift.shiftDate}", { selected = shift.shift.id }, Modifier.fillMaxWidth().height(48.dp), filled = selected == shift.shift.id)
                }
                item { OutlinedTextField(reason, { reason = it }, label = { Text("Änderungsgrund") }, minLines = 2) }
            }
        },
        confirmButton = { TextButton(enabled = selected != null && reason.isNotBlank(), onClick = { save(selected!!, reason) }) { Text("SPEICHERN") } },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun EditBoxDialog(
    item: BoxWithInterruptions,
    close: () -> Unit,
    save: (BoxType, String, Long, Long, String) -> Unit,
) {
    var type by remember(item.box.id) { mutableStateOf(item.box.type) }
    var employee by remember(item.box.id) { mutableStateOf(item.box.employeeNumber) }
    var start by remember(item.box.id) { mutableStateOf(editDateTimeFormatter.format(item.box.startedAtUtc.asInstant())) }
    var end by remember(item.box.id) { mutableStateOf(editDateTimeFormatter.format(item.box.endedAtUtc!!.asInstant())) }
    var reason by remember(item.box.id) { mutableStateOf("") }
    var chooseType by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (chooseType) {
        TypeSelectionDialog(close = { chooseType = false }, select = { type = it; chooseType = false })
        return
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Kiste manuell bearbeiten", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Kistenart", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { chooseType = true }) { Text("${type.label.uppercase()} ÄNDERN") }
                }
                item {
                    OutlinedTextField(
                        value = employee,
                        onValueChange = { employee = it.filter(Char::isDigit); error = null },
                        label = { Text("Personalnummer") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it; error = null },
                        label = { Text("Start (TT.MM.JJJJ HH:MM:SS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it; error = null },
                        label = { Text("Ende (TT.MM.JJJJ HH:MM:SS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(200); error = null },
                        label = { Text("Änderungsgrund (Pflichtfeld)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { error?.let { Text(it, color = DhlDarkRed, fontWeight = FontWeight.Bold) } }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedStart = parseEditDateTime(start)
                    val parsedEnd = parseEditDateTime(end)
                    error = when {
                        employee.isBlank() -> "Personalnummer fehlt."
                        parsedStart == null || parsedEnd == null -> "Datum/Uhrzeit ist ungültig."
                        parsedEnd <= parsedStart -> "Das Ende muss nach dem Start liegen."
                        item.interruptions.any {
                            it.startedAtUtc < parsedStart || (it.endedAtUtc ?: Long.MAX_VALUE) > parsedEnd
                        } -> "Eine Unterbrechung liegt außerhalb der neuen Kistenlaufzeit. Bitte zuerst ändern oder löschen."
                        reason.isBlank() -> "Änderungsgrund fehlt."
                        else -> null
                    }
                    if (error == null) save(type, employee, parsedStart!!, parsedEnd!!, reason.trim())
                },
            ) { Text("ÄNDERUNGEN SPEICHERN") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun EditInterruptionDialog(
    item: BoxWithInterruptions,
    interruption: InterruptionEntity?,
    close: () -> Unit,
    save: (InterruptionType, Long, Long, String?, String) -> Unit,
) {
    val defaultStart = interruption?.startedAtUtc ?: item.box.startedAtUtc
    val defaultEnd = interruption?.endedAtUtc
        ?: minOf(defaultStart + 5L * 60L * 1_000L, item.box.endedAtUtc!!)
    var type by remember(interruption?.id) { mutableStateOf(interruption?.type ?: InterruptionType.PAUSE) }
    var start by remember(interruption?.id) { mutableStateOf(editDateTimeFormatter.format(defaultStart.asInstant())) }
    var end by remember(interruption?.id) { mutableStateOf(editDateTimeFormatter.format(defaultEnd.asInstant())) }
    var note by remember(interruption?.id) { mutableStateOf(interruption?.optionalNote.orEmpty()) }
    var reason by remember(interruption?.id) { mutableStateOf("") }
    var chooseType by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (chooseType) {
        InterruptionTypeSelectionDialog(
            close = { chooseType = false },
            select = { type = it; chooseType = false; error = null },
        )
        return
    }
    AlertDialog(
        onDismissRequest = close,
        title = {
            Text(
                if (interruption == null) "Unterbrechung hinzufügen" else "Unterbrechung bearbeiten",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Kategorie", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { chooseType = true }) { Text("${type.label.uppercase()} ÄNDERN") }
                }
                item {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it; error = null },
                        label = { Text("Start (TT.MM.JJJJ HH:MM:SS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it; error = null },
                        label = { Text("Ende (TT.MM.JJJJ HH:MM:SS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == InterruptionType.MISC) {
                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(100); error = null },
                            label = { Text("Kurze Information zu Diverse") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(200); error = null },
                        label = { Text("Änderungsgrund (Pflichtfeld)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { error?.let { Text(it, color = DhlDarkRed, fontWeight = FontWeight.Bold) } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedStart = parseEditDateTime(start)
                val parsedEnd = parseEditDateTime(end)
                error = when {
                    parsedStart == null || parsedEnd == null -> "Datum/Uhrzeit ist ungültig."
                    type == InterruptionType.MISC && note.isBlank() -> "Diverse benötigt eine kurze Information."
                    reason.isBlank() -> "Änderungsgrund fehlt."
                    else -> validateManualInterruption(
                        item.box,
                        item.interruptions,
                        parsedStart,
                        parsedEnd,
                        interruption?.id,
                    )
                }
                if (error == null) {
                    save(type, parsedStart!!, parsedEnd!!, note.trim().ifBlank { null }, reason.trim())
                }
            }) { Text(if (interruption == null) "HINZUFÜGEN" else "SPEICHERN") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun InterruptionTypeSelectionDialog(
    close: () -> Unit,
    select: (InterruptionType) -> Unit,
) = AlertDialog(
    onDismissRequest = close,
    title = { Text("Welche Unterbrechung?", fontWeight = FontWeight.Bold) },
    text = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InterruptionType.entries.forEach { type ->
                BigButton(
                    type.label.uppercase(), { select(type) },
                    Modifier.fillMaxWidth().height(52.dp), filled = false,
                )
            }
        }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
)

@Composable
private fun DeleteInterruptionDialog(
    interruption: InterruptionEntity,
    close: () -> Unit,
    delete: (String) -> Unit,
) {
    var reason by remember(interruption.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Unterbrechung löschen?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${interruption.type.label}, ${time(interruption.startedAtUtc)}–${time(interruption.endedAtUtc!!)} " +
                        "(${interruption.durationMillis().asDuration()})",
                )
                Text("Die Löschung verändert die Nettozeit der Kiste.", color = DhlDarkRed)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(200) },
                    label = { Text("Löschgrund (Pflichtfeld)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { delete(reason.trim()) }, enabled = reason.isNotBlank()) {
                Text("ENDGÜLTIG LÖSCHEN", color = DhlDarkRed)
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun TypeSelectionDialog(close: () -> Unit, select: (BoxType) -> Unit) = AlertDialog(
    onDismissRequest = close,
    title = { Text("Welche Kistenart?", fontWeight = FontWeight.Bold) },
    text = {
        LazyColumn(Modifier.fillMaxWidth().height(330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(BoxType.entries) { type ->
                BigButton(type.label.uppercase(), { select(type) }, Modifier.fillMaxWidth().height(49.dp), filled = false)
            }
        }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
)

@Composable
private fun EmployeeNumberDialog(type: BoxType, close: () -> Unit, start: (String) -> Unit) {
    var number by remember(type) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Personalnummer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kistenart: ${type.label}")
                OutlinedTextField(
                    value = number,
                    onValueChange = { input -> number = input.filter(Char::isDigit) },
                    label = { Text("Personalnummer") },
                    placeholder = { Text("Nur Zahlen") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { start(number) }, enabled = number.isNotBlank()) { Text("KISTE STARTEN") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun MiscNoteDialog(close: () -> Unit, start: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Diverse – kurze Information", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(100) },
                    label = { Text("Was ist der Grund?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${note.length}/100 Zeichen", fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
            }
        },
        confirmButton = {
            TextButton(onClick = { start(note.trim()) }, enabled = note.isNotBlank()) { Text("UNTERBRECHUNG STARTEN") }
        },
        dismissButton = { TextButton(onClick = close) { Text("ABBRECHEN") } },
    )
}

@Composable
private fun RecoveryDialog(item: BoxWithInterruptions, vm: MainViewModel) {
    var deleteConfirm by remember { mutableStateOf(false) }
    if (deleteConfirm) AlertDialog(
        onDismissRequest = { deleteConfirm = false },
        title = { Text("Fehleintrag löschen?") },
        text = { Text("Die Kiste und alle Unterbrechungen werden dauerhaft gelöscht.") },
        confirmButton = { TextButton(onClick = vm::deleteMistake) { Text("LÖSCHEN", color = Color.Red) } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("ABBRECHEN") } },
    ) else AlertDialog(
        onDismissRequest = {},
        title = { Text("Laufende Kiste gefunden") },
        text = { Text("${item.box.type.label} ${item.box.displayNumber} (Personalnr. ${item.box.employeeNumber.ifBlank { "–" }}) läuft seit ${time(item.box.startedAtUtc)} Uhr.") },
        confirmButton = { TextButton(onClick = vm::dismissRecovery) { Text("WEITERFÜHREN") } },
        dismissButton = { Column {
            TextButton(onClick = { vm.finish(); vm.dismissRecovery() }) { Text("KISTE JETZT BEENDEN") }
            TextButton(onClick = { deleteConfirm = true }) { Text("FEHLEINTRAG KORRIGIEREN", color = Color.Red) }
        } },
    )
}

@Composable
private fun FinishDialog(item: BoxWithInterruptions, clock: ClockSnapshot, cancel: () -> Unit, finish: () -> Unit) = AlertDialog(
    onDismissRequest = cancel,
    title = { Text("Kiste wirklich beenden?") },
    text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Stat("Kistenart", item.box.type.label)
        Stat("Personalnummer", item.box.employeeNumber.ifBlank { "–" })
        Stat("Start", time(item.box.startedAtUtc)); Stat("Ende", time(clock.utcMillis))
        Stat("Bruttozeit", item.grossMillis(clock).asDuration())
        Stat("Unterbrechungen", item.interruptionMillis(clock).asDuration())
        Stat("Nettozeit", item.netMillis(clock).asDuration(), true)
        if (item.interruptions.any { it.endedAtUtc == null }) Text("Die aktive Unterbrechung wird ebenfalls beendet.", color = DhlDarkRed)
    } },
    confirmButton = { TextButton(onClick = finish) { Text("BEENDEN") } },
    dismissButton = { TextButton(onClick = cancel) { Text("ABBRECHEN") } },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(title: String, back: () -> Unit) = TopAppBar(
    title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp) },
    navigationIcon = { TextButton(onClick = back) { Text("‹ ZURÜCK", color = Color.White) } },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = DhlRed, titleContentColor = Color.White),
)

@Composable
private fun Stat(label: String, value: String, strong: Boolean = false) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, fontSize = if (strong) 18.sp else 16.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    Text(value, fontSize = if (strong) 18.sp else 16.sp, fontWeight = FontWeight.Bold, color = if (strong) DhlRed else Color.Unspecified)
}

@Composable
private fun BigButton(label: String, click: () -> Unit, modifier: Modifier, filled: Boolean = true, danger: Boolean = false, enabled: Boolean = true) {
    Button(
        onClick = click,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when { danger -> DhlDarkRed; filled -> DhlRed; else -> Color.White },
            contentColor = if (filled || danger) Color.White else DhlRed,
        ),
        elevation = ButtonDefaults.buttonElevation(3.dp),
    ) { Text(label, fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center) }
}

@Composable private fun Empty(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, textAlign = TextAlign.Center) }
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())
private val editDateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss")
    .withResolverStyle(ResolverStyle.STRICT).withZone(ZoneId.systemDefault())
private fun time(millis: Long) = timeFormatter.format(millis.asInstant())
private fun date(millis: Long) = dateFormatter.format(millis.asInstant())
private fun parseEditDateTime(value: String): Long? = runCatching {
    LocalDateTime.parse(value.trim(), editDateTimeFormatter)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()
