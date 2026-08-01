package de.av.modular

import de.av.modular.config.ConfigurationLoader
import de.av.modular.model.PlatformEvent
import de.av.modular.model.SignedCustomerProfile
import de.av.modular.model.WorkItemRecord
import de.av.modular.runtime.PlatformConfigurationException
import de.av.modular.runtime.PlatformRuntime
import de.av.modular.security.DevelopmentProfileVerifier
import de.av.modular.validation.ConfigurationValidator
import de.av.modular.validation.Severity
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PlatformRuntimeTest {
    private val root = Path.of(System.getProperty("av.modular.root"))
    private val loader = ConfigurationLoader()
    private val module = loader.loadModule(root.resolve("modules/mail_processing"))
    private val signedProfile = loader.loadProfile(root.resolve("enterprise/profiles/demo_dhl"))

    @Test
    fun `reference module and profile are valid`() {
        val issues = ConfigurationValidator { LocalDate.of(2026, 8, 1) }.validate(module, signedProfile)
        assertTrue(issues.none { it.severity == Severity.ERROR }, issues.joinToString())
        assertEquals(6, module.processes.workItems.size)
    }

    @Test
    fun `customer profile overrides module target without changing module`() {
        val runtime = PlatformRuntime.create(module, signedProfile, DevelopmentProfileVerifier)
        assertEquals(1800, module.processes.workItems.single { it.id == "routing" }.targetDurationSeconds)
        assertEquals(2100, runtime.module.workItems.getValue("routing").targetDurationSeconds)
    }

    @Test
    fun `rule engine creates qs actions for duration anomaly`() {
        val runtime = PlatformRuntime.create(module, signedProfile, DevelopmentProfileVerifier)
        val actions = runtime.processEvent(
            PlatformEvent(
                type = "work_item.completed",
                attributes = mapOf("process_type" to "routing"),
                metrics = mapOf("duration_seconds" to 3200.0),
            ),
        )
        assertEquals(listOf("require_reason", "create_qs_flag"), actions.map { it.type })
    }

    @Test
    fun `production profile without signature is rejected`() {
        val production = SignedCustomerProfile(signedProfile.profile.copy(environment = "production"))
        assertFailsWith<PlatformConfigurationException> {
            PlatformRuntime.create(module, production, DevelopmentProfileVerifier)
        }
    }

    @Test
    fun `record validator enforces employee id and module field schema`() {
        val runtime = PlatformRuntime.create(module, signedProfile, DevelopmentProfileVerifier)
        val record = WorkItemRecord(
            id = "WI-TEST",
            moduleId = "mail_processing",
            processType = "hr_file",
            schemaVersion = 1,
            tenantId = "tenant_demo",
            locationId = "berlin",
            employeeId = "A-12",
            shiftId = "SHIFT-1",
            startTimestamp = "2026-08-01T05:30:00Z",
            status = "active",
            customData = buildJsonObject {
                put("document_group", "x".repeat(81))
                put("unknown", true)
            },
        )
        val paths = runtime.validateRecord(record).map { it.path }
        assertTrue("employee_id" in paths)
        assertTrue("custom_data.document_group" in paths)
        assertTrue("custom_data.unknown" in paths)
    }
}
