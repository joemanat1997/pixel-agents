package com.example.applocker

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.view.isVisible

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

    @Volatile
    private var view: View? = null
    private var currentKey: String? = null

    fun isShowing(): Boolean = view != null

    /** Real lock shown over a protected app. */
    fun show(packageName: String) {
        present(
            key = packageName,
            title = appLabelFor(packageName),
            subtitle = appContext.getString(R.string.enter_pin_to_open),
            onCorrect = {
                SessionState.markUnlocked(packageName)
                dismiss()
            },
            onBack = {
                goHome()
                dismiss()
            }
        )
    }

    /** Preview/self-test triggered from inside the app to prove the overlay works. */
    fun showPreview() {
        present(
            key = PREVIEW_KEY,
            title = appContext.getString(R.string.test_lock_title),
            subtitle = appContext.getString(R.string.test_lock_subtitle),
            onCorrect = { dismiss() },
            onBack = { dismiss() }
        )
    }

    private fun present(
        key: String,
        title: CharSequence,
        subtitle: CharSequence,
        onCorrect: () -> Unit,
        onBack: () -> Unit
    ) {
        if (view != null) {
            if (key == currentKey) return
            remove()
        }
        currentKey = key
        SessionState.lockPromptShowing = true

        val themed = ThemeManager.themedContext(appContext, prefs.themeName, prefs.nightMode)
        val root = LayoutInflater.from(themed).inflate(R.layout.view_lock_overlay, null)
        val keypad = root.findViewById<PinKeypadView>(R.id.keypad)

        keypad.shuffleEnabled = prefs.shuffleKeypad
        keypad.setTitle(title)
        keypad.setSubtitle(subtitle)
        keypad.onSubmit = { pin ->
            when {
                prefs.isLockedOut() -> keypad.showError(lockoutMessage())
                prefs.verifyPin(pin) -> {
                    prefs.resetFailedAttempts()
                    onCorrect()
                }
                else -> {
                    prefs.recordFailedAttempt()
                    keypad.showError(
                        if (prefs.isLockedOut()) lockoutMessage()
                        else appContext.getString(R.string.error_wrong_pin)
                    )
                }
            }
        }

        val biometricOk = prefs.biometricEnabled && BiometricAuth.isAvailable(appContext)
        val fingerprintButton = root.findViewById<ImageView>(R.id.fingerprintButton)
        fingerprintButton.isVisible = biometricOk
        fingerprintButton.setOnClickListener { promptBiometric(key, title.toString()) }

        root.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onBack()
                true
            } else {
                false
            }
        }

        // addView throws if the "display over other apps" permission is missing
        // or was revoked; fail gracefully instead of crashing.
        val added = runCatching { windowManager.addView(root, buildParams()) }.isSuccess
        if (added) {
            root.requestFocus()
            view = root
            active = this
            if (biometricOk) promptBiometric(key, title.toString())
        } else {
            currentKey = null
            SessionState.lockPromptShowing = false
        }
    }

    /** Launches the transparent activity that hosts the system fingerprint prompt. */
    private fun promptBiometric(pkg: String, title: String) {
        val intent = Intent(appContext, BiometricActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(BiometricActivity.EXTRA_PACKAGE, pkg)
            putExtra(BiometricActivity.EXTRA_TITLE, title)
        }
        runCatching { appContext.startActivity(intent) }
    }

    private fun lockoutMessage(): String {
        val seconds = ((prefs.lockoutRemainingMs() + 999) / 1000).toInt()
        return appContext.getString(R.string.error_locked_out, seconds)
    }

    /** Soft fade + slight zoom-out, then remove — a gentle reveal of the app behind. */
    fun dismiss() {
        val v = view
        if (v == null) {
            remove()
            return
        }
        v.animate()
            .alpha(0f)
            .scaleX(1.06f)
            .scaleY(1.06f)
            .setDuration(DISMISS_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { remove() }
            .start()
    }

    fun remove() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        currentKey = null
        if (active === this) active = null
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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SECURE,
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

    companion object {
        private const val PREVIEW_KEY = "__preview__"
        private const val DISMISS_MS = 240L

        @Volatile
        private var active: LockOverlay? = null

        /** Dismisses the currently-shown lock overlay (called after a fingerprint unlock). */
        fun dismissActive() {
            val overlay = active ?: return
            Handler(Looper.getMainLooper()).post { overlay.dismiss() }
        }
    }
}
