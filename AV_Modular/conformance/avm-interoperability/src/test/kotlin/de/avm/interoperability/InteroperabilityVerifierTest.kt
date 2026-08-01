package de.avm.interoperability

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class InteroperabilityVerifierTest {
    private val root = Path.of(requireNotNull(System.getProperty("avm.root")))
    private val baseline = InteroperabilityVerifier.parse(root.resolve("pilot/evidence/reference-cross-language.json"))

    @Test
    fun `cross-language reference baseline is interoperable`() {
        assertTrue(InteroperabilityVerifier.verify(baseline, false, root).passed)
    }

    @Test
    fun `different implementation decision is rejected`() {
        val vector = baseline.vectors.first()
        val changedResult = vector.results.last().copy(digest = "0".repeat(64))
        val changedVector = vector.copy(results = vector.results.dropLast(1) + changedResult)
        val changed = baseline.copy(vectors = listOf(changedVector) + baseline.vectors.drop(1))
        assertFalse(InteroperabilityVerifier.verify(changed, false, root).passed)
    }

    @Test
    fun `reference baseline cannot claim final industry readiness`() {
        assertFalse(InteroperabilityVerifier.verify(baseline, true, root).passed)
    }

    @Test
    fun `two organizations and independent approvals satisfy final governance`() {
        val externalImplementation = baseline.implementations.last().copy(organizationId = "org-independent-validator")
        val evidence = baseline.copy(
            scope = "industry-pilot",
            implementations = baseline.implementations.dropLast(1) + externalImplementation,
            pilotOrganizations = listOf("org-avm-reference", "org-independent-validator"),
            approvals = listOf(
                ApprovalEvidence("approver-a", "org-avm-reference", "release-owner", "2026-08-01T12:00:00Z", true),
                ApprovalEvidence("approver-b", "org-independent-validator", "independent-reviewer", "2026-08-01T12:01:00Z", true),
            ),
        )
        assertTrue(InteroperabilityVerifier.verify(evidence, true, root).passed)
    }

    @Test
    fun `pilot schema and template are valid json documents`() {
        listOf(
            root.resolve("pilot/interoperability-evidence.schema.json"),
            root.resolve("pilot/templates/external-industry-pilot.template.json"),
        ).forEach { path -> Json.parseToJsonElement(path.toFile().readText()) }
    }
}
