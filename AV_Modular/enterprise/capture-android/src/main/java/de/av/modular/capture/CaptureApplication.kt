package de.av.modular.capture

import android.app.Application
import android.content.RestrictionsManager
import androidx.room.Room
import de.av.modular.config.ConfigurationParser
import de.av.modular.runtime.PlatformRuntime
import de.av.modular.security.DevelopmentProfileVerifier
import de.av.modular.capture.data.CaptureDatabase
import de.av.modular.capture.data.CaptureRepository

class CaptureApplication : Application() {
    lateinit var runtime: PlatformRuntime
        private set
    lateinit var repository: CaptureRepository
        private set
    lateinit var managedConfiguration: ManagedConfiguration
        private set

    override fun onCreate() {
        super.onCreate()
        managedConfiguration = ManagedConfiguration.load(getSystemService(RestrictionsManager::class.java))
        val parser = ConfigurationParser()
        val module = parser.loadModule { relative -> assets.open("${managedConfiguration.moduleId}/$relative").bufferedReader().use { it.readText() } }
        val profile = parser.loadProfile(assets.open("${managedConfiguration.profileId}/profile.json").bufferedReader().use { it.readText() })
        runtime = PlatformRuntime.create(module, profile, DevelopmentProfileVerifier)
        val database = Room.databaseBuilder(this, CaptureDatabase::class.java, "av_capture.db")
            .addMigrations(CaptureDatabase.MIGRATION_1_2)
            .build()
        repository = CaptureRepository(
            database, runtime,
            defaultLocationId = managedConfiguration.locationId,
            sourceDeviceId = managedConfiguration.deviceId,
        )
    }
}
