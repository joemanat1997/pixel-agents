package com.example.applocker

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.applocker.databinding.ActivityPinSetupBinding

/**
 * Sets the app PIN for the first time, or changes an existing one, using a
 * multi-step keypad flow. This PIN is stored (hashed) by the app itself and is
 * independent of the phone's unlock code, so the user can choose something
 * different on purpose.
 */
class PinSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinSetupBinding
    private lateinit var prefs: SecurePrefs

    private var changing = false
    private var step = STEP_NEW
    private var pendingNewPin = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SecurePrefs.get(this)
        setTheme(ThemeManager.styleFor(prefs.themeName))
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityPinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        changing = prefs.isPinSet

        binding.keypad.pinLength = PIN_LENGTH
        binding.keypad.onSubmit = { pin -> handleStep(pin) }

        goToStep(if (changing) STEP_CURRENT else STEP_NEW)
    }

    private fun handleStep(pin: String) {
        when (step) {
            STEP_CURRENT -> {
                if (prefs.verifyPin(pin)) {
                    goToStep(STEP_NEW)
                } else {
                    binding.keypad.showError(getString(R.string.error_current_pin_wrong))
                }
            }
            STEP_NEW -> {
                pendingNewPin = pin
                goToStep(STEP_CONFIRM)
            }
            STEP_CONFIRM -> {
                if (pin == pendingNewPin) {
                    prefs.setPin(pin)
                    Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    pendingNewPin = ""
                    goToStep(STEP_NEW)
                    binding.keypad.showError(getString(R.string.error_pin_mismatch))
                }
            }
        }
    }

    private fun goToStep(next: Int) {
        step = next
        binding.keypad.reset()
        binding.keypad.clearError()
        when (next) {
            STEP_CURRENT -> {
                binding.keypad.setTitle(getString(R.string.title_change_pin))
                binding.keypad.setSubtitle(getString(R.string.prompt_current_pin))
            }
            STEP_NEW -> {
                binding.keypad.setTitle(
                    getString(if (changing) R.string.title_new_pin else R.string.title_set_pin)
                )
                binding.keypad.setSubtitle(getString(R.string.prompt_new_pin))
            }
            STEP_CONFIRM -> {
                binding.keypad.setTitle(getString(R.string.title_confirm_pin))
                binding.keypad.setSubtitle(getString(R.string.prompt_confirm_pin))
            }
        }
    }

    companion object {
        private const val PIN_LENGTH = 6
        private const val STEP_CURRENT = 0
        private const val STEP_NEW = 1
        private const val STEP_CONFIRM = 2
    }
}
