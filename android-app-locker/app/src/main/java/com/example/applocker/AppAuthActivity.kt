package com.example.applocker

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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

        binding.keypad.apply {
            shuffleEnabled = prefs.shuffleKeypad
            setTitle(getString(R.string.app_name))
            setSubtitle(getString(R.string.enter_pin_to_enter_app))
            onSubmit = { pin -> attempt(pin) }
        }

        val biometricOk = prefs.biometricEnabled && BiometricAuth.isAvailable(this)
        binding.fingerprintButton.isVisible = biometricOk
        binding.fingerprintButton.setOnClickListener { promptBiometric() }
        if (biometricOk) promptBiometric()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_CANCELED)
                finish()
            }
        })
    }

    private fun promptBiometric() {
        BiometricAuth.authenticate(
            context = this,
            title = getString(R.string.app_name),
            subtitle = getString(R.string.biometric_subtitle),
            negativeText = getString(R.string.use_pin_instead),
            onSuccess = {
                prefs.resetFailedAttempts()
                SessionState.appUnlocked = true
                setResult(RESULT_OK)
                finish()
            },
            onCancel = { /* fall back to the PIN keypad */ }
        )
    }

    private fun attempt(pin: String) {
        when {
            prefs.isLockedOut() -> binding.keypad.showError(lockoutMessage())
            prefs.verifyPin(pin) -> {
                prefs.resetFailedAttempts()
                SessionState.appUnlocked = true
                setResult(RESULT_OK)
                finish()
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
