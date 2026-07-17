package de.postkisten.tracker

import android.app.Application
import de.postkisten.tracker.data.TrackerDatabase
import de.postkisten.tracker.data.TrackerRepository

class TrackerApplication : Application() {
    val database by lazy { TrackerDatabase.get(this) }
    val repository by lazy { TrackerRepository(this, database) }
}
