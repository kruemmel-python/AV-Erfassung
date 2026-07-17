package de.postkisten.tracker.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamLeaderKeyTest {
    private val issuedAt = 1_752_600_000_000L
    private val knownKey = "TL1-SZGATC-OZZ7FNZFDCB5"

    @Test fun `known generated key is accepted for nine hours`() {
        val result = TeamLeaderKey.validate(knownKey, issuedAt + 60_000L)
        assertTrue(result.message, result.valid)
        assertEquals((issuedAt / 1_000L) * 1_000L + TeamLeaderKey.VALIDITY_MILLIS, result.expiresAtUtc)
    }

    @Test fun `key expires after nine hours`() {
        val expiry = (issuedAt / 1_000L) * 1_000L + TeamLeaderKey.VALIDITY_MILLIS
        assertFalse(TeamLeaderKey.validate(knownKey, expiry).valid)
    }

    @Test fun `modified key is rejected`() {
        assertFalse(TeamLeaderKey.validate(knownKey.dropLast(1) + "A", issuedAt + 60_000L).valid)
    }
}
