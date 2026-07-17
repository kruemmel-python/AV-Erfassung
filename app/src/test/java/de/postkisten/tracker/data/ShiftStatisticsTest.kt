package de.postkisten.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftStatisticsTest {
    @Test fun `reference night shift counts only real boxes and preserves legacy registration aggregate`() {
        val shift = ShiftEntity(
            id = 1,
            type = ShiftType.NIGHT,
            shiftDate = "2026-07-16",
            scheduledStartAtUtc = 0,
            scheduledEndAtUtc = 30_000_000,
            status = ShiftStatus.COMPLETED,
            personnelNumber = "7262",
            createdAtUtc = 0,
            updatedAtUtc = 0,
        )
        val durations = List(13) { 1_020_000L } + 1_113_000L
        val boxes = durations.mapIndexed { index, duration ->
            BoxWithInterruptions(
                BoxEntity(
                    id = (index + 1).toLong(),
                    displayNumber = "N-2026-07-16-${index + 1}",
                    type = BoxType.DAILY_MAIL,
                    employeeNumber = "7262",
                    startedAtUtc = index * 1_200_000L,
                    startedElapsedRealtime = 0,
                    bootCount = 0,
                    endedAtUtc = index * 1_200_000L + duration,
                    status = BoxStatus.FINISHED,
                    createdAtUtc = 0,
                    updatedAtUtc = 0,
                    shiftId = 1,
                ),
                emptyList(),
            )
        } + BoxWithInterruptions(
            BoxEntity(
                id = 99,
                displayNumber = "2026-07-16-003",
                type = BoxType.DAILY_MAIL,
                employeeNumber = "7262",
                startedAtUtc = 0,
                startedElapsedRealtime = 0,
                bootCount = 0,
                endedAtUtc = 1,
                status = BoxStatus.FINISHED,
                createdAtUtc = 0,
                updatedAtUtc = 0,
                shiftId = 1,
                countsAsBox = false,
            ),
            emptyList(),
        )
        val processes = listOf(
            WorkProcessEntity(
                id = 1,
                type = ProcessType.REGISTRATION,
                shiftId = 1,
                personnelNumber = "7262",
                startedAtUtc = 0,
                endedAtUtc = 11_000_000,
                status = ProcessStatus.MANUALLY_CORRECTED,
                legacyAggregateMillis = 9_850_000,
                createdAtUtc = 0,
                updatedAtUtc = 0,
            ),
            WorkProcessEntity(
                id = 2,
                type = ProcessType.BREAK,
                shiftId = 1,
                personnelNumber = "7262",
                startedAtUtc = 11_000_000,
                endedAtUtc = 12_410_000,
                status = ProcessStatus.COMPLETED,
                createdAtUtc = 0,
                updatedAtUtc = 0,
            ),
        )
        val stats = ShiftStatisticsService.calculate(ShiftWithData(shift, boxes, processes), nowUtc = 30_000_000)
        assertEquals(14, stats.boxCount)
        assertEquals(14_373_000L, stats.boxNetMillis)
        assertTrue(kotlin.math.abs(stats.averageBoxMillis!! - 1_026_643L) <= 1L)
        assertEquals(9_850_000L, stats.registrationMillis)
        assertEquals(1_410_000L, stats.breakMillis)
        assertEquals(24_223_000L, stats.productiveMillis)
        assertTrue(stats.deviationPercent!! < -14.0 && stats.deviationPercent > -15.0)
    }

    @Test fun `box change is productive but does not change box average`() {
        val shift = ShiftEntity(1, ShiftType.EARLY, "2026-07-17", 0, 10_000, status = ShiftStatus.COMPLETED, personnelNumber = "1", createdAtUtc = 0, updatedAtUtc = 0)
        val box = BoxWithInterruptions(
            BoxEntity(id = 1, displayNumber = "F-1", type = BoxType.FILING, employeeNumber = "1", startedAtUtc = 0, startedElapsedRealtime = 0, bootCount = 0, endedAtUtc = 1_000, status = BoxStatus.FINISHED, createdAtUtc = 0, updatedAtUtc = 0, shiftId = 1),
            emptyList(),
        )
        val change = WorkProcessEntity(id = 1, type = ProcessType.BOX_CHANGE, shiftId = 1, personnelNumber = "1", startedAtUtc = 1_000, endedAtUtc = 2_000, status = ProcessStatus.COMPLETED, createdAtUtc = 0, updatedAtUtc = 0)
        val stats = ShiftStatisticsService.calculate(ShiftWithData(shift, listOf(box), listOf(change)), nowUtc = 2_000)
        assertEquals(1_000L, stats.averageBoxMillis)
        assertEquals(2_000L, stats.productiveMillis)
    }

    @Test fun `deleted box remains stored but is excluded from all box statistics`() {
        val shift = ShiftEntity(1, ShiftType.LATE, "2026-07-17", 0, 10_000, status = ShiftStatus.MANUALLY_CORRECTED, personnelNumber = "7262", createdAtUtc = 0, updatedAtUtc = 0)
        val deleted = BoxWithInterruptions(
            BoxEntity(id = 1, displayNumber = "S-2026-07-17-001", type = BoxType.DAILY_MAIL, employeeNumber = "7262", startedAtUtc = 1_000, startedElapsedRealtime = 0, bootCount = 0, endedAtUtc = 5_000, status = BoxStatus.CANCELLED, createdAtUtc = 0, updatedAtUtc = 0, shiftId = 1),
            emptyList(),
        )
        val stats = ShiftStatisticsService.calculate(ShiftWithData(shift, listOf(deleted), emptyList()), nowUtc = 10_000)
        assertEquals(0, stats.boxCount)
        assertEquals(0L, stats.boxGrossMillis)
        assertEquals(0L, stats.boxNetMillis)
        assertEquals(null, stats.averageBoxMillis)
    }
}
