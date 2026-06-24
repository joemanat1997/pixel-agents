package com.example.applocker

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.applocker.databinding.ActivityPinSetupBinding

/**
 * Sets the app PIN for the first time, or changes an existing one. This PIN is
 * stored (hashed) by the app itself and is independent of the phone's unlock
 * code, so the user can choose something different on purpose.
 */
class PinSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinSetupBinding
    private lateinit var prefs: SecurePrefs
    private var changing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SecurePrefs.get(this)
        changing = prefs.isPinSet

        binding.currentPinLayout.visibility = if (changing) View.VISIBLE else View.GONE
        binding.title.text = getString(
            if (changing) R.string.title_change_pin else R.string.title_set_pin
        )

        binding.saveButton.setOnClickListener { save() }
    }

    private fun save() {
        val newPin = binding.newPin.text?.toString().orEmpty()
        val confirmPin = binding.confirmPin.text?.toString().orEmpty()

        if (changing) {
            val currentPin = binding.currentPin.text?.toString().orEmpty()
            if (!prefs.verifyPin(currentPin)) {
                showError(getString(R.string.error_current_pin_wrong))
                return
            }
        }

        if (newPin.length < MIN_PIN_LENGTH) {
            showError(getString(R.string.error_pin_too_short, MIN_PIN_LENGTH))
            return
        }
        if (newPin != confirmPin) {
            showError(getString(R.string.error_pin_mismatch))
            return
        }

        prefs.setPin(newPin)
        Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    companion object {
        private const val MIN_PIN_LENGTH = 4
    }
}
