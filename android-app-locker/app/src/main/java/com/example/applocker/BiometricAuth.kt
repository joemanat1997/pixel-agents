package com.example.applocker

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi

/**
 * Thin wrapper over the framework BiometricPrompt (API 28+). The framework
 * prompt — unlike the AndroidX one — only needs a Context, so it can be shown
 * from the overlay window as well as from activities.
 *
 * Version-specific classes are kept in @RequiresApi helpers so older devices
 * never have to verify code that references classes they don't ship.
 */
object BiometricAuth {

    /** True if the device has biometric hardware with at least one enrolled credential. */
    fun isAvailable(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> availableViaBiometricManager(context)
        Build.VERSION.SDK_INT == Build.VERSION_CODES.P -> availableViaFingerprint(context)
        else -> false
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun availableViaBiometricManager(context: Context): Boolean {
        val bm = context.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.P)
    private fun availableViaFingerprint(context: Context): Boolean {
        val fm = context.getSystemService(FingerprintManager::class.java) ?: return false
        return fm.isHardwareDetected && fm.hasEnrolledFingerprints()
    }

    /**
     * Shows the system biometric prompt. [onSuccess] runs on a correct scan;
     * [onCancel] runs if the user dismisses it or taps the "use PIN" button, so
     * the caller can fall back to the keypad.
     */
    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onCancel()
            return
        }
        authenticateApi28(context, title, subtitle, negativeText, onSuccess, onCancel)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun authenticateApi28(
        context: Context,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onCancel: () -> Unit
    ) {
        val shown = runCatching {
            val executor = context.mainExecutor
            val prompt = BiometricPrompt.Builder(context)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton(negativeText, executor) { _, _ -> onCancel() }
                .build()

            prompt.authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        onCancel()
                    }
                    // A single failed attempt keeps the prompt open — nothing to do.
                }
            )
        }.isSuccess
        if (!shown) onCancel()
    }
}
