package de.postkisten.tracker.shift

import de.postkisten.tracker.data.ShiftType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftResolverTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val resolver = ShiftResolver(zone)

    private fun millis(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private fun types(value: String) = resolver.resolveCandidates(millis(value)).map { it.type }.toSet()

    @Test fun `regular overlap boundaries use start inclusive end exclusive`() {
        assertEquals(setOf(ShiftType.EARLY), types("2026-07-15T13:29:00"))
        assertEquals(setOf(ShiftType.EARLY, ShiftType.LATE), types("2026-07-15T13:30:00"))
        assertEquals(setOf(ShiftType.LATE), types("2026-07-15T13:45:00"))
        assertEquals(setOf(ShiftType.LATE, ShiftType.NIGHT), types("2026-07-15T21:30:00"))
        assertEquals(setOf(ShiftType.NIGHT), types("2026-07-15T21:45:00"))
    }

    @Test fun `night shift stays assigned to previous shift date after midnight`() {
        val candidates = resolver.resolveCandidates(millis("2026-07-17T00:30:00"))
        assertEquals(1, candidates.size)
        assertEquals(ShiftType.NIGHT, candidates.single().type)
        assertEquals(LocalDate.of(2026, 7, 16), candidates.single().shiftDate)
    }

    @Test fun `night and early overlap until 0545`() {
        assertEquals(setOf(ShiftType.NIGHT), types("2026-07-17T05:29:00"))
        assertEquals(setOf(ShiftType.NIGHT, ShiftType.EARLY), types("2026-07-17T05:30:00"))
        assertEquals(setOf(ShiftType.EARLY), types("2026-07-17T05:45:00"))
    }

    @Test fun `monday windows use extended end times based on shift start weekday`() {
        val monday = LocalDate.of(2026, 7, 20)
        val early = resolver.getShiftWindow(ShiftType.EARLY, monday)
        val late = resolver.getShiftWindow(ShiftType.LATE, monday)
        val night = resolver.getShiftWindow(ShiftType.NIGHT, monday)
        assertEquals(millis("2026-07-20T14:00:00"), early.scheduledEndAtUtc)
        assertEquals(millis("2026-07-20T22:00:00"), late.scheduledEndAtUtc)
        assertEquals(millis("2026-07-21T06:00:00"), night.scheduledEndAtUtc)
    }

    @Test fun `sunday night ends monday 0545 and monday 0550 is not night`() {
        val sundayNight = resolver.getShiftWindow(ShiftType.NIGHT, LocalDate.of(2026, 7, 19))
        assertEquals(millis("2026-07-20T05:45:00"), sundayNight.scheduledEndAtUtc)
        assertTrue(ShiftType.NIGHT !in types("2026-07-20T05:50:00"))
    }

    @Test fun `monday night includes tuesday 0030 and overlaps early until 0600`() {
        val at0030 = resolver.resolveCandidates(millis("2026-07-21T00:30:00")).single()
        assertEquals(LocalDate.of(2026, 7, 20), at0030.shiftDate)
        assertEquals(setOf(ShiftType.NIGHT, ShiftType.EARLY), types("2026-07-21T05:59:00"))
        assertEquals(setOf(ShiftType.EARLY), types("2026-07-21T06:00:00"))
    }

    @Test fun `DST windows are built from Berlin zoned date times`() {
        val springNight = resolver.getShiftWindow(ShiftType.NIGHT, LocalDate.of(2026, 3, 28))
        val autumnNight = resolver.getShiftWindow(ShiftType.NIGHT, LocalDate.of(2026, 10, 24))
        assertEquals(7L * 60L * 60L * 1_000L + 15L * 60L * 1_000L, springNight.scheduledEndAtUtc - springNight.scheduledStartAtUtc)
        assertEquals(9L * 60L * 60L * 1_000L + 15L * 60L * 1_000L, autumnNight.scheduledEndAtUtc - autumnNight.scheduledStartAtUtc)
    }
}
