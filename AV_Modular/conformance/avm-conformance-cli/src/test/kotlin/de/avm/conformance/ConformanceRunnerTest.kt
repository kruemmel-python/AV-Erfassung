package de.avm.conformance

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConformanceRunnerTest {
    @Test
    fun `all normative fixtures pass`() {
        val root = Path.of(requireNotNull(System.getProperty("avm.root")))
        val results = ConformanceRunner(root).run("all")
        val failures = results.filterNot(CheckResult::passed)
        assertTrue(failures.isEmpty(), failures.joinToString())
        assertEquals(results.size, results.map(CheckResult::id).distinct().size)
        assertTrue(results.none { ':' in it.subject || '\\' in it.subject })
        assertTrue(results.filter { it.testType == "NEGATIVE" }.all { it.expectedErrorCode == it.observedErrorCode })
    }

    @Test
    fun `evidence report is signed and detects result tampering`() {
        val root = Path.of(requireNotNull(System.getProperty("avm.root")))
        val report = ConformanceEvidence.create(root, ConformanceRunner(root).run("all"))
        assertTrue(ConformanceEvidence.verify(report))
        assertEquals("ephemeral-test-key", report.signature.signerType)
        assertEquals("untrusted-development-evidence", report.signature.trustStatus)
        assertFalse(report.officialRelease)
        assertFalse(report.signature.officialRelease)
        assertTrue(report.implementation.commit.matches(Regex("[0-9a-f]{40}")))
        val first = report.results.first()
        val tamperedResults = listOf(first.copy(detail = "tampered")) + report.results.drop(1)
        assertFalse(ConformanceEvidence.verify(report.copy(results = tamperedResults)))
    }
}
