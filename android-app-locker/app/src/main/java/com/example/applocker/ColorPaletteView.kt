package com.example.applocker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * A full-spectrum colour palette: hue runs left→right, lightness runs
 * top (bright) → bottom (dark). Tap or drag anywhere to pick any accent colour.
 *
 * The gradient is rendered once into a bitmap so the exact colour under the
 * finger can be read straight from the pixel — no colour maths at touch time.
 */
class ColorPaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Called while dragging (isFinal = false) and once on release (isFinal = true). */
    var onColorPicked: ((color: Int, isFinal: Boolean) -> Unit)? = null

    private var bitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var selX = -1f
    private var selY = -1f
    private var selectedColor = Color.WHITE

    private val ringStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = Color.WHITE
    }
    private val ringShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        color = 0x66000000
    }
    private val ringFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Positions the selector ring to match an existing colour. */
    fun setColor(color: Int) {
        selectedColor = color
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        pendingHue = hsl[0]
        pendingLight = hsl[2]
        if (width > 0 && height > 0) positionFromColor()
        invalidate()
    }

    private var pendingHue = 0f
    private var pendingLight = 0.5f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        bitmap = buildPalette(w, h)
        positionFromColor()
    }

    private fun positionFromColor() {
        // Inverse of the mapping in buildPalette: hue → x, lightness → y.
        selX = (pendingHue / 360f) * width
        selY = ((LIGHT_TOP - pendingLight) / (LIGHT_TOP - LIGHT_BOTTOM)).coerceIn(0f, 1f) * height
    }

    private fun buildPalette(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Horizontal rainbow of hues.
        val hues = IntArray(HUE_STOPS + 1) { i ->
            ColorUtils.HSLToColor(floatArrayOf(i * 360f / HUE_STOPS, 1f, 0.5f))
        }
        val huePaint = Paint()
        huePaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f, hues, null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), huePaint)

        // Vertical light→hue→dark overlay so every column spans bright to dark.
        val overlayPaint = Paint()
        overlayPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.WHITE, 0x00FFFFFF, Color.BLACK),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), overlayPaint)
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        if (selX >= 0f && selY >= 0f) {
            val r = dp(9f)
            ringFill.color = selectedColor
            canvas.drawCircle(selX, selY, r, ringShadow)
            canvas.drawCircle(selX, selY, r, ringFill)
            canvas.drawCircle(selX, selY, r, ringStroke)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bmp = bitmap ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                selX = event.x.coerceIn(0f, (width - 1).toFloat())
                selY = event.y.coerceIn(0f, (height - 1).toFloat())
                selectedColor = bmp.getPixel(selX.toInt(), selY.toInt())
                invalidate()
                onColorPicked?.invoke(selectedColor, event.action == MotionEvent.ACTION_UP)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val HUE_STOPS = 12
        const val LIGHT_TOP = 0.85f
        const val LIGHT_BOTTOM = 0.15f
    }
}
