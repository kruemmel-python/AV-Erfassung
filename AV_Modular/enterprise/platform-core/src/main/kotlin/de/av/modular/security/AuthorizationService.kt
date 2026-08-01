package de.av.modular.security

import de.av.modular.model.CustomerProfile

data class Principal(
    val id: String,
    val tenantId: String,
    val roleIds: Set<String>,
    val locationIds: Set<String> = emptySet(),
)

data class AccessRequest(
    val permission: String,
    val tenantId: String,
    val locationId: String? = null,
)

data class AuthorizationDecision(
    val granted: Boolean,
    val reason: String,
)

class AuthorizationService(private val profile: CustomerProfile) {
    private val permissionsByRole = profile.roles.associate { it.id to it.permissions.toSet() }

    fun authorize(principal: Principal, request: AccessRequest): AuthorizationDecision {
        if (principal.tenantId != profile.tenantId || request.tenantId != profile.tenantId) {
            return AuthorizationDecision(false, "Mandantengrenze verletzt")
        }
        if (request.locationId != null && principal.locationIds.isNotEmpty() && request.locationId !in principal.locationIds) {
            return AuthorizationDecision(false, "Standort nicht freigegeben")
        }
        val knownRoles = principal.roleIds.filter(permissionsByRole::containsKey)
        if (knownRoles.isEmpty()) return AuthorizationDecision(false, "Keine gültige Rolle")
        val permissions = knownRoles.flatMap { permissionsByRole.getValue(it) }.toSet()
        val granted = request.permission in permissions || "*" in permissions
        return AuthorizationDecision(granted, if (granted) "Freigegeben" else "Berechtigung fehlt: ${request.permission}")
    }

    fun require(principal: Principal, request: AccessRequest) {
        val decision = authorize(principal, request)
        if (!decision.granted) throw SecurityException(decision.reason)
    }
}
