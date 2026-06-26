package com.example.applocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Foreground service that watches the current foreground app and, when a locked
 * app comes to the front, shows the PIN overlay on top of it.
 *
 * To stay light on resources the foreground polling runs on a dedicated
 * background thread (never the UI thread), pauses entirely while the screen is
 * off, and short-circuits when nothing is locked.
 */
class AppLockService : Service() {

    private lateinit var prefs: SecurePrefs
    private lateinit var usageStats: UsageStatsManager
    private lateinit var lockOverlay: LockOverlay

    private lateinit var pollThread: HandlerThread
    private lateinit var pollHandler: Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var polling = false
    private var lastForegroundPackage: String = ""

    // Reused on the poll thread only, to avoid per-tick allocations.
    private val reusableEvent = UsageEvents.Event()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Stop scanning and re-lock everything while the screen is off.
                    stopPolling()
                    SessionState.relockAll()
                    LockOverlay.dismissActive()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startPolling()
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            checkForeground()
            if (polling) pollHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = SecurePrefs.get(this)
        usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        lockOverlay = LockOverlay(this)

        pollThread = HandlerThread("AppLockPoll").apply { start() }
        pollHandler = Handler(pollThread.looper)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        pollHandler.removeCallbacks(pollRunnable)
        pollHandler.post(pollRunnable)
    }

    private fun stopPolling() {
        polling = false
        pollHandler.removeCallbacks(pollRunnable)
    }

    /** Runs on the poll thread. */
    private fun checkForeground() {
        if (!prefs.serviceEnabled || !prefs.isPinSet) return
        // The accessibility service (when enabled) handles locking instantly, so
        // skip the polling path to avoid two detectors fighting over the overlay.
        if (AppLockAccessibilityService.isEnabled(this)) return

        // Nothing to guard — skip the (relatively expensive) usage query entirely.
        val lockedPackages = prefs.lockedPackages
        if (lockedPackages.isEmpty()) return

        val current = queryForegroundPackage() ?: return
        if (current.isEmpty() || current == packageName) {
            if (current == packageName) lastForegroundPackage = current
            return
        }

        // When the foreground app changes, re-lock the previous app so it
        // requires the PIN again next time it is opened.
        if (current != lastForegroundPackage) {
            if (lastForegroundPackage.isNotEmpty()) {
                SessionState.relock(lastForegroundPackage)
            }
            lastForegroundPackage = current
        }

        if (current in lockedPackages &&
            !SessionState.isUnlocked(current) &&
            !SessionState.lockPromptShowing
        ) {
            // Guard immediately so the next poll tick won't post a second show
            // before the main thread has put the overlay up.
            SessionState.lockPromptShowing = true
            mainHandler.post { lockOverlay.show(current) }
        }
    }

    /** Returns the package most recently moved to the foreground (poll thread). */
    private fun queryForegroundPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - QUERY_WINDOW_MS
        val events = usageStats.queryEvents(begin, end)
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(reusableEvent)
            if (reusableEvent.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                reusableEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                pkg = reusableEvent.packageName
            }
        }
        return pkg
    }

    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.notif_channel_desc) }
            manager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_lock)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        pollThread.quitSafely()
        lockOverlay.remove()
        runCatching { unregisterReceiver(screenReceiver) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val POLL_INTERVAL_MS = 700L
        private const val QUERY_WINDOW_MS = 2_000L
        private const val CHANNEL_ID = "app_lock_service"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AppLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppLockService::class.java))
        }
    }
}
