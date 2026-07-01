package com.example.applocker

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Instant app-lock detection. Unlike the UsageStats poll (which lags ~0.7s),
 * an accessibility service is notified the moment a window comes to the front,
 * so the PIN overlay can appear with no visible gap.
 *
 * It only reads the foreground package name from window-change events — it does
 * NOT retrieve screen content (canRetrieveWindowContent=false).
 */
class AppLockAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SecurePrefs
    private lateinit var lockOverlay: LockOverlay
    private val launchable = HashMap<String, Boolean>()
    private var lastForegroundPackage = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = SecurePrefs.get(this)
        lockOverlay = LockOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        handleForeground(pkg)
    }

    private fun handleForeground(current: String) {
        if (!::prefs.isInitialized) return
        if (!prefs.serviceEnabled || !prefs.isPinSet) return
        if (current.isEmpty() || current == packageName) return
        // Ignore system UI, keyboards, etc. — only react to real launchable apps.
        if (!isLaunchable(current)) return

        if (current != lastForegroundPackage) {
            if (lastForegroundPackage.isNotEmpty()) SessionState.relock(lastForegroundPackage)
            lastForegroundPackage = current
        }

        if (current in prefs.lockedPackages &&
            !SessionState.isUnlocked(current) &&
            !SessionState.lockPromptShowing
        ) {
            SessionState.lockPromptShowing = true
            lockOverlay.show(current)
        }
    }

    private fun isLaunchable(pkg: String): Boolean =
        launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (::lockOverlay.isInitialized) lockOverlay.remove()
    }

    companion object {
        /** True if the user has turned this accessibility service on in Settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val component =
                ComponentName(context, AppLockAccessibilityService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(component, ignoreCase = true) }
        }
    }
}
