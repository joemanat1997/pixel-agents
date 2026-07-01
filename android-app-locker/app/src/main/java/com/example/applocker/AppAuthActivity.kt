package com.example.applocker

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.applocker.databinding.ActivityAppAuthBinding

/**
 * Passcode gate for the App Locker app itself. Launched by [MainActivity] when
 * app self-lock is enabled and a PIN is set. Returns [Activity.RESULT_OK] on a
 * correct PIN; pressing back cancels and closes the app.
 */
class AppAuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppAuthBinding
    private lateinit var prefs: SecurePrefs
    private var fingerprintAuth: FingerprintAuthenticator? = null
    private var pulseAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SecurePrefs.get(this)
        setTheme(ThemeManager.styleFor(prefs.themeName))
        // Block screenshots / screen recording / recents preview of the lock screen.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityAppAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val accent = ThemeManager.accentColor(this)
        binding.root.background = LockStyle.background(this, accent)
        binding.lockIcon.imageTintList = ColorStateList.valueOf(accent)
        binding.lockIcon.isVisible = prefs.lockShowIcon

        binding.keypad.apply {
            shuffleEnabled = prefs.shuffleKeypad
            setTitle(prefs.lockGreeting.ifBlank { getString(R.string.app_name) })
            setSubtitle(getString(R.string.enter_pin_to_enter_app))
            onSubmit = { pin -> attempt(pin) }
        }

        val authenticator = FingerprintAuthenticator(this)
        val fingerprintOk = prefs.biometricEnabled && authenticator.isAvailable()
        binding.fingerprintArea.isVisible = fingerprintOk
        if (fingerprintOk) {
            fingerprintAuth = authenticator
            binding.fingerprintIcon.setOnClickListener { startFingerprintScan() }
            startFingerprintScan()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_CANCELED)
                finish()
            }
        })
    }

    /** Listens for a fingerprint silently and animates our own themed icon. */
    private fun startFingerprintScan() {
        val authenticator = fingerprintAuth ?: return
        val icon = binding.fingerprintIcon
        val hint = binding.fingerprintHint
        val accent = ThemeManager.accentColor(this)
        val error = ContextCompat.getColor(this, R.color.error)
        val success = ContextCompat.getColor(this, R.color.success)
        icon.imageTintList = ColorStateList.valueOf(accent)
        hint.text = getString(R.string.biometric_subtitle)
        startPulse(icon)
        authenticator.start(
            onSuccess = {
                stopPulse()
                prefs.resetFailedAttempts()
                icon.imageTintList = ColorStateList.valueOf(success)
                unlockAndFinish()
            },
            onFailed = {
                icon.imageTintList = ColorStateList.valueOf(error)
                icon.animate().translationX(-12f).setDuration(60).withEndAction {
                    icon.animate().translationX(12f).setDuration(60).withEndAction {
                        icon.animate().translationX(0f).setDuration(60).start()
                    }.start()
                }.start()
                icon.postDelayed({ icon.imageTintList = ColorStateList.valueOf(accent) }, 650)
            },
            onError = { message ->
                stopPulse()
                hint.text = message ?: getString(R.string.error_wrong_pin)
            }
        )
    }

    private fun startPulse(view: View) {
        stopPulse()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f)
        ).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        fingerprintAuth?.stop()
        stopPulse()
    }

    @Suppress("DEPRECATION")
    private fun unlockAndFinish() {
        SessionState.appUnlocked = true
        setResult(RESULT_OK)
        finish()
        // Soft fade into the app instead of a hard cut.
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun attempt(pin: String) {
        when {
            prefs.isLockedOut() -> binding.keypad.showError(lockoutMessage())
            prefs.verifyPin(pin) -> {
                prefs.resetFailedAttempts()
                unlockAndFinish()
            }
            else -> {
                prefs.recordFailedAttempt()
                binding.keypad.showError(
                    if (prefs.isLockedOut()) lockoutMessage()
                    else getString(R.string.error_wrong_pin)
                )
            }
        }
    }

    private fun lockoutMessage(): String {
        val seconds = ((prefs.lockoutRemainingMs() + 999) / 1000).toInt()
        return getString(R.string.error_locked_out, seconds)
    }
}
