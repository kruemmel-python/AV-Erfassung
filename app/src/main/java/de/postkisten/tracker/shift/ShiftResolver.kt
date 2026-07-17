package de.postkisten.tracker.shift

import de.postkisten.tracker.data.ShiftEntity
import de.postkisten.tracker.data.ShiftType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ShiftWindow(
    val type: ShiftType,
    val shiftDate: LocalDate,
    val scheduledStartAtUtc: Long,
    val scheduledEndAtUtc: Long,
)

class ShiftResolver(val zone: ZoneId = ZoneId.of("Europe/Berlin")) {
    fun getShiftWindow(type: ShiftType, shiftDate: LocalDate): ShiftWindow {
        val monday = shiftDate.dayOfWeek == DayOfWeek.MONDAY
        val startTime = when (type) {
            ShiftType.EARLY -> LocalTime.of(5, 30)
            ShiftType.LATE -> LocalTime.of(13, 30)
            ShiftType.NIGHT -> LocalTime.of(21, 30)
        }
        val endDate = if (type == ShiftType.NIGHT) shiftDate.plusDays(1) else shiftDate
        val endTime = when (type) {
            ShiftType.EARLY -> if (monday) LocalTime.of(14, 0) else LocalTime.of(13, 45)
            ShiftType.LATE -> if (monday) LocalTime.of(22, 0) else LocalTime.of(21, 45)
            ShiftType.NIGHT -> if (monday) LocalTime.of(6, 0) else LocalTime.of(5, 45)
        }
        return ShiftWindow(
            type = type,
            shiftDate = shiftDate,
            scheduledStartAtUtc = shiftDate.atTime(startTime).atZone(zone).toInstant().toEpochMilli(),
            scheduledEndAtUtc = endDate.atTime(endTime).atZone(zone).toInstant().toEpochMilli(),
        )
    }

    fun resolveCandidates(timestampUtc: Long): List<ShiftWindow> {
        val localDate = Instant.ofEpochMilli(timestampUtc).atZone(zone).toLocalDate()
        return listOf(localDate.minusDays(1), localDate)
            .flatMap { date -> ShiftType.entries.map { getShiftWindow(it, date) } }
            .filter { timestampUtc >= it.scheduledStartAtUtc && timestampUtc < it.scheduledEndAtUtc }
            .sortedWith(compareBy<ShiftWindow> { it.scheduledStartAtUtc }.thenBy { it.type.ordinal })
    }

    fun isTimestampInsideShift(timestampUtc: Long, shift: ShiftEntity): Boolean =
        timestampUtc >= shift.scheduledStartAtUtc && timestampUtc < shift.scheduledEndAtUtc
}
