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
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Foreground service that polls the current foreground app. When a locked app
 * comes to the front and has not been unlocked this session, it launches the
 * PIN lock screen on top of it.
 */
class AppLockService : Service() {

    private lateinit var prefs: SecurePrefs
    private lateinit var usageStats: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastForegroundPackage: String = ""

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Re-lock everything when the screen turns off.
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                SessionState.relockAll()
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = SecurePrefs.get(this)
        usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    private fun checkForeground() {
        if (!prefs.serviceEnabled || !prefs.isPinSet) return

        val current = queryForegroundPackage() ?: return
        if (current.isEmpty()) return

        // Our own lock screen and main UI must never lock themselves.
        if (current == packageName) {
            lastForegroundPackage = current
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

        val locked = prefs.lockedPackages.contains(current)
        if (locked && !SessionState.isUnlocked(current) && !SessionState.lockPromptShowing) {
            launchLockScreen(current)
        }
    }

    /** Returns the package most recently moved to the foreground. */
    private fun queryForegroundPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - QUERY_WINDOW_MS
        val events = usageStats.queryEvents(begin, end)
        var pkg: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                pkg = event.packageName
            }
        }
        return pkg
    }

    private fun launchLockScreen(pkg: String) {
        SessionState.lockPromptShowing = true
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(LockScreenActivity.EXTRA_PACKAGE, pkg)
        }
        startActivity(intent)
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
        handler.removeCallbacks(pollRunnable)
        runCatching { unregisterReceiver(screenReceiver) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val POLL_INTERVAL_MS = 500L
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
