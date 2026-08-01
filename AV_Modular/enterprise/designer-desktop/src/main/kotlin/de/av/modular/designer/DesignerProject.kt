package de.av.modular.designer

import de.av.modular.config.ConfigurationLoader
import de.av.modular.model.LoadedModule
import de.av.modular.model.ModuleManifest
import de.av.modular.model.ProcessCatalog
import de.av.modular.model.ReportCatalog
import de.av.modular.model.RuleCatalog
import de.av.modular.model.SignedCustomerProfile
import de.av.modular.validation.ConfigurationValidator
import de.av.modular.validation.ValidationIssue
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class DesignerProject(
    val moduleDirectory: Path,
    val profileDirectory: Path,
    var module: LoadedModule,
    var profile: SignedCustomerProfile,
)

class DesignerProjectService(
    private val json: Json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false },
) {
    fun open(moduleDirectory: Path, profileDirectory: Path): DesignerProject {
        val loader = ConfigurationLoader()
        return DesignerProject(
            moduleDirectory.toAbsolutePath().normalize(),
            profileDirectory.toAbsolutePath().normalize(),
            loader.loadModule(moduleDirectory),
            loader.loadProfile(profileDirectory),
        )
    }

    fun validate(project: DesignerProject): List<ValidationIssue> = ConfigurationValidator().validate(project.module, project.profile)

    fun updateFromJson(
        project: DesignerProject,
        moduleJson: String,
        processesJson: String,
        rulesJson: String,
        reportsJson: String,
        profileJson: String,
    ) {
        val manifest = json.decodeFromString<ModuleManifest>(moduleJson)
        project.module = LoadedModule(
            manifest = manifest,
            processes = json.decodeFromString<ProcessCatalog>(processesJson),
            rules = json.decodeFromString<RuleCatalog>(rulesJson),
            reports = json.decodeFromString<ReportCatalog>(reportsJson),
            strings = project.module.strings,
        )
        project.profile = json.decodeFromString<SignedCustomerProfile>(profileJson)
    }

    fun save(project: DesignerProject) {
        val manifest = project.module.manifest
        Files.writeString(project.moduleDirectory.resolve("module.json"), json.encodeToString(manifest))
        Files.writeString(project.moduleDirectory.resolve(manifest.processesFile), json.encodeToString(project.module.processes))
        Files.writeString(project.moduleDirectory.resolve(manifest.rulesFile), json.encodeToString(project.module.rules))
        Files.writeString(project.moduleDirectory.resolve(manifest.reportsFile), json.encodeToString(project.module.reports))
        Files.writeString(project.moduleDirectory.resolve(manifest.stringsFile), json.encodeToString(project.module.strings))
        Files.writeString(project.profileDirectory.resolve("profile.json"), json.encodeToString(project.profile))
    }

    fun moduleJson(project: DesignerProject): String = json.encodeToString(project.module.manifest)
    fun processesJson(project: DesignerProject): String = json.encodeToString(project.module.processes)
    fun rulesJson(project: DesignerProject): String = json.encodeToString(project.module.rules)
    fun reportsJson(project: DesignerProject): String = json.encodeToString(project.module.reports)
    fun profileJson(project: DesignerProject): String = json.encodeToString(project.profile)
}
