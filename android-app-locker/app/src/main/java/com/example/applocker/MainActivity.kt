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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.applocker.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SecurePrefs
    private lateinit var adapter: AppListAdapter

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SecurePrefs.get(this)

        adapter = AppListAdapter(mutableListOf()) { app, locked ->
            prefs.setLocked(app.packageName, locked)
        }
        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter

        binding.protectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            onProtectionToggled(isChecked)
        }

        binding.setPinButton.setOnClickListener {
            startActivity(Intent(this, PinSetupActivity::class.java))
        }
        binding.usageAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.overlayButton.setOnClickListener { requestOverlayPermission() }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        loadInstalledApps()
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
            startActivity(Intent(this, PinSetupActivity::class.java))
            return
        }
        if (!hasUsageAccess()) {
            Toast.makeText(this, R.string.error_need_usage, Toast.LENGTH_LONG).show()
            binding.protectionSwitch.isChecked = false
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
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
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
    }
}
