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

    /** Whether opening the App Locker app itself requires the PIN. On by default. */
    var appSelfLock: Boolean
        get() = prefs.getBoolean(KEY_SELF_LOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_SELF_LOCK, value).apply()

    /** Allow unlocking with a fingerprint in addition to the PIN. On by default. */
    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    /** Shuffle the number positions on the keypad each time it is shown. */
    var shuffleKeypad: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE, value).apply()

    /** Selected accent theme key (see ThemeManager). */
    var themeName: String
        get() = prefs.getString(KEY_THEME, ThemeManager.DEFAULT) ?: ThemeManager.DEFAULT
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** Free custom accent color (ARGB int) used when themeName == ThemeManager.CUSTOM. */
    var customAccent: Int
        get() = prefs.getInt(KEY_CUSTOM_ACCENT, ThemeManager.swatchFor(ThemeManager.DEFAULT))
        set(value) = prefs.edit().putInt(KEY_CUSTOM_ACCENT, value).apply()

    /** Selected launcher icon key (see AppIconManager). */
    var iconKey: String
        get() = prefs.getString(KEY_ICON, AppIconManager.DEFAULT) ?: AppIconManager.DEFAULT
        set(value) = prefs.edit().putString(KEY_ICON, value).apply()

    /** Night mode: one of AppCompatDelegate.MODE_NIGHT_* (default: follow system). */
    var nightMode: Int
        get() = prefs.getInt(KEY_NIGHT, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_NIGHT, value).apply()

    // --- Lock screen personalisation ---

    /** Lock background style: one of LockStyle.GRADIENT / SOLID / IMAGE. */
    var lockBackgroundStyle: String
        get() = prefs.getString(KEY_LOCK_BG_STYLE, LockStyle.GRADIENT) ?: LockStyle.GRADIENT
        set(value) = prefs.edit().putString(KEY_LOCK_BG_STYLE, value).apply()

    /** content:// URI of the user-picked lock wallpaper, or null. */
    var lockBackgroundImage: String?
        get() = prefs.getString(KEY_LOCK_BG_IMAGE, null)
        set(value) = prefs.edit().putString(KEY_LOCK_BG_IMAGE, value).apply()

    /** Custom greeting shown on the lock screen; blank falls back to the app name. */
    var lockGreeting: String
        get() = prefs.getString(KEY_LOCK_GREETING, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LOCK_GREETING, value).apply()

    /** Whether to show the padlock icon on the lock screen. On by default. */
    var lockShowIcon: Boolean
        get() = prefs.getBoolean(KEY_LOCK_SHOW_ICON, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_SHOW_ICON, value).apply()

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

    // --- Brute-force lockout ---

    /** Milliseconds remaining in the current lockout, or 0 if not locked out. */
    fun lockoutRemainingMs(): Long {
        val until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun isLockedOut(): Boolean = lockoutRemainingMs() > 0L

    /** Records a wrong PIN; triggers a timed lockout once the threshold is hit. */
    fun recordFailedAttempt() {
        val attempts = prefs.getInt(KEY_FAILED, 0) + 1
        if (attempts >= MAX_ATTEMPTS) {
            prefs.edit()
                .putInt(KEY_FAILED, 0)
                .putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_MS)
                .apply()
        } else {
            prefs.edit().putInt(KEY_FAILED, attempts).apply()
        }
    }

    fun resetFailedAttempts() {
        prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply()
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
        private const val KEY_SELF_LOCK = "app_self_lock"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_SHUFFLE = "shuffle_keypad"
        private const val KEY_THEME = "accent_theme"
        private const val KEY_CUSTOM_ACCENT = "custom_accent"
        private const val KEY_ICON = "launcher_icon"
        private const val KEY_NIGHT = "night_mode"
        private const val KEY_LOCK_BG_STYLE = "lock_bg_style"
        private const val KEY_LOCK_BG_IMAGE = "lock_bg_image"
        private const val KEY_LOCK_GREETING = "lock_greeting"
        private const val KEY_LOCK_SHOW_ICON = "lock_show_icon"
        private const val KEY_FAILED = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 30_000L

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
