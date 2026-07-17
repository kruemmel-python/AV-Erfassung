package de.postkisten.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportServiceTest {
    private val shift = ShiftEntity(1, ShiftType.NIGHT, "2026-07-16", 0, 20_000, status = ShiftStatus.COMPLETED, personnelNumber = "7262", createdAtUtc = 0, updatedAtUtc = 0)
    private val box = BoxWithInterruptions(
        BoxEntity(id = 4, displayNumber = "N-2026-07-16-001", type = BoxType.HR_FILE, employeeNumber = "7262", startedAtUtc = 1_000, startedElapsedRealtime = 0, bootCount = 0, endedAtUtc = 2_000, status = BoxStatus.FINISHED, createdAtUtc = 0, updatedAtUtc = 0, shiftId = 1),
        emptyList(),
    )
    private val registration = WorkProcessEntity(id = 8, type = ProcessType.REGISTRATION, shiftId = 1, personnelNumber = "7262", startedAtUtc = 2_000, endedAtUtc = 5_000, status = ProcessStatus.COMPLETED, createdAtUtc = 0, updatedAtUtc = 0)

    @Test fun `details contain boxes and independent processes with stable columns`() {
        val csv = CsvExportService.details(listOf(ShiftWithData(shift, listOf(box), listOf(registration))))
        val rows = csv.trim().lines()
        assertEquals(3, rows.size)
        assertEquals(rows.first().split(';').size, rows[1].split(';').size)
        assertEquals(rows.first().split(';').size, rows[2].split(';').size)
        assertTrue(rows[1].contains("Kistenbearbeitung"))
        assertTrue(rows[2].contains("Registrierung"))
        assertFalse(rows[2].contains("N-2026-07-16-001"))
    }

    @Test fun `summary handles a shift without boxes without division by zero`() {
        val csv = CsvExportService.summary(listOf(ShiftWithData(shift, emptyList(), listOf(registration))))
        assertTrue(csv.contains("Anzahl Kisten"))
        assertTrue(csv.lines()[1].contains(";0;"))
    }

    @Test fun `semicolons and quotes are escaped`() {
        val process = registration.copy(type = ProcessType.OTHER, note = "A; \"B\"")
        val csv = CsvExportService.details(listOf(ShiftWithData(shift, emptyList(), listOf(process))))
        assertTrue(csv.contains("\"A; \"\"B\"\"\""))
    }
}
