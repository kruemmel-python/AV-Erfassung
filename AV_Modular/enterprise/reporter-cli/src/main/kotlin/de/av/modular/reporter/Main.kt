package de.av.modular.reporter

import de.av.modular.config.ConfigurationLoader
import de.av.modular.reporting.QualityAnalyzer
import de.av.modular.reporting.ReportEngine
import de.av.modular.reporting.WorkRecordCsvImporter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

fun main(args: Array<String>) {
    val options = args.toList()
    if ("--help" in options || options.isEmpty()) {
        println("AV Reporter CLI\n--module <Ordner> --output <bericht.html> [--employee <Personalnummer>] <csv> [csv...]")
        return
    }
    fun required(name: String): String {
        val index = options.indexOf(name)
        require(index >= 0 && index + 1 < options.size) { "Parameter fehlt: $name" }
        return options[index + 1]
    }
    val moduleDirectory = Path.of(required("--module"))
    val output = Path.of(required("--output"))
    val employee = options.indexOf("--employee").takeIf { it >= 0 }?.let { options.getOrNull(it + 1) }
    val consumed = setOf("--module", moduleDirectory.toString(), "--output", output.toString(), "--employee", employee)
    val csvFiles = options.filterNot { it in consumed }.map(Path::of)
    require(csvFiles.isNotEmpty()) { "Mindestens eine CSV-Datei ist erforderlich" }

    val module = ConfigurationLoader().loadModule(moduleDirectory)
    val batch = WorkRecordCsvImporter().import(csvFiles.map { it.name to Files.readString(it) })
    require(batch.conflicts.isEmpty()) {
        "Import wegen ${batch.conflicts.size} Revisionskonflikt(en) abgebrochen: " +
            batch.conflicts.joinToString { "${it.recordId}@${it.revisionNumber}" }
    }
    val targets = module.processes.workItems.associate { it.id to it.targetDurationSeconds.toDouble() }
    val enriched = batch.records.map { if (it.targetDurationSeconds > 0) it else it.copy(targetDurationSeconds = targets[it.processType] ?: 0.0) }
    val selected = employee?.let { id -> enriched.filter { it.employeeId == id } } ?: enriched
    val quality = QualityAnalyzer()
    val summary = quality.analyze(selected)
    output.toAbsolutePath().parent?.let(Files::createDirectories)
    Files.writeString(output, quality.toHtml("QS-Bericht ${module.manifest.displayName}", summary))

    println("Quellen: ${batch.sourceCount}; Datensätze: ${selected.size}; Duplikate: ${batch.duplicateCount}; ersetzte Revisionen: ${batch.supersededRevisionCount}; Warnungen: ${batch.warnings.size}")
    module.reports.reports.forEach { definition ->
        val report = ReportEngine().generate(definition, selected)
        println("${report.title}: ${report.groups.size} Gruppe(n)")
    }
    println("QS-Bericht: ${output.toAbsolutePath()}")
    batch.warnings.forEach { println("WARNUNG: $it") }
}
