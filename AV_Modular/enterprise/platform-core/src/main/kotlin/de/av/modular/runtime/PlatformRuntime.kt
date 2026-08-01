package de.av.modular.runtime

import de.av.modular.model.EffectiveModule
import de.av.modular.model.LoadedModule
import de.av.modular.model.PlatformEvent
import de.av.modular.model.SignedCustomerProfile
import de.av.modular.model.TriggeredAction
import de.av.modular.model.WorkItemDefinition
import de.av.modular.model.WorkItemRecord
import de.av.modular.rules.RuleEngine
import de.av.modular.security.ProfileSignatureVerifier
import de.av.modular.validation.ConfigurationValidator
import de.av.modular.validation.RecordValidator
import de.av.modular.validation.Severity

class PlatformConfigurationException(message: String) : IllegalArgumentException(message)

class PlatformRuntime private constructor(
    val module: EffectiveModule,
    val profile: SignedCustomerProfile,
    private val eventBus: EventBus,
    private val ruleEngine: RuleEngine,
) {
    fun validateRecord(record: WorkItemRecord) = RecordValidator().validate(record, module, profile.profile)

    fun processEvent(event: PlatformEvent): List<TriggeredAction> {
        val processId = event.attributes["process_type"]
        val process = processId?.let(module.workItems::get)
        val actions = ruleEngine.evaluate(event, process)
        eventBus.publish(event)
        return actions
    }

    fun subscribe(eventType: String, handler: (PlatformEvent) -> Unit): AutoCloseable =
        eventBus.subscribe(eventType, handler)

    companion object {
        fun create(
            loadedModule: LoadedModule,
            profile: SignedCustomerProfile,
            signatureVerifier: ProfileSignatureVerifier,
            validator: ConfigurationValidator = ConfigurationValidator(),
        ): PlatformRuntime {
            val issues = validator.validate(loadedModule, profile)
            val errors = issues.filter { it.severity == Severity.ERROR }
            if (errors.isNotEmpty()) {
                throw PlatformConfigurationException(errors.joinToString("\n") { "${it.path}: ${it.message}" })
            }
            val signature = signatureVerifier.verify(profile)
            if (!signature.accepted) throw PlatformConfigurationException(signature.message)
            val overrides = profile.profile.targetOverrides
                .filter { it.moduleId == loadedModule.manifest.moduleId }
                .associateBy { it.workItemId }
            val effective = loadedModule.processes.workItems.associate { item ->
                item.id to item.copy(
                    targetDurationSeconds = overrides[item.id]?.targetDurationSeconds ?: item.targetDurationSeconds,
                )
            }
            return PlatformRuntime(
                module = EffectiveModule(loadedModule, effective),
                profile = profile,
                eventBus = EventBus(),
                ruleEngine = RuleEngine(loadedModule.rules),
            )
        }
    }
}

class EventBus {
    private val handlers = mutableMapOf<String, MutableList<(PlatformEvent) -> Unit>>()

    @Synchronized
    fun subscribe(eventType: String, handler: (PlatformEvent) -> Unit): AutoCloseable {
        handlers.getOrPut(eventType) { mutableListOf() }.add(handler)
        return AutoCloseable { synchronized(this) { handlers[eventType]?.remove(handler) } }
    }

    fun publish(event: PlatformEvent) {
        val snapshot = synchronized(this) {
            (handlers[event.type].orEmpty() + handlers["*"].orEmpty()).toList()
        }
        snapshot.forEach { it(event) }
    }
}
