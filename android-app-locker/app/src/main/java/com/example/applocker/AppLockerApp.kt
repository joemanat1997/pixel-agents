package com.example.applocker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** Applies the saved night-mode choice for the whole process at startup. */
class AppLockerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(SecurePrefs.get(this).nightMode)
    }
}
