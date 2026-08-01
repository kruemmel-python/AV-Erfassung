package de.av.modular.registry

import de.av.modular.model.LoadedModule
import java.util.concurrent.ConcurrentHashMap

class ModuleRegistry(private val supportedSchemaVersion: Int = 1) {
    private val modules = ConcurrentHashMap<String, LoadedModule>()

    fun install(module: LoadedModule, replace: Boolean = false) {
        require(module.manifest.schemaVersion == supportedSchemaVersion) {
            "Nicht unterstützte Schema-Version ${module.manifest.schemaVersion}"
        }
        if (replace) modules[module.manifest.moduleId] = module
        else require(modules.putIfAbsent(module.manifest.moduleId, module) == null) {
            "Modul bereits installiert: ${module.manifest.moduleId}"
        }
    }

    fun uninstall(moduleId: String): LoadedModule? = modules.remove(moduleId)
    fun get(moduleId: String): LoadedModule? = modules[moduleId]
    fun installed(): List<LoadedModule> = modules.values.sortedBy { it.manifest.moduleId }
}
