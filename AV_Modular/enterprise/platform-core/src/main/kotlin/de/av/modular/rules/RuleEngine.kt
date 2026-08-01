package de.av.modular.rules

import de.av.modular.model.PlatformEvent
import de.av.modular.model.RuleCatalog
import de.av.modular.model.RuleCondition
import de.av.modular.model.TriggeredAction
import de.av.modular.model.WorkItemDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

class RuleEngine(private val catalog: RuleCatalog) {
    fun evaluate(event: PlatformEvent, process: WorkItemDefinition?): List<TriggeredAction> =
        catalog.rules.asSequence()
            .filter { it.event == event.type }
            .filter { rule -> rule.conditions.all { matches(it, event, process) } }
            .flatMap { rule -> rule.actions.map { TriggeredAction(rule.id, it.type, it.parameter) } }
            .toList()

    private fun matches(condition: RuleCondition, event: PlatformEvent, process: WorkItemDefinition?): Boolean {
        val primitive = condition.value as? JsonPrimitive
        return when (condition.operator) {
            "exists" -> condition.fact in event.attributes || condition.fact in event.metrics
            "equals" -> {
                val expected = primitive?.content ?: return false
                event.attributes[condition.fact] == expected || event.metrics[condition.fact]?.toString() == expected
            }
            "greater_than" -> {
                val expected = primitive?.doubleOrNull ?: return false
                (event.metrics[condition.fact] ?: return false) > expected
            }
            "greater_than_target_factor" -> {
                val factor = primitive?.doubleOrNull ?: return false
                val actual = event.metrics[condition.fact] ?: return false
                val target = process?.targetDurationSeconds?.toDouble() ?: return false
                actual > target * factor
            }
            else -> false
        }
    }
}
