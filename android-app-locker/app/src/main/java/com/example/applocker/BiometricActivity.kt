package com.example.applocker

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Invisible host for the system fingerprint prompt over a locked app.
 *
 * The framework BiometricPrompt needs a real (activity) window to show, so the
 * lock overlay can't display it directly. This transparent activity is launched
 * on top of the overlay, shows the prompt, and on success unlocks the target
 * app and dismisses the overlay; on cancel it just finishes, leaving the PIN
 * keypad in place.
 */
class BiometricActivity : AppCompatActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val prefs = SecurePrefs.get(this)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)

        if (!prefs.biometricEnabled || !BiometricAuth.isAvailable(this)) {
            finish()
            return
        }

        BiometricAuth.authenticate(
            context = this,
            title = title,
            subtitle = getString(R.string.biometric_subtitle),
            negativeText = getString(R.string.use_pin_instead),
            onSuccess = {
                handled = true
                prefs.resetFailedAttempts()
                SessionState.markUnlocked(pkg)
                LockOverlay.dismissActive()
                finish()
            },
            onCancel = {
                handled = true
                finish()
            }
        )
    }

    override fun onPause() {
        super.onPause()
        // If the prompt is dismissed for any other reason, don't linger.
        if (handled) overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_TITLE = "extra_title"
    }
}
