package de.postkisten.keygenerator

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamLeaderKeyGeneratorTest {
    @Test fun `generator matches employee app key format`() {
        val value = TeamLeaderKeyGenerator.generate(1_752_600_000_000L)
        assertEquals("TL1-SZGATC-OZZ7FNZFDCB5", value.key)
        assertEquals(1_752_600_000_000L + TeamLeaderKeyGenerator.VALIDITY_MILLIS, value.expiresAtUtc)
    }
}
