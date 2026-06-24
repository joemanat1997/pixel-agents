package com.example.applocker

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.ContextThemeWrapper

/**
 * Draws the PIN lock screen directly as a system overlay window (using the
 * "display over other apps" permission) instead of launching an Activity.
 * Activity launches from a background service are blocked on modern Android, so
 * an overlay window is the reliable way to cover a locked app.
 */
class LockOverlay(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = SecurePrefs.get(appContext)

    private var view: View? = null
    private var currentPackage: String? = null

    fun isShowing(): Boolean = view != null

    fun show(packageName: String) {
        if (view != null) {
            if (packageName == currentPackage) return
            remove()
        }
        currentPackage = packageName
        SessionState.lockPromptShowing = true

        val themed = ContextThemeWrapper(appContext, R.style.Theme_AppLocker_Lock)
        val root = LayoutInflater.from(themed).inflate(R.layout.view_lock_overlay, null)
        val keypad = root.findViewById<PinKeypadView>(R.id.keypad)

        keypad.setTitle(appLabelFor(packageName))
        keypad.setSubtitle(appContext.getString(R.string.enter_pin_to_open))
        keypad.onSubmit = { pin ->
            if (prefs.verifyPin(pin)) {
                SessionState.markUnlocked(packageName)
                remove()
            } else {
                keypad.showError(appContext.getString(R.string.error_wrong_pin))
            }
        }

        // Back key sends the user home instead of into the locked app.
        root.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                goHome()
                remove()
                true
            } else {
                false
            }
        }

        // addView throws if the "display over other apps" permission is missing
        // or was revoked; fail gracefully instead of crashing the service.
        val added = runCatching { windowManager.addView(root, buildParams()) }.isSuccess
        if (added) {
            root.requestFocus()
            view = root
        } else {
            currentPackage = null
            SessionState.lockPromptShowing = false
        }
    }

    fun remove() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        currentPackage = null
        SessionState.lockPromptShowing = false
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Focusable (no FLAG_NOT_FOCUSABLE) so we receive the back key; fills
            // the whole screen and stays on top of the locked app.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun appLabelFor(pkg: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(appContext.getString(R.string.app_name))

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(home)
    }
}
