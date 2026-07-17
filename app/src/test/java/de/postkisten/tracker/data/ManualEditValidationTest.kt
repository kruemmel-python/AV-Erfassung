package de.postkisten.tracker.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ManualEditValidationTest {
    private val box = BoxEntity(
        id = 1,
        displayNumber = "2026-07-16-001",
        type = BoxType.DAILY_MAIL,
        employeeNumber = "1234",
        startedAtUtc = 1_000L,
        startedElapsedRealtime = 0L,
        bootCount = 0,
        endedAtUtc = 101_000L,
        status = BoxStatus.FINISHED,
        createdAtUtc = 1_000L,
        updatedAtUtc = 101_000L,
    )
    private val existing = listOf(
        InterruptionEntity(
            id = 10,
            boxId = 1,
            type = InterruptionType.PAUSE,
            startedAtUtc = 20_000L,
            startedElapsedRealtime = 0L,
            bootCount = 0,
            endedAtUtc = 30_000L,
        ),
    )

    @Test fun `manual interruption inside free part of box is accepted`() {
        assertNull(validateManualInterruption(box, existing, 40_000L, 50_000L))
    }

    @Test fun `overlapping interruption is rejected`() {
        assertNotNull(validateManualInterruption(box, existing, 25_000L, 35_000L))
    }

    @Test fun `editing an interruption excludes itself from overlap check`() {
        assertNull(validateManualInterruption(box, existing, 22_000L, 32_000L, excludedInterruptionId = 10))
    }

    @Test fun `interruption outside box is rejected`() {
        assertNotNull(validateManualInterruption(box, existing, 90_000L, 102_000L))
    }
}
