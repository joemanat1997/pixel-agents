package com.example.applocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/**
 * Listens for a fingerprint WITHOUT showing the system BiometricPrompt sheet,
 * so the app can draw its own themed fingerprint UI. Fingerprint only (face
 * unlock still uses the keypad). Uses the framework FingerprintManager which is
 * deprecated but still the only way to authenticate without system UI.
 */
@Suppress("DEPRECATION")
class FingerprintAuthenticator(private val context: Context) {

    private val fm: FingerprintManager? =
        context.getSystemService(FingerprintManager::class.java)
    private var cancellation: CancellationSignal? = null

    fun isAvailable(): Boolean {
        val manager = fm ?: return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return runCatching { manager.isHardwareDetected && manager.hasEnrolledFingerprints() }
            .getOrDefault(false)
    }

    fun start(
        onSuccess: () -> Unit,
        onFailed: () -> Unit,
        onError: (CharSequence?) -> Unit
    ) {
        val manager = fm ?: return
        stop()
        val signal = CancellationSignal()
        cancellation = signal
        runCatching {
            manager.authenticate(
                null, signal, 0,
                object : FingerprintManager.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
                        onSuccess()
                    }

                    override fun onAuthenticationFailed() {
                        onFailed()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        // Ignore cancellations (we stop/restart it ourselves).
                        if (errorCode != FingerprintManager.FINGERPRINT_ERROR_CANCELED &&
                            errorCode != ERROR_USER_CANCELED
                        ) {
                            onError(errString)
                        }
                    }
                },
                null
            )
        }
    }

    fun stop() {
        cancellation?.let { if (!it.isCanceled) it.cancel() }
        cancellation = null
    }

    private companion object {
        const val ERROR_USER_CANCELED = 10
    }
}
