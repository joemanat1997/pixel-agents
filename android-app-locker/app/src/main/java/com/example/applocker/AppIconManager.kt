package com.example.applocker

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the launcher icon between a set of predefined activity-aliases.
 * Android can't use an arbitrary user image as the launcher icon, but it can
 * enable one alias (each with its own icon) while disabling the others.
 */
object AppIconManager {

    const val DEFAULT = "default"

    data class Option(val key: String, val alias: String, val previewMipmap: Int)

    val options: List<Option> = listOf(
        Option("default", "IconDefault", R.mipmap.ic_launcher),
        Option("dark", "IconDark", R.mipmap.ic_launcher_dark),
        Option("teal", "IconTeal", R.mipmap.ic_launcher_teal),
        Option("black", "IconBlack", R.mipmap.ic_launcher_black)
    )

    fun previewFor(key: String): Int =
        (options.firstOrNull { it.key == key } ?: options.first()).previewMipmap

    /** Enables the chosen icon's alias and disables the rest. */
    fun apply(context: Context, key: String) {
        val pm = context.packageManager
        val pkg = context.packageName
        val chosen = options.firstOrNull { it.key == key } ?: options.first()

        // Enable the chosen one first so at least one launcher entry stays active.
        pm.setComponentEnabledSetting(
            ComponentName(pkg, "$pkg.${chosen.alias}"),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        options.filter { it.key != key }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg.${it.alias}"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        SecurePrefs.get(context).iconKey = key
    }
}
