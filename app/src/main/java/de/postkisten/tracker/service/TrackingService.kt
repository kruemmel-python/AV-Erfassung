package de.postkisten.tracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.postkisten.tracker.MainActivity
import de.postkisten.tracker.R
import de.postkisten.tracker.TrackerApplication
import de.postkisten.tracker.data.DeviceClock
import de.postkisten.tracker.data.asDuration
import de.postkisten.tracker.data.grossMillis
import de.postkisten.tracker.data.netMillis
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private val manager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Aktive Postkiste", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Postkiste wird geladen …", "Zeiterfassung ist aktiv"))
        ticker?.cancel()
        ticker = scope.launch {
            val repository = (application as TrackerApplication).repository
            repository.active.combine(repository.activeProcess) { box, process -> box to process }
                .collectLatest { (active, process) ->
                if (active == null && process == null) {
                    stopSelf()
                } else {
                    while (isActive) {
                        val clock = DeviceClock.snapshot(this@TrackingService)
                        val startedAt = process?.startedAtUtc ?: active!!.box.startedAtUtc
                        val started = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                            .format(startedAt.let(java.time.Instant::ofEpochMilli))
                        val title = process?.let { "${it.type.label} läuft" }
                            ?: "${active!!.box.type.label} ${active.box.displayNumber} läuft"
                        val duration = process?.grossMillis(clock.utcMillis)?.asDuration()
                            ?: active!!.netMillis(clock).asDuration()
                        manager.notify(NOTIFICATION_ID, notification(title, "Start: $started · Dauer: $duration"))
                        delay(1_000)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun notification(title: String, text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_recent_history)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ))
        .build()

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "active_box"
        private const val NOTIFICATION_ID = 1001
        fun start(context: Context) = context.startForegroundService(Intent(context, TrackingService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, TrackingService::class.java))
    }
}
