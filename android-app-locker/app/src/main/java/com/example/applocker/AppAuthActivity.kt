package com.example.applocker

import android.app.Activity
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
        binding = ActivityAppAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SecurePrefs.get(this)

        binding.keypad.apply {
            setTitle(getString(R.string.app_name))
            setSubtitle(getString(R.string.enter_pin_to_enter_app))
            onSubmit = { pin -> attempt(pin) }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(RESULT_CANCELED)
                finish()
            }
        })
    }

    private fun attempt(pin: String) {
        if (prefs.verifyPin(pin)) {
            SessionState.appUnlocked = true
            setResult(RESULT_OK)
            finish()
        } else {
            binding.keypad.showError(getString(R.string.error_wrong_pin))
        }
    }
}
