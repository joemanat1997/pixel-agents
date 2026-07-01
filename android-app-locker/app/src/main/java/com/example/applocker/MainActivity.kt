package com.example.applocker

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
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

    private val previewOverlay by lazy { LockOverlay(this) }
    private var appsLoaded = false

    private val authLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        isAuthenticating = false
        if (result.resultCode == RESULT_OK) {
            SessionState.appUnlocked = true
            binding.root.visibility = View.VISIBLE
        } else {
            // User backed out of the passcode prompt — leave the app.
            finishAffinity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SecurePrefs.get(this)
        setTheme(ThemeManager.styleFor(prefs.themeName))
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            if (isChecked && !FingerprintAuthenticator(this).isAvailable()) {
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
        binding.instantLockRow.setOnClickListener {
            navigateInternally(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.overlayRow.setOnClickListener { requestOverlayPermission() }
        binding.testLockButton.setOnClickListener { testLockNow() }

        setupBottomNav(savedInstanceState?.getInt(KEY_TAB) ?: R.id.nav_dashboard)
        setupThemePicker()
        setupLanguagePicker()
        setupIconPicker()
        setupNightMode()
        applyCustomAccentChrome()
    }

    private fun setupNightMode() {
        val idByMode = mapOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.id.night_system,
            AppCompatDelegate.MODE_NIGHT_NO to R.id.night_light,
            AppCompatDelegate.MODE_NIGHT_YES to R.id.night_dark
        )
        val modeById = idByMode.entries.associate { (mode, id) -> id to mode }
        val checkedId = idByMode[prefs.nightMode] ?: R.id.night_system
        binding.nightGroup.check(checkedId)
        updateNightLabel(checkedId)

        binding.nightHeader.setOnClickListener {
            val show = binding.nightGroup.visibility != View.VISIBLE
            TransitionManager.beginDelayedTransition(binding.nightGroup.parent as ViewGroup)
            binding.nightGroup.visibility = if (show) View.VISIBLE else View.GONE
            binding.nightChevron.animate().rotation(if (show) 180f else 0f).setDuration(150).start()
        }

        binding.nightGroup.setOnCheckedChangeListener { _, id ->
            updateNightLabel(id)
            val mode = modeById[id] ?: return@setOnCheckedChangeListener
            if (mode != prefs.nightMode) {
                prefs.nightMode = mode
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }

    private fun updateNightLabel(id: Int) {
        binding.currentNightLabel.text = findViewById<RadioButton>(id)?.text
    }

    private fun setupIconPicker() {
        binding.currentIconPreview.setImageResource(AppIconManager.previewFor(prefs.iconKey))

        val options = mapOf(
            binding.iconDefault to "default",
            binding.iconDark to "dark",
            binding.iconTeal to "teal",
            binding.iconBlack to "black"
        )
        options.forEach { (view, key) ->
            view.setOnClickListener {
                AppIconManager.apply(this, key)
                binding.currentIconPreview.setImageResource(AppIconManager.previewFor(key))
                markSelectedIcon(options, key)
                Toast.makeText(this, R.string.icon_changed, Toast.LENGTH_SHORT).show()
            }
        }
        markSelectedIcon(options, prefs.iconKey)

        binding.iconHeader.setOnClickListener {
            val show = binding.iconRow.visibility != View.VISIBLE
            TransitionManager.beginDelayedTransition(binding.iconRow.parent as ViewGroup)
            binding.iconRow.visibility = if (show) View.VISIBLE else View.GONE
            binding.iconChevron.animate().rotation(if (show) 180f else 0f).setDuration(150).start()
        }
    }

    private fun markSelectedIcon(options: Map<ImageView, String>, selected: String) {
        options.forEach { (view, key) -> view.alpha = if (key == selected) 1f else 0.4f }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, binding.bottomNav.selectedItemId)
    }

    private fun setupBottomNav(initialTab: Int) {
        binding.bottomNav.setOnItemSelectedListener { item ->
            binding.dashboardPage.visibility =
                if (item.itemId == R.id.nav_dashboard) View.VISIBLE else View.GONE
            binding.appListPage.visibility =
                if (item.itemId == R.id.nav_apps) View.VISIBLE else View.GONE
            binding.settingsPage.visibility =
                if (item.itemId == R.id.nav_settings) View.VISIBLE else View.GONE
            true
        }
        binding.bottomNav.selectedItemId = initialTab
    }

    private fun setupThemePicker() {
        val row = binding.themeSwatchRow
        row.removeAllViews()
        val size = dp(40)
        val margin = dp(6)
        ThemeManager.themes.forEach { theme ->
            val swatch = View(this)
            swatch.layoutParams = GridLayout.LayoutParams().apply {
                width = size
                height = size
                setMargins(margin, margin, margin, margin)
            }
            swatch.background = makeSwatch(theme.swatch, theme.key == prefs.themeName)
            swatch.setOnClickListener {
                if (prefs.themeName != theme.key) {
                    prefs.themeName = theme.key
                    recreate()
                }
            }
            row.addView(swatch)
        }

        // Free-color swatch: a rainbow chip that opens the custom color picker.
        val custom = View(this)
        custom.layoutParams = GridLayout.LayoutParams().apply {
            width = size
            height = size
            setMargins(margin, margin, margin, margin)
        }
        custom.background = makeRainbowSwatch(prefs.themeName == ThemeManager.CUSTOM)
        custom.setOnClickListener { openColorPicker() }
        row.addView(custom)

        binding.themeHeader.setOnClickListener {
            val show = binding.themeSwatchRow.visibility != View.VISIBLE
            TransitionManager.beginDelayedTransition(binding.themeSwatchRow.parent as ViewGroup)
            binding.themeSwatchRow.visibility = if (show) View.VISIBLE else View.GONE
            binding.themeChevron.animate().rotation(if (show) 180f else 0f).setDuration(150).start()
        }
    }

    private fun bindPermissionState(label: TextView, granted: Boolean) {
        label.text = getString(if (granted) R.string.state_granted else R.string.state_grant)
        label.setTextColor(getColor(if (granted) R.color.success else R.color.text_secondary))
    }

    private fun makeSwatch(color: Int, selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (selected) setStroke(dp(3), Color.WHITE)
        }

    private fun makeRainbowSwatch(selected: Boolean): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                0xFFFF3B30.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(),
                0xFF00C7BE.toInt(), 0xFF007AFF.toInt(), 0xFFAF52DE.toInt()
            )
        ).apply {
            shape = GradientDrawable.OVAL
            if (selected) setStroke(dp(3), Color.WHITE)
        }

    /** Slide-based HSV picker so the user can dial in any accent color they like. */
    private fun openColorPicker() {
        val initial = if (prefs.themeName == ThemeManager.CUSTOM) prefs.customAccent
        else ThemeManager.accentColor(this)

        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val preview = dialogView.findViewById<View>(R.id.colorPreview)
        val hueSeek = dialogView.findViewById<SeekBar>(R.id.hueSeek)
        val satSeek = dialogView.findViewById<SeekBar>(R.id.satSeek)
        val valSeek = dialogView.findViewById<SeekBar>(R.id.valSeek)
        val hexLabel = dialogView.findViewById<TextView>(R.id.hexValue)

        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)
        hueSeek.progress = hsv[0].toInt()
        satSeek.progress = (hsv[1] * 100).toInt()
        valSeek.progress = (hsv[2] * 100).toInt()

        val previewBg = GradientDrawable().apply { cornerRadius = dp(14).toFloat() }
        preview.background = previewBg

        fun current(): Int = Color.HSVToColor(
            floatArrayOf(hueSeek.progress.toFloat(), satSeek.progress / 100f, valSeek.progress / 100f)
        )
        fun refresh() {
            val c = current()
            previewBg.setColor(c)
            hexLabel.text = String.format("#%06X", 0xFFFFFF and c)
        }
        refresh()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = refresh()
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }
        hueSeek.setOnSeekBarChangeListener(listener)
        satSeek.setOnSeekBarChangeListener(listener)
        valSeek.setOnSeekBarChangeListener(listener)

        AlertDialog.Builder(this)
            .setTitle(R.string.color_pick_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.customAccent = current()
                prefs.themeName = ThemeManager.CUSTOM
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * A free custom color can't live in an XML theme, so tint the accent-colored
     * chrome (nav bar, switches, indicators) programmatically when it's selected.
     * Preset themes color themselves through the theme, so this is a no-op for them.
     */
    private fun applyCustomAccentChrome() {
        if (prefs.themeName != ThemeManager.CUSTOM) return
        val accent = prefs.customAccent
        binding.themeCurrentDot.imageTintList = ColorStateList.valueOf(accent)
        binding.testLockButton.setTextColor(accent)

        val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val navTint = ColorStateList(
            checkedStates, intArrayOf(accent, getColor(R.color.text_secondary))
        )
        binding.bottomNav.itemIconTintList = navTint
        binding.bottomNav.itemTextColor = navTint

        val trackTint = ColorStateList(checkedStates, intArrayOf(accent, 0x4D9E9E9E))
        listOf(
            binding.protectionSwitch, binding.selfLockSwitch,
            binding.biometricSwitch, binding.shuffleSwitch
        ).forEach { it.trackTintList = trackTint }
    }

    private fun setupLanguagePicker() {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val checkedId = LANG_ID_BY_TAG.entries.firstOrNull { current.startsWith(it.key) }?.value
            ?: R.id.lang_en
        binding.languageGroup.check(checkedId)
        updateCurrentLanguageLabel(checkedId)

        binding.languageHeader.setOnClickListener {
            val show = binding.languageGroup.visibility != View.VISIBLE
            TransitionManager.beginDelayedTransition(binding.languageGroup.parent as ViewGroup)
            binding.languageGroup.visibility = if (show) View.VISIBLE else View.GONE
            binding.langChevron.animate().rotation(if (show) 180f else 0f).setDuration(150).start()
        }

        binding.languageGroup.setOnCheckedChangeListener { _, id ->
            updateCurrentLanguageLabel(id)
            val tag = LANG_TAG_BY_ID[id] ?: return@setOnCheckedChangeListener
            val now = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (now.startsWith(tag)) return@setOnCheckedChangeListener
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    private fun updateCurrentLanguageLabel(checkedId: Int) {
        binding.currentLanguageLabel.text =
            findViewById<RadioButton>(checkedId)?.text ?: getString(R.string.lang_english)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
        refreshState()
        // Loading every installed app's icon is heavy; do it once per launch.
        if (!appsLoaded) {
            appsLoaded = true
            loadInstalledApps()
        }
    }

    // Re-locking on background is handled at the process level (see AppLockerApp)
    // so it fires exactly when the whole app leaves the foreground — not on
    // rotation or when stepping into one of our own sub-screens.

    /** Launches the passcode gate if app self-lock is on and not yet unlocked. */
    private fun maybeRequireAuth() {
        if (isAuthenticating) return
        if (SessionState.appUnlocked || !prefs.isPinSet || !prefs.appSelfLock) {
            binding.root.visibility = View.VISIBLE
            return
        }
        // Hide the sensitive content (locked-app list, toggles) so it can't be
        // glimpsed for the frame before the opaque gate covers it.
        binding.root.visibility = View.INVISIBLE
        isAuthenticating = true
        authLauncher.launch(Intent(this, AppAuthActivity::class.java))
    }

    private fun navigateInternally(intent: Intent) {
        // Stepping into one of our own screens or a system settings page keeps the
        // app in the foreground, so the process-level self-lock does NOT re-lock —
        // the user isn't asked for the PIN again until the whole app is actually
        // backgrounded.
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

        bindPermissionState(binding.instantLockState, AppLockAccessibilityService.isEnabled(this))
        bindPermissionState(binding.overlayStateLabel, hasOverlayPermission())

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

        val biometricAvailable = FingerprintAuthenticator(this).isAvailable()
        binding.biometricSwitch.setOnCheckedChangeListener(null)
        binding.biometricSwitch.isEnabled = biometricAvailable
        binding.biometricSwitch.isChecked = biometricAvailable && prefs.biometricEnabled
        binding.biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !FingerprintAuthenticator(this).isAvailable()) {
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
            !AppLockAccessibilityService.isEnabled(this) -> getString(R.string.status_need_accessibility) to false
            !hasOverlayPermission() -> getString(R.string.status_need_overlay) to false
            !prefs.serviceEnabled -> getString(R.string.status_off) to false
            prefs.lockedPackages.isEmpty() -> getString(R.string.status_ready) to true
            else -> getString(R.string.status_active, prefs.lockedPackages.size) to true
        }
        binding.statusBanner.text = text
        binding.statusBanner.backgroundTintList = android.content.res.ColorStateList.valueOf(
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
        if (!AppLockAccessibilityService.isEnabled(this)) {
            Toast.makeText(this, R.string.status_need_accessibility, Toast.LENGTH_LONG).show()
            binding.protectionSwitch.isChecked = false
            navigateInternally(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
        private const val KEY_TAB = "selected_tab"
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
