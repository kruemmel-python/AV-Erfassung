package de.av.modular.capture

import android.content.RestrictionsManager

data class ManagedConfiguration(
    val moduleId: String,
    val profileId: String,
    val locationId: String,
    val deviceId: String,
    val allowLegacyImport: Boolean,
) {
    companion object {
        private val safeId = Regex("^[a-z][a-z0-9_]{1,63}$")

        fun load(manager: RestrictionsManager?): ManagedConfiguration {
            val restrictions = manager?.applicationRestrictions
            fun id(key: String, fallback: String): String = restrictions?.getString(key)
                ?.takeIf(safeId::matches) ?: fallback
            return ManagedConfiguration(
                moduleId = id("av_module_id", "mail_processing"),
                profileId = id("av_profile_id", "demo_dhl"),
                locationId = id("av_location_id", "default"),
                deviceId = id("av_device_id", "unmanaged_device"),
                allowLegacyImport = restrictions?.getBoolean("av_allow_legacy_import", false) ?: false,
            )
        }
    }
}
