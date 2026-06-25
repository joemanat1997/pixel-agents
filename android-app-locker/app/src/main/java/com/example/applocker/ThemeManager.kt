package com.example.applocker

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.annotation.StyleRes
import com.google.android.material.color.MaterialColors

/** Accent themes the user can choose from in Settings. */
object ThemeManager {

    const val DEFAULT = "teal"

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
        (themes.firstOrNull { it.key == key } ?: themes.first()).styleRes

    fun swatchFor(key: String): Int =
        (themes.firstOrNull { it.key == key } ?: themes.first()).swatch

    /** A context carrying the user's selected accent theme (for the overlay window). */
    fun themedContext(context: Context, key: String): Context =
        ContextThemeWrapper(context, styleFor(key))

    /** Resolves the current accent color from a (themed) context. */
    fun accentColor(context: Context): Int =
        MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorPrimary,
            0xFF2FB8AC.toInt()
        )
}
