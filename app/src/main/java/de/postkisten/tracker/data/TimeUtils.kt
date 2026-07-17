package de.postkisten.tracker.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ClockSnapshot(val utcMillis: Long, val elapsedMillis: Long, val bootCount: Int)

object DeviceClock {
    fun snapshot(context: Context) = ClockSnapshot(
        utcMillis = System.currentTimeMillis(),
        elapsedMillis = SystemClock.elapsedRealtime(),
        bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0),
    )
}

fun LocalDate.utcRange(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
    atStartOfDay(zone).toInstant().toEpochMilli() to plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

fun BoxWithInterruptions.grossMillis(now: ClockSnapshot? = null): Long {
    val end = box.endedAtUtc
    if (end != null) return (end - box.startedAtUtc).coerceAtLeast(0)
    if (now != null && now.bootCount == box.bootCount) {
        return (now.elapsedMillis - box.startedElapsedRealtime).coerceAtLeast(0)
    }
    return ((now?.utcMillis ?: System.currentTimeMillis()) - box.startedAtUtc).coerceAtLeast(0)
}

fun InterruptionEntity.durationMillis(now: ClockSnapshot? = null): Long {
    val end = endedAtUtc
    if (end != null) return (end - startedAtUtc).coerceAtLeast(0)
    if (now != null && now.bootCount == bootCount) {
        return (now.elapsedMillis - startedElapsedRealtime).coerceAtLeast(0)
    }
    return ((now?.utcMillis ?: System.currentTimeMillis()) - startedAtUtc).coerceAtLeast(0)
}

fun BoxWithInterruptions.interruptionMillis(now: ClockSnapshot? = null) =
    interruptions.sumOf { it.durationMillis(now) }

fun BoxWithInterruptions.netMillis(now: ClockSnapshot? = null) =
    (grossMillis(now) - interruptionMillis(now)).coerceAtLeast(0)

fun Long.asDuration(includeHours: Boolean = true): String {
    val total = (this.coerceAtLeast(0) / 1_000)
    val h = total / 3_600
    val m = (total % 3_600) / 60
    val s = total % 60
    return if (includeHours || h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun Long.asInstant(): Instant = Instant.ofEpochMilli(this)

fun WorkProcessEntity.grossMillis(nowUtc: Long = System.currentTimeMillis()): Long =
    ((endedAtUtc ?: nowUtc) - startedAtUtc).coerceAtLeast(0)

fun WorkProcessEntity.netMillis(
    allProcesses: List<WorkProcessEntity>,
    nowUtc: Long = System.currentTimeMillis(),
): Long {
    legacyAggregateMillis?.let { return it.coerceAtLeast(0) }
    val childMillis = allProcesses
        .filter { it.parentProcessId == id && it.status != ProcessStatus.CANCELLED }
        .sumOf { it.grossMillis(nowUtc) }
    return (grossMillis(nowUtc) - childMillis).coerceAtLeast(0)
}
