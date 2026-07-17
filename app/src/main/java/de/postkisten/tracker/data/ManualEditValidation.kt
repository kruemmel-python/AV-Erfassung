package de.postkisten.tracker.data

fun validateManualInterruption(
    box: BoxEntity,
    existing: List<InterruptionEntity>,
    startedAtUtc: Long,
    endedAtUtc: Long,
    excludedInterruptionId: Long? = null,
): String? {
    val boxEnd = box.endedAtUtc ?: return "Die Kiste ist noch nicht abgeschlossen."
    if (endedAtUtc <= startedAtUtc) return "Das Ende der Unterbrechung muss nach dem Start liegen."
    if (startedAtUtc < box.startedAtUtc || endedAtUtc > boxEnd) {
        return "Die Unterbrechung muss vollständig innerhalb der Kistenlaufzeit liegen."
    }
    val overlaps = existing.any { interruption ->
        interruption.id != excludedInterruptionId &&
            startedAtUtc < (interruption.endedAtUtc ?: boxEnd) &&
            endedAtUtc > interruption.startedAtUtc
    }
    return if (overlaps) "Die Unterbrechung überschneidet sich mit einer vorhandenen Unterbrechung." else null
}
