package de.av.modular

import de.av.modular.audit.AuditLedger
import de.avm.backup.EvidenceBackupMetadata
import de.avm.backup.EvidenceBackupService
import de.av.modular.config.ConfigurationLoader
import de.avm.diagnostics.DiagnosticMetadata
import de.avm.diagnostics.DiagnosticService
import de.av.modular.packages.ConfigurationPackageService
import de.av.modular.security.AccessRequest
import de.av.modular.security.AuthorizationService
import de.av.modular.security.Principal
import de.av.modular.security.DevelopmentProfileVerifier
import de.av.modular.runtime.PlatformRuntime
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnterpriseServicesTest {
    private val root = Path.of(System.getProperty("av.modular.root"))
    private val profile = ConfigurationLoader().loadProfile(root.resolve("enterprise/profiles/demo_dhl")).profile

    @Test
    fun `authorization enforces tenant location and permission`() {
        val service = AuthorizationService(profile)
        val supervisor = Principal("tl-1", "tenant_demo", setOf("supervisor"), setOf("berlin"))
        assertTrue(service.authorize(supervisor, AccessRequest("work_item.correct", "tenant_demo", "berlin")).granted)
        assertFalse(service.authorize(supervisor, AccessRequest("work_item.correct", "other", "berlin")).granted)
        assertFalse(service.authorize(supervisor, AccessRequest("shift.start", "tenant_demo", "berlin")).granted)
    }

    @Test
    fun `audit ledger creates verifiable hash chain`() {
        val ledger = AuditLedger(Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC))
        ledger.append("tenant_demo", "shift.started", "10001", "SHIFT-1", "{}".toByteArray())
        ledger.append("tenant_demo", "work_item.completed", "10001", "WI-1", "{\"duration\":1200}".toByteArray())
        val result = ledger.verify()
        assertTrue(result.valid, result.message)
        assertTrue(ledger.snapshot("tenant_demo").zipWithNext().all { (a, b) -> b.previousHash == a.entryHash })
    }

    @Test
    fun `signed configuration package detects tampering and supports revocation`() {
        val temp = Files.createTempDirectory("av-package-test")
        val source = Files.createDirectories(temp.resolve("source"))
        source.resolve("module.json").writeText("{\"module_id\":\"test\"}")
        val packageFile = temp.resolve("test.avpkg")
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val service = ConfigurationPackageService(clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC))
        service.create(source, packageFile, "test_package", "1.0.0", "key-1", keys.private)
        assertTrue(service.verify(packageFile, mapOf("key-1" to keys.public)).valid)
        assertFalse(service.verify(packageFile, mapOf("key-1" to keys.public), setOf("key-1")).valid)
    }

    @Test
    fun `diagnostics expose technical metadata only`() {
        val module = ConfigurationLoader().loadModule(root.resolve("modules/mail_processing"))
        val signedProfile = ConfigurationLoader().loadProfile(root.resolve("enterprise/profiles/demo_dhl"))
        val runtime = PlatformRuntime.create(module, signedProfile, DevelopmentProfileVerifier)
        val metadata = DiagnosticMetadata(
            coreVersion = "1.0.0", moduleId = runtime.module.source.manifest.moduleId,
            moduleVersion = runtime.module.source.manifest.moduleVersion,
            moduleSchemaVersion = runtime.module.source.manifest.schemaVersion,
            profileId = runtime.profile.profile.profileId, environment = runtime.profile.profile.environment,
            timezone = runtime.profile.profile.timezone, storageSchemaVersion = 2,
        )
        val snapshot = DiagnosticService(clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC))
            .capture(metadata, mapOf("shifts" to 2, "work_items" to 8, "audit_events" to 10))
        assertTrue("mail_processing" in snapshot.supportText())
        assertFalse("employee" in snapshot.supportText().lowercase())
        assertTrue("av-support-diagnostic-v1" in snapshot.contractJson())
        assertFalse("employee" in snapshot.contractJson().lowercase())
    }

    @Test
    fun `evidence backup requires complete signed payload and rejects revocation`() {
        val temp = Files.createTempDirectory("av-evidence-test")
        val backup = temp.resolve("complete.avbackup")
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val metadata = EvidenceBackupMetadata(
            backupId = "BACKUP-1", tenantId = "tenant_demo", sourceDeviceId = "DEVICE-1",
            roomSchemaVersion = 2, coreVersion = "1.0.0", moduleId = "mail_processing",
            moduleVersion = "1.0.0", profileId = "demo_dhl", packageFormatVersion = 1,
            auditHeadHash = "a".repeat(64),
        )
        val files = EvidenceBackupService.REQUIRED_FILES.associateWith { "[]".toByteArray() }
        val service = EvidenceBackupService(clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC))
        service.create(backup, metadata, files, "backup-key-1", keys.private)
        assertTrue(service.verify(backup, mapOf("backup-key-1" to keys.public)).valid)
        assertFalse(service.verify(backup, mapOf("backup-key-1" to keys.public), setOf("backup-key-1")).valid)
    }
}
