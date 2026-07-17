package de.postkisten.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {
    @Test fun `finished box subtracts interruptions from gross time`() {
        val box = BoxWithInterruptions(
            BoxEntity(
                id = 1, displayNumber = "2026-07-15-001",
                type = BoxType.DAILY_MAIL,
                employeeNumber = "12345",
                startedAtUtc = 1_000, startedElapsedRealtime = 100, bootCount = 2,
                endedAtUtc = 81_000, status = BoxStatus.FINISHED,
                createdAtUtc = 1_000, updatedAtUtc = 81_000,
            ),
            listOf(
                InterruptionEntity(
                    id = 1, boxId = 1, type = InterruptionType.REGISTRATION,
                    startedAtUtc = 26_000, startedElapsedRealtime = 25_100, bootCount = 2,
                    endedAtUtc = 38_000,
                ),
                InterruptionEntity(
                    id = 2, boxId = 1, type = InterruptionType.IMAGE,
                    startedAtUtc = 53_000, startedElapsedRealtime = 52_100, bootCount = 2,
                    endedAtUtc = 61_000,
                ),
            ),
        )
        assertEquals(80_000, box.grossMillis())
        assertEquals(20_000, box.interruptionMillis())
        assertEquals(60_000, box.netMillis())
    }

    @Test fun `active duration uses monotonic time on same boot`() {
        val interruption = InterruptionEntity(
            boxId = 1, type = InterruptionType.PAUSE,
            startedAtUtc = 10_000, startedElapsedRealtime = 4_000, bootCount = 7,
        )
        assertEquals(6_000, interruption.durationMillis(ClockSnapshot(999_000, 10_000, 7)))
    }

    @Test fun `duration formatting is stable`() {
        assertEquals("01:02:03", 3_723_000L.asDuration())
    }
}
