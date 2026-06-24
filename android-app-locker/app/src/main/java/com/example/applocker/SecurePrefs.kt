package com.example.applocker

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted persistent storage for the app PIN (hashed) and the set of
 * locked package names.
 */
class SecurePrefs private constructor(private val prefs: SharedPreferences) {

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        private set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var pinSalt: String?
        get() = prefs.getString(KEY_PIN_SALT, null)
        private set(value) = prefs.edit().putString(KEY_PIN_SALT, value).apply()

    val isPinSet: Boolean
        get() = pinHash != null && pinSalt != null

    var lockedPackages: Set<String>
        get() = prefs.getStringSet(KEY_LOCKED, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_LOCKED, value).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Stores a brand-new PIN (used for first setup and for changing the PIN). */
    fun setPin(pin: String) {
        val salt = PinHasher.newSalt()
        pinSalt = salt
        pinHash = PinHasher.hash(pin, salt)
    }

    fun verifyPin(pin: String): Boolean {
        val hash = pinHash ?: return false
        val salt = pinSalt ?: return false
        return PinHasher.verify(pin, salt, hash)
    }

    fun setLocked(pkg: String, locked: Boolean) {
        val updated = lockedPackages.toMutableSet()
        if (locked) updated.add(pkg) else updated.remove(pkg)
        lockedPackages = updated
    }

    companion object {
        private const val FILE = "applocker_secure_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_LOCKED = "locked_packages"
        private const val KEY_ENABLED = "service_enabled"

        @Volatile
        private var instance: SecurePrefs? = null

        fun get(context: Context): SecurePrefs {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): SecurePrefs {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return SecurePrefs(prefs)
        }
    }
}
