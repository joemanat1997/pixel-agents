package com.example.applocker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.applocker.databinding.ActivityLockScreenBinding

/**
 * Full-screen passcode prompt shown over a locked app. The user must enter the
 * correct app PIN on the keypad to continue; pressing back simply sends them to
 * the home screen rather than into the protected app.
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private lateinit var prefs: SecurePrefs
    private var targetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show above the lock screen / keyguard and keep it on top.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SecurePrefs.get(this)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()

        binding.keypad.apply {
            setTitle(appLabelFor(targetPackage))
            setSubtitle(getString(R.string.enter_pin_to_open))
            onSubmit = { pin -> attemptUnlock(pin) }
        }

        // Back press goes home instead of revealing the locked app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })
    }

    private fun attemptUnlock(pin: String) {
        if (prefs.verifyPin(pin)) {
            SessionState.markUnlocked(targetPackage)
            SessionState.lockPromptShowing = false
            finish()
        } else {
            binding.keypad.showError(getString(R.string.error_wrong_pin))
        }
    }

    private fun goHome() {
        SessionState.lockPromptShowing = false
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    private fun appLabelFor(pkg: String): String {
        return runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(getString(R.string.app_name))
    }

    override fun onUserLeaveHint() {
        // If the user swipes home, clear the prompt flag so it can re-trigger.
        SessionState.lockPromptShowing = false
        super.onUserLeaveHint()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }
}
