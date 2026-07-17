package de.postkisten.tracker.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ShiftExportType { DETAILS, SUMMARY, BOTH }

object CsvExportService {
    private val zone = ZoneId.of("Europe/Berlin")
    private val date = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(zone)
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)

    fun create(shifts: List<ShiftWithData>, type: ShiftExportType): String = buildString {
        append('\uFEFF')
        if (type != ShiftExportType.SUMMARY) append(details(shifts))
        if (type == ShiftExportType.BOTH) append("\r\n\r\nSCHICHTZUSAMMENFASSUNG\r\n")
        if (type != ShiftExportType.DETAILS) append(summary(shifts))
    }

    fun details(shifts: List<ShiftWithData>): String = csv(buildList {
        add(listOf("Schicht-ID", "Schichtdatum", "Schichtart", "Geplanter Schichtbeginn", "Geplantes Schichtende", "Schichtstatus", "Prozess-ID", "Prozessart", "Kisten-ID", "Alte Kisten-ID", "Kistenart", "Vorherige Kisten-ID", "Nächste Kisten-ID", "Personalnummer", "Startdatum", "Startzeit", "Enddatum", "Endzeit", "Bruttozeit", "Nettozeit", "Pausenzeit", "Registrierungszeit", "Image-Zeit", "Diverse-Zeit", "Hinweis", "Manuell geändert", "Änderungsprotokoll"))
        shifts.forEach { data ->
            val shift = data.shift
            data.boxes.sortedBy { it.box.startedAtUtc }.forEach { item ->
                val b = item.box
                fun interruption(type: InterruptionType) = item.interruptions.filter { it.type == type }.sumOf { it.durationMillis() }.asDuration()
                add(common(shift) + listOf("", "Kistenbearbeitung", b.displayNumber, b.legacyBoxId.orEmpty(), b.type.label, "", "", b.employeeNumber) + interval(b.startedAtUtc, b.endedAtUtc) + listOf(item.grossMillis().asDuration(), item.netMillis().asDuration(), interruption(InterruptionType.PAUSE), interruption(InterruptionType.REGISTRATION), interruption(InterruptionType.IMAGE), interruption(InterruptionType.MISC), "", (b.manualEditedAtUtc != null).toString(), b.manualEditHistory))
            }
            data.processes.sortedBy { it.startedAtUtc }.forEach { p ->
                val duration = p.netMillis(data.processes).asDuration()
                add(common(shift) + listOf(p.id.toString(), p.type.label, "", "", "", p.previousBoxId?.toString().orEmpty(), p.nextBoxId?.toString().orEmpty(), p.personnelNumber) + interval(p.startedAtUtc, p.endedAtUtc) + listOf(p.grossMillis().asDuration(), duration, if (p.type == ProcessType.BREAK) duration else "", if (p.type == ProcessType.REGISTRATION) duration else "", if (p.type == ProcessType.IMAGE) duration else "", if (p.type == ProcessType.OTHER) duration else "", p.note.orEmpty(), p.manuallyModified.toString(), p.changeLog))
            }
        }
    })

    fun summary(shifts: List<ShiftWithData>): String = csv(buildList {
        add(listOf("Schicht-ID", "Schichtdatum", "Schichtart", "Geplanter Beginn", "Geplantes Ende", "Tatsächlicher erster Vorgang", "Tatsächlicher letzter Vorgang", "Schichtstatus", "Personalnummer", "Anzahl Kisten", "Tagespost", "Sachbearbeitung", "Rückläufer", "Routing", "Ablage", "HR-Akte", "Kisten-Bruttozeit", "Kisten-Nettozeit", "Durchschnitt je Kiste", "Schnellste Kiste", "Langsamste Kiste", "Sollzeit je Kiste", "Abweichung je Kiste", "Abweichung Prozent", "Anzahl Kistenwechsel", "Kistenwechselzeit gesamt", "Durchschnitt je Kistenwechsel", "Schnellster Kistenwechsel", "Längster Kistenwechsel", "Schichtvorbereitung", "Schichtabschlusszeit", "Registrierungszeit", "Image-Zeit", "Diverse-Zeit", "Pausenzeit", "Produktive Gesamtzeit", "Nicht klassifizierte Zeit", "Manuell geändert"))
        shifts.forEach { data ->
            val s = data.shift
            val x = ShiftStatisticsService.calculate(data)
            add(listOf(s.id.toString(), s.shiftDate, s.type.label, stamp(s.scheduledStartAtUtc), stamp(s.scheduledEndAtUtc), s.actualFirstActivityAtUtc?.let(::stamp).orEmpty(), s.actualLastActivityAtUtc?.let(::stamp).orEmpty(), s.status.toString(), s.personnelNumber, x.boxCount.toString()) + BoxType.entries.map { x.boxesByType[it].toString() } + listOf(x.boxGrossMillis.asDuration(), x.boxNetMillis.asDuration(), x.averageBoxMillis?.asDuration().orEmpty(), x.fastestBoxMillis?.asDuration().orEmpty(), x.slowestBoxMillis?.asDuration().orEmpty(), x.targetBoxMillis.asDuration(), x.deviationPerBoxMillis?.let { signed(it) }.orEmpty(), x.deviationPercent?.let { "%.2f".format(java.util.Locale.GERMANY, it) }.orEmpty(), x.boxChangeCount.toString(), x.boxChangeMillis.asDuration(), x.averageBoxChangeMillis?.asDuration().orEmpty(), x.fastestBoxChangeMillis?.asDuration().orEmpty(), x.slowestBoxChangeMillis?.asDuration().orEmpty(), x.preparationMillis.asDuration(), x.cleanupMillis.asDuration(), x.registrationMillis.asDuration(), x.imageMillis.asDuration(), x.otherMillis.asDuration(), x.breakMillis.asDuration(), x.productiveMillis.asDuration(), x.unclassifiedMillis.asDuration(), s.manuallyModified.toString()))
        }
    })

    private fun common(s: ShiftEntity) = listOf(s.id.toString(), s.shiftDate, s.type.label, stamp(s.scheduledStartAtUtc), stamp(s.scheduledEndAtUtc), s.status.toString())
    private fun interval(start: Long, end: Long?) = listOf(date.format(Instant.ofEpochMilli(start)), time.format(Instant.ofEpochMilli(start)), end?.let { date.format(Instant.ofEpochMilli(it)) }.orEmpty(), end?.let { time.format(Instant.ofEpochMilli(it)) }.orEmpty())
    private fun stamp(value: Long) = "${date.format(Instant.ofEpochMilli(value))} ${time.format(Instant.ofEpochMilli(value))}"
    private fun signed(value: Long) = (if (value >= 0) "+" else "-") + kotlin.math.abs(value).asDuration()
    private fun csv(rows: List<List<String>>) = rows.joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(";") { field -> escape(field) } }
    private fun escape(value: String): String = if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value
}
