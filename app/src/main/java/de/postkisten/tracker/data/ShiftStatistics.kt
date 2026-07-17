package de.postkisten.tracker.data

data class ShiftStatistics(
    val boxCount: Int,
    val boxesByType: Map<BoxType, Int>,
    val boxGrossMillis: Long,
    val boxNetMillis: Long,
    val averageBoxMillis: Long?,
    val fastestBoxMillis: Long?,
    val slowestBoxMillis: Long?,
    val targetBoxMillis: Long,
    val deviationPerBoxMillis: Long?,
    val deviationPercent: Double?,
    val boxChangeCount: Int,
    val boxChangeMillis: Long,
    val averageBoxChangeMillis: Long?,
    val fastestBoxChangeMillis: Long?,
    val slowestBoxChangeMillis: Long?,
    val registrationMillis: Long,
    val imageMillis: Long,
    val otherMillis: Long,
    val breakMillis: Long,
    val preparationMillis: Long,
    val cleanupMillis: Long,
    val productiveMillis: Long,
    val unclassifiedMillis: Long,
)

object ShiftStatisticsService {
    const val DEFAULT_TARGET_BOX_MILLIS = 20L * 60L * 1_000L

    fun calculate(
        data: ShiftWithData,
        nowUtc: Long = System.currentTimeMillis(),
        targetBoxMillis: Long = DEFAULT_TARGET_BOX_MILLIS,
    ): ShiftStatistics {
        val boxes = data.boxes.filter { it.box.countsAsBox && it.box.status != BoxStatus.CANCELLED }
        val processes = data.processes.filter { it.status != ProcessStatus.CANCELLED }
        val boxDurations = boxes.map { it.netMillis(if (it.box.endedAtUtc == null) ClockSnapshot(nowUtc, 0, -1) else null) }
        fun durations(type: ProcessType) = processes.filter { it.type == type }.map { it.netMillis(processes, nowUtc) }
        val boxChanges = durations(ProcessType.BOX_CHANGE)
        val registration = durations(ProcessType.REGISTRATION).sum()
        val image = durations(ProcessType.IMAGE).sum()
        val other = durations(ProcessType.OTHER).sum()
        val breaks = durations(ProcessType.BREAK).sum()
        val preparation = durations(ProcessType.SHIFT_PREPARATION).sum()
        val cleanup = durations(ProcessType.SHIFT_CLEANUP).sum()
        val boxNet = boxDurations.sum()
        val productive = boxNet + boxChanges.sum() + registration + image + other + preparation + cleanup
        val first = (
            boxes.map { it.box.startedAtUtc } + processes.map { it.startedAtUtc }
        ).minOrNull()
        val last = (
            boxes.map { it.box.endedAtUtc ?: nowUtc } + processes.map { it.endedAtUtc ?: nowUtc }
        ).maxOrNull()
        val observed = if (first == null || last == null) 0L else (last - first).coerceAtLeast(0)
        val average = boxDurations.takeIf { it.isNotEmpty() }?.average()?.toLong()
        return ShiftStatistics(
            boxCount = boxes.size,
            boxesByType = BoxType.entries.associateWith { type -> boxes.count { it.box.type == type } },
            boxGrossMillis = boxes.sumOf { it.grossMillis() },
            boxNetMillis = boxNet,
            averageBoxMillis = average,
            fastestBoxMillis = boxDurations.minOrNull(),
            slowestBoxMillis = boxDurations.maxOrNull(),
            targetBoxMillis = targetBoxMillis,
            deviationPerBoxMillis = average?.minus(targetBoxMillis),
            deviationPercent = average?.let { (it - targetBoxMillis).toDouble() / targetBoxMillis * 100.0 },
            boxChangeCount = boxChanges.size,
            boxChangeMillis = boxChanges.sum(),
            averageBoxChangeMillis = boxChanges.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            fastestBoxChangeMillis = boxChanges.minOrNull(),
            slowestBoxChangeMillis = boxChanges.maxOrNull(),
            registrationMillis = registration,
            imageMillis = image,
            otherMillis = other,
            breakMillis = breaks,
            preparationMillis = preparation,
            cleanupMillis = cleanup,
            productiveMillis = productive,
            unclassifiedMillis = (observed - productive - breaks).coerceAtLeast(0),
        )
    }
}
