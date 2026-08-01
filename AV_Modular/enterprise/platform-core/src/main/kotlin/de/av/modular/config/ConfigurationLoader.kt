package de.av.modular.config

import de.av.modular.model.LoadedModule
import de.av.modular.model.ModuleManifest
import de.av.modular.model.ProcessCatalog
import de.av.modular.model.ReportCatalog
import de.av.modular.model.RuleCatalog
import de.av.modular.model.SignedCustomerProfile
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class ConfigurationLoader(
    private val parser: ConfigurationParser = ConfigurationParser(),
) {
    fun loadModule(directory: Path): LoadedModule {
        val root = directory.toAbsolutePath().normalize()
        return parser.loadModule { relative -> Files.readString(resolveSafe(root, relative)) }
    }

    fun loadProfile(directory: Path): SignedCustomerProfile =
        parser.loadProfile(Files.readString(resolveSafe(directory.toAbsolutePath().normalize(), "profile.json")))

    private fun resolveSafe(root: Path, relative: String): Path {
        require(relative.isNotBlank()) { "Leerer Konfigurationspfad" }
        val resolved = root.resolve(relative).normalize()
        require(resolved.startsWith(root)) { "Pfad verlässt das Modulverzeichnis: $relative" }
        return resolved
    }
}

class ConfigurationParser(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
    },
) {
    fun loadModule(readText: (String) -> String): LoadedModule {
        val manifest = decode<ModuleManifest>(readText("module.json"))
        return LoadedModule(
            manifest = manifest,
            processes = decode(readText(manifest.processesFile)),
            rules = decode(readText(manifest.rulesFile)),
            reports = decode(readText(manifest.reportsFile)),
            strings = json.decodeFromJsonElement(json.parseToJsonElement(readText(manifest.stringsFile))),
        )
    }

    fun loadProfile(text: String): SignedCustomerProfile = decode(text)

    private inline fun <reified T> decode(text: String): T = json.decodeFromString(text)
}
