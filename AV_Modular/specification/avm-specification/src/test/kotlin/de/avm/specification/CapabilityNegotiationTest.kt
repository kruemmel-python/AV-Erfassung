package de.avm.specification

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityNegotiationTest {
    private val reporter = ComponentCapabilities(
        component = "av-reporter",
        version = "1.0.0",
        supportedContracts = mapOf("avm-work-record" to listOf(1, 2)),
        capabilities = setOf("revision-aware-import", "conflict-rejection"),
    )

    @Test
    fun `matching contract and capability are accepted`() {
        val decision = CapabilityNegotiation.evaluate(
            reporter,
            listOf(CapabilityRequirement("avm-work-record", setOf(2), setOf("conflict-rejection"))),
        )
        assertTrue(decision.compatible, decision.reasons.joinToString())
    }

    @Test
    fun `missing mandatory capability is rejected deterministically`() {
        val decision = CapabilityNegotiation.evaluate(
            reporter,
            listOf(CapabilityRequirement("avm-work-record", setOf(2), setOf("evidence-backup"))),
        )
        assertFalse(decision.compatible)
        assertTrue("capability:evidence-backup" in decision.reasons)
    }
}
