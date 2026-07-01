package com.example.applocker

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.MaterialColors

/** Accent themes the user can choose from in Settings. */
object ThemeManager {

    const val DEFAULT = "teal"

    /** Special key meaning "use the user's free-picked custom accent color". */
    const val CUSTOM = "custom"

    data class Theme(val key: String, val styleRes: Int, val swatch: Int)

    val themes: List<Theme> = listOf(
        Theme("teal", R.style.Theme_AppLocker_Teal, 0xFF2FB8AC.toInt()),
        Theme("orange", R.style.Theme_AppLocker_Orange, 0xFFFF7A1A.toInt()),
        Theme("blue", R.style.Theme_AppLocker_Blue, 0xFF4F8CFF.toInt()),
        Theme("violet", R.style.Theme_AppLocker_Violet, 0xFFA78BFA.toInt()),
        Theme("rose", R.style.Theme_AppLocker_Rose, 0xFFFB7185.toInt()),
        Theme("green", R.style.Theme_AppLocker_Green, 0xFF34C759.toInt()),
        Theme("red", R.style.Theme_AppLocker_Red, 0xFFF25555.toInt()),
        Theme("amber", R.style.Theme_AppLocker_Amber, 0xFFF5A623.toInt()),
        Theme("cyan", R.style.Theme_AppLocker_Cyan, 0xFF22C8E0.toInt()),
        Theme("indigo", R.style.Theme_AppLocker_Indigo, 0xFF6C72F0.toInt()),
        Theme("pink", R.style.Theme_AppLocker_Pink, 0xFFEC4899.toInt()),
        Theme("lime", R.style.Theme_AppLocker_Lime, 0xFF9CCC65.toInt())
    )

    @StyleRes
    fun styleFor(key: String): Int =
        // Custom accent is applied programmatically over a neutral base theme.
        if (key == CUSTOM) R.style.Theme_AppLocker_Teal
        else (themes.firstOrNull { it.key == key } ?: themes.first()).styleRes

    fun swatchFor(key: String): Int =
        (themes.firstOrNull { it.key == key } ?: themes.first()).swatch

    /**
     * A context carrying the user's selected accent theme AND night mode, so a
     * non-activity surface (the overlay window) matches the rest of the app.
     */
    fun themedContext(context: Context, key: String, nightMode: Int): Context {
        val config = Configuration(context.resources.configuration)
        val nightFlag = when (nightMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
            AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
            else -> config.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightFlag
        val configContext = context.createConfigurationContext(config)
        return ContextThemeWrapper(configContext, styleFor(key))
    }

    /**
     * Resolves the current accent color. When the user picked a free custom
     * color it returns exactly that; otherwise it reads the selected preset
     * theme's colorPrimary from the (themed) context.
     */
    fun accentColor(context: Context): Int {
        val prefs = SecurePrefs.get(context)
        if (prefs.themeName == CUSTOM) return prefs.customAccent
        return MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorPrimary,
            0xFF2FB8AC.toInt()
        )
    }
}
