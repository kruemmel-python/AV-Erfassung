package de.av.modular.license

import de.av.modular.model.LicenseDefinition
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

data class LicenseDecision(val allowed: Boolean, val reason: String)

class LicenseService(private val license: LicenseDefinition, private val clock: Clock = Clock.systemUTC()) {
    fun module(moduleId: String): LicenseDecision {
        if (expired()) return LicenseDecision(false, "Lizenz ${license.licenseId} ist abgelaufen")
        return if (moduleId in license.modules) LicenseDecision(true, "Modul lizenziert")
        else LicenseDecision(false, "Modul nicht lizenziert: $moduleId")
    }

    fun feature(featureId: String): LicenseDecision {
        if (expired()) return LicenseDecision(false, "Lizenz ${license.licenseId} ist abgelaufen")
        return if (license.features[featureId] == true) LicenseDecision(true, "Feature lizenziert")
        else LicenseDecision(false, "Feature nicht lizenziert: $featureId")
    }

    private fun expired(): Boolean = LocalDate.parse(license.validUntil) < LocalDate.now(clock.withZone(ZoneOffset.UTC))
}
