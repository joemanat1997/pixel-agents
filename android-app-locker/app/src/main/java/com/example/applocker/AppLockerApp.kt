package com.example.applocker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Applies the saved night-mode choice for the whole process at startup and
 * drives the app's own self-lock at the PROCESS level.
 *
 * Self-lock previously relied on a single Activity's onStop, which is fragile:
 * it also fires on rotation and when stepping into our own sub-screens, and it
 * could be skipped — leaving the app flagged "unlocked" so the next person
 * opened it with no PIN. ProcessLifecycleOwner instead fires ON_STOP only when
 * the ENTIRE app goes to the background (Home, recents, another app), which is
 * exactly when we want to re-lock. Moving between our own activities and
 * rotating the screen do NOT trigger it.
 */
class AppLockerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(SecurePrefs.get(this).nightMode)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // The whole app went to the background — require the passcode
                // again the next time it is opened.
                SessionState.appUnlocked = false
            }
        })
    }
}
