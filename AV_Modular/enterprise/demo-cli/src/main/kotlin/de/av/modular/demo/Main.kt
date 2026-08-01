package de.av.modular.demo

import de.av.modular.config.ConfigurationLoader
import de.av.modular.model.PlatformEvent
import de.av.modular.model.WorkItemRecord
import de.av.modular.runtime.PlatformRuntime
import de.av.modular.security.DevelopmentProfileVerifier
import java.nio.file.Path

fun main(args: Array<String>) {
    val root = Path.of(args.firstOrNull() ?: System.getProperty("av.modular.root", ".")).toAbsolutePath().normalize()
    val loader = ConfigurationLoader()
    val module = loader.loadModule(root.resolve("modules/mail_processing"))
    val profile = loader.loadProfile(root.resolve("enterprise/profiles/demo_dhl"))
    val runtime = PlatformRuntime.create(module, profile, DevelopmentProfileVerifier)

    println("AV Modular Enterprise 1.0.0-RC2")
    println("Profil: ${runtime.profile.profile.displayName}")
    println("Modul: ${runtime.module.source.manifest.displayName} ${runtime.module.source.manifest.moduleVersion}")
    println("Vorgänge: ${runtime.module.workItems.values.joinToString { it.displayName }}")
    println("Routing-Zielzeit nach Kundenprofil: ${runtime.module.workItems.getValue("routing").targetDurationSeconds / 60} Minuten")

    val record = WorkItemRecord(
        id = "WI-DEMO-0001",
        moduleId = "mail_processing",
        processType = "routing",
        schemaVersion = 1,
        tenantId = "tenant_demo",
        locationId = "location_berlin",
        employeeId = "10001",
        shiftId = "SHIFT-DEMO-001",
        startTimestamp = "2026-08-01T05:30:00Z",
        endTimestamp = "2026-08-01T06:25:00Z",
        status = "completed",
    )
    val issues = runtime.validateRecord(record)
    println("Datensatzprüfung: ${if (issues.isEmpty()) "OK" else issues.joinToString { it.message }}")

    runtime.subscribe("work_item.completed") { println("Ereignis empfangen: ${it.type}") }
    val actions = runtime.processEvent(
        PlatformEvent(
            type = "work_item.completed",
            attributes = mapOf("process_type" to "routing", "work_item_id" to record.id),
            metrics = mapOf("duration_seconds" to 3300.0),
        ),
    )
    println("Ausgelöste Regeln: ${actions.joinToString { "${it.type}(${it.parameter})" }}")
    println("Berichte: ${runtime.module.source.reports.reports.joinToString { it.title }}")
}
