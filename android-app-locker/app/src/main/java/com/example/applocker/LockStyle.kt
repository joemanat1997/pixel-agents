package com.example.applocker

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import androidx.core.graphics.ColorUtils

/**
 * Builds the lock-screen background so the user can style it their own way:
 * an accent-tinted gradient, a flat dark colour, or one of their own photos.
 * Kept in one place so the overlay window and the app's self-lock screen look
 * identical.
 */
object LockStyle {

    const val GRADIENT = "gradient"
    const val SOLID = "solid"
    const val IMAGE = "image"

    private const val BASE_DARK = 0xFF0A0A14.toInt()

    /** Returns the background drawable for the current lock-screen style. */
    fun background(context: Context, accent: Int): Drawable {
        val prefs = SecurePrefs.get(context)
        return when (prefs.lockBackgroundStyle) {
            SOLID -> ColorDrawable(ColorUtils.blendARGB(accent, BASE_DARK, 0.86f))
            IMAGE -> imageBackground(context, prefs.lockBackgroundImage) ?: gradient(accent)
            else -> gradient(accent)
        }
    }

    /** A soft top-to-bottom accent gradient fading into near-black. */
    private fun gradient(accent: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            ColorUtils.blendARGB(accent, BASE_DARK, 0.62f),
            ColorUtils.blendARGB(accent, BASE_DARK, 0.92f),
            BASE_DARK
        )
    )

    /** Decodes the chosen photo (downsampled) with a dark scrim for readability. */
    private fun imageBackground(context: Context, uriString: String?): Drawable? {
        val uri = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        val bitmap = runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            }
        }.getOrNull() ?: return null

        val photo = BitmapDrawable(context.resources, bitmap)
        // A translucent black scrim so the white keypad/text stays legible.
        val scrim = ColorDrawable(Color.argb(140, 0, 0, 0))
        return LayerDrawable(arrayOf(photo, scrim))
    }
}
