package com.example.applocker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.applocker.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SecurePrefs
    private lateinit var adapter: AppListAdapter

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // App self-lock gating.
    private var isAuthenticating = false
    private var leavingForInternalNav = false

    private val previewOverlay by lazy { LockOverlay(this) }
    private var appsLoaded = false

    private val authLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        isAuthenticating = false
        if (result.resultCode == RESULT_OK) {
            SessionState.appUnlocked = true
        } else {
            // User backed out of the passcode prompt — leave the app.
            finishAffinity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SecurePrefs.get(this)

        adapter = AppListAdapter(mutableListOf()) { app, locked ->
            prefs.setLocked(app.packageName, locked)
            updateStatusBanner()
        }
        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter

        binding.protectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            onProtectionToggled(isChecked)
        }

        binding.selfLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            onSelfLockToggled(isChecked)
        }

        binding.biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !BiometricAuth.isAvailable(this)) {
                Toast.makeText(this, R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
                binding.biometricSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            prefs.biometricEnabled = isChecked
        }

        binding.shuffleSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.shuffleKeypad = isChecked
        }

        binding.setPinButton.setOnClickListener {
            navigateInternally(Intent(this, PinSetupActivity::class.java))
        }
        binding.usageAccessButton.setOnClickListener {
            navigateInternally(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.overlayButton.setOnClickListener { requestOverlayPermission() }
        binding.testLockButton.setOnClickListener { testLockNow() }

        setupBottomNav()
        setupLanguagePicker()
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            binding.dashboardPage.visibility =
                if (item.itemId == R.id.nav_dashboard) android.view.View.VISIBLE else android.view.View.GONE
            binding.appListPage.visibility =
                if (item.itemId == R.id.nav_apps) android.view.View.VISIBLE else android.view.View.GONE
            binding.settingsPage.visibility =
                if (item.itemId == R.id.nav_settings) android.view.View.VISIBLE else android.view.View.GONE
            true
        }
        binding.bottomNav.selectedItemId = R.id.nav_dashboard
    }

    private fun setupLanguagePicker() {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val checkedId = LANG_ID_BY_TAG.entries.firstOrNull { current.startsWith(it.key) }?.value
            ?: R.id.lang_en
        binding.languageGroup.check(checkedId)

        binding.languageGroup.setOnCheckedChangeListener { _, id ->
            val tag = LANG_TAG_BY_ID[id] ?: return@setOnCheckedChangeListener
            val now = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (now.startsWith(tag)) return@setOnCheckedChangeListener
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    /**
     * One-tap diagnostic: if the overlay permission is missing it says so and
     * opens the right settings page; otherwise it shows the lock screen
     * immediately, proving the lock UI works without relying on the service.
     */
    private fun testLockNow() {
        if (!prefs.isPinSet) {
            Toast.makeText(this, R.string.test_need_pin, Toast.LENGTH_LONG).show()
            navigateInternally(Intent(this, PinSetupActivity::class.java))
            return
        }
        if (!hasOverlayPermission()) {
            Toast.makeText(this, R.string.test_need_overlay, Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        previewOverlay.showPreview()
    }

    override fun onStart() {
        super.onStart()
        maybeRequireAuth()
    }

    override fun onResume() {
        super.onResume()
        leavingForInternalNav = false
        refreshState()
        // Loading every installed app's icon is heavy; do it once per launch.
        if (!appsLoaded) {
            appsLoaded = true
            loadInstalledApps()
        }
    }

    override fun onStop() {
        super.onStop()
        // Re-lock the app when it goes to the background, but not when we are
        // showing the passcode prompt or stepping into one of our own screens.
        if (!isAuthenticating && !leavingForInternalNav) {
            SessionState.appUnlocked = false
        }
    }

    /** Launches the passcode gate if app self-lock is on and not yet unlocked. */
    private fun maybeRequireAuth() {
        if (isAuthenticating || SessionState.appUnlocked) return
        if (!prefs.isPinSet || !prefs.appSelfLock) return
        isAuthenticating = true
        authLauncher.launch(Intent(this, AppAuthActivity::class.java))
    }

    private fun navigateInternally(intent: Intent) {
        leavingForInternalNav = true
        startActivity(intent)
    }

    private fun onSelfLockToggled(enabled: Boolean) {
        if (enabled && !prefs.isPinSet) {
            Toast.makeText(this, R.string.error_set_pin_first, Toast.LENGTH_LONG).show()
            binding.selfLockSwitch.isChecked = false
            navigateInternally(Intent(this, PinSetupActivity::class.java))
            return
        }
        prefs.appSelfLock = enabled
    }

    private fun refreshState() {
        val pinSet = prefs.isPinSet
        binding.setPinButton.text = getString(
            if (pinSet) R.string.change_pin else R.string.set_pin
        )

        val usageGranted = hasUsageAccess()
        val overlayGranted = hasOverlayPermission()

        binding.usageAccessButton.isEnabled = !usageGranted
        binding.usageAccessButton.text = getString(
            if (usageGranted) R.string.usage_granted else R.string.grant_usage_access
        )
        binding.overlayButton.isEnabled = !overlayGranted
        binding.overlayButton.text = getString(
            if (overlayGranted) R.string.overlay_granted else R.string.grant_overlay
        )

        binding.protectionSwitch.setOnCheckedChangeListener(null)
        binding.protectionSwitch.isChecked = prefs.serviceEnabled
        binding.protectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            onProtectionToggled(isChecked)
        }

        binding.selfLockSwitch.setOnCheckedChangeListener(null)
        binding.selfLockSwitch.isChecked = pinSet && prefs.appSelfLock
        binding.selfLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            onSelfLockToggled(isChecked)
        }

        val biometricAvailable = BiometricAuth.isAvailable(this)
        binding.biometricSwitch.setOnCheckedChangeListener(null)
        binding.biometricSwitch.isEnabled = biometricAvailable
        binding.biometricSwitch.isChecked = biometricAvailable && prefs.biometricEnabled
        binding.biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !BiometricAuth.isAvailable(this)) {
                Toast.makeText(this, R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
                binding.biometricSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            prefs.biometricEnabled = isChecked
        }

        binding.shuffleSwitch.setOnCheckedChangeListener(null)
        binding.shuffleSwitch.isChecked = prefs.shuffleKeypad
        binding.shuffleSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.shuffleKeypad = isChecked
        }

        updateStatusBanner()
    }

    /** Shows exactly what (if anything) is preventing the lock from working. */
    private fun updateStatusBanner() {
        val (text, ok) = when {
            !prefs.isPinSet -> getString(R.string.status_no_pin) to false
            !hasUsageAccess() -> getString(R.string.status_need_usage) to false
            !hasOverlayPermission() -> getString(R.string.status_need_overlay) to false
            !prefs.serviceEnabled -> getString(R.string.status_off) to false
            prefs.lockedPackages.isEmpty() -> getString(R.string.status_ready) to true
            else -> getString(R.string.status_active, prefs.lockedPackages.size) to true
        }
        binding.statusBanner.text = text
        binding.statusBanner.setBackgroundColor(
            getColor(if (ok) R.color.success else R.color.warning)
        )
        binding.statusBanner.setTextColor(
            getColor(if (ok) R.color.text_primary else R.color.on_primary)
        )
    }

    private fun onProtectionToggled(enabled: Boolean) {
        if (!enabled) {
            prefs.serviceEnabled = false
            AppLockService.stop(this)
            return
        }

        // Guard rails before turning protection on.
        if (!prefs.isPinSet) {
            Toast.makeText(this, R.string.error_set_pin_first, Toast.LENGTH_LONG).show()
            binding.protectionSwitch.isChecked = false
            navigateInternally(Intent(this, PinSetupActivity::class.java))
            return
        }
        if (!hasUsageAccess()) {
            Toast.makeText(this, R.string.error_need_usage, Toast.LENGTH_LONG).show()
            binding.protectionSwitch.isChecked = false
            navigateInternally(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        if (!hasOverlayPermission()) {
            Toast.makeText(this, R.string.error_need_overlay, Toast.LENGTH_LONG).show()
            binding.protectionSwitch.isChecked = false
            requestOverlayPermission()
            return
        }

        prefs.serviceEnabled = true
        AppLockService.start(this)
    }

    private fun loadInstalledApps() {
        ioExecutor.execute {
            val pm = packageManager
            val locked = prefs.lockedPackages
            val launchable = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != packageName }
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null || isUserApp(it) }
                .map {
                    InstalledApp(
                        packageName = it.packageName,
                        label = pm.getApplicationLabel(it).toString(),
                        icon = pm.getApplicationIcon(it),
                        locked = locked.contains(it.packageName)
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()

            mainHandler.post { adapter.submit(launchable) }
        }
    }

    private fun isUserApp(info: ApplicationInfo): Boolean {
        val mask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return (info.flags and mask) == 0
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        navigateInternally(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }

    companion object {
        private val LANG_ID_BY_TAG = linkedMapOf(
            "th" to R.id.lang_th,
            "zh" to R.id.lang_zh,
            "ja" to R.id.lang_ja,
            "es" to R.id.lang_es,
            "nb" to R.id.lang_nb,
            "en" to R.id.lang_en
        )
        private val LANG_TAG_BY_ID = LANG_ID_BY_TAG.entries.associate { (tag, id) -> id to tag }
    }
}
