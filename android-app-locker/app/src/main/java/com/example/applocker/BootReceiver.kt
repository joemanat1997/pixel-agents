package com.example.applocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the lock service after the device reboots, if it was enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = SecurePrefs.get(context)
        if (prefs.serviceEnabled && prefs.isPinSet) {
            AppLockService.start(context)
        }
    }
}
