package de.av.modular.reporting

import de.av.modular.model.MetricDefinition
import de.av.modular.model.ReportDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportingTest {
    @Test
    fun `same record and digest is ignored as true duplicate`() {
        val original = record("WI-1", revision = 1, exportId = "EXP-1")
        val duplicate = original.copy(exportId = "EXP-2", revisionNumber = 2)
        val batch = WorkRecordCsvImporter().import(listOf("a.csv" to csv(original), "b.csv" to csv(duplicate)))
        assertEquals(1, batch.records.size)
        assertEquals(1, batch.duplicateCount)
        assertTrue(batch.conflicts.isEmpty())
    }

    @Test
    fun `higher revision deterministically supersedes older payload`() {
        val old = record("WI-1", revision = 1, exportId = "EXP-1")
        val newer = record("WI-1", revision = 2, exportId = "EXP-2", end = "2026-08-01T06:10:00Z")
        val batch = WorkRecordCsvImporter().import(listOf("new.csv" to csv(newer), "old.csv" to csv(old)))
        assertEquals(2, batch.records.single().revisionNumber)
        assertEquals("2026-08-01T06:10:00Z", batch.records.single().endTimestamp)
        assertTrue(batch.conflicts.isEmpty())
    }

    @Test
    fun `same revision with different digest is quarantined`() {
        val first = record("WI-1", revision = 4, exportId = "EXP-1")
        val altered = record("WI-1", revision = 4, exportId = "EXP-2", end = "2026-08-01T06:20:00Z")
        val batch = WorkRecordCsvImporter().import(listOf("a.csv" to csv(first), "b.csv" to csv(altered)))
        assertTrue(batch.records.isEmpty())
        assertEquals(1, batch.conflicts.size)
        assertEquals("WI-1", batch.conflicts.single().recordId)
    }

    @Test
    fun `different record ids remain separate despite identical business values`() {
        val first = record("WI-1", revision = 1, exportId = "EXP-1")
        val second = record("WI-2", revision = 1, exportId = "EXP-1")
        val batch = WorkRecordCsvImporter().import(listOf("a.csv" to csv(first, second)))
        assertEquals(2, batch.records.size)
    }

    @Test
    fun `payload modification is rejected before deduplication`() {
        val original = record("WI-1", revision = 1, exportId = "EXP-1")
        val tamperedCsv = csv(original).replace("2026-08-01T06:00:00Z", "2026-08-01T06:30:00Z")
        val batch = WorkRecordCsvImporter().import(listOf("tampered.csv" to tamperedCsv))
        assertTrue(batch.records.isEmpty())
        assertTrue(batch.warnings.single().contains("payload_digest"))
    }

    @Test
    fun `legacy v1 migration is deterministic and preserves the original source`() {
        val legacy = buildString {
            appendLine("contract_version;tenant_id;module_id;schema_version;shift_id;employee_id;process_type;start_timestamp;end_timestamp;status;custom_data;manually_modified;deleted_for_audit;net_duration_seconds;target_duration_seconds")
            appendLine("av-work-record-v1;tenant_demo;mail_processing;1;SHIFT-1;10001;routing;2026-08-01T05:30:00Z;2026-08-01T06:00:00Z;completed;{};false;false;1800;2100")
        }
        val first = WorkRecordCsvImporter().import(listOf("legacy.csv" to legacy))
        val second = WorkRecordCsvImporter().import(listOf("legacy.csv" to legacy))
        assertEquals(first.records.single().recordId, second.records.single().recordId)
        assertEquals(first.records.single().payloadDigest, second.records.single().payloadDigest)
        assertEquals(legacy, legacy)
        assertTrue(first.warnings.single().contains("deterministisch migriert"))
    }

    @Test
    fun `operational report excludes deleted work items`() {
        val records = listOf(record("WI-1", 1, "EXP-1"), record("WI-2", 1, "EXP-1", deleted = true))
        val definition = ReportDefinition(
            "productivity", "Produktivität", listOf("employee_id"),
            listOf(MetricDefinition("count", "count"), MetricDefinition("average", "average", "net_duration_seconds")),
        )
        val report = ReportEngine().generate(definition, records)
        assertEquals(1.0, report.groups.single().metrics.getValue("count"))
    }

    @Test
    fun `quality and correction reports retain soft deletion`() {
        val records = listOf(record("WI-1", 1, "EXP-1"), record("WI-2", 1, "EXP-1", deleted = true))
        val summary = QualityAnalyzer().analyze(records)
        assertEquals(1, summary.validWorkItems)
        assertEquals(1, summary.deletedWorkItems)
        assertFalse(summary.narrative.isBlank())
        val definition = ReportDefinition("audit", "Änderungen", listOf("correction_type"), listOf(MetricDefinition("count", "count")))
        val report = ReportEngine().generate(definition, records)
        assertEquals(setOf("none", "deleted"), report.groups.map { it.dimensions.getValue("correction_type") }.toSet())
    }

    private fun record(
        id: String,
        revision: Long,
        exportId: String,
        end: String = "2026-08-01T06:00:00Z",
        deleted: Boolean = false,
    ): WorkRecord {
        val unsigned = WorkRecord(
            contractVersion = WorkRecordCsvImporter.CONTRACT_V2,
            recordId = id,
            revisionNumber = revision,
            sourceDeviceId = "DEVICE-1",
            exportId = exportId,
            payloadDigest = "",
            tenantId = "tenant_demo",
            moduleId = "mail_processing",
            schemaVersion = 2,
            shiftId = "SHIFT-1",
            employeeId = "10001",
            processType = "routing",
            startTimestamp = "2026-08-01T05:30:00Z",
            endTimestamp = end,
            status = if (deleted) "deleted" else "completed",
            customData = "{}",
            manuallyModified = deleted,
            deletedForAudit = deleted,
            netDurationSeconds = 1800.0,
            targetDurationSeconds = 2100.0,
            shiftDate = "2026-08-01",
        )
        return unsigned.copy(payloadDigest = WorkRecordDigest.compute(unsigned))
    }

    private fun csv(vararg records: WorkRecord): String = buildString {
        appendLine("contract_version;record_id;revision_number;source_device_id;export_id;payload_digest;tenant_id;module_id;schema_version;shift_id;employee_id;process_type;start_timestamp;end_timestamp;status;custom_data;manually_modified;deleted_for_audit;net_duration_seconds;target_duration_seconds")
        records.forEach { record ->
            appendLine(listOf(
                record.contractVersion, record.recordId, record.revisionNumber, record.sourceDeviceId, record.exportId,
                record.payloadDigest, record.tenantId, record.moduleId, record.schemaVersion, record.shiftId,
                record.employeeId, record.processType, record.startTimestamp, record.endTimestamp.orEmpty(), record.status,
                record.customData, record.manuallyModified, record.deletedForAudit, record.netDurationSeconds, record.targetDurationSeconds,
            ).joinToString(";"))
        }
    }
}
