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
        Theme("green", R.style.Theme_AppLocker_Green, 0xFF34C759.toInt())
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
