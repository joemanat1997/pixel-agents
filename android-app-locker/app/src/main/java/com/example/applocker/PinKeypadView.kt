package com.example.applocker

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible

/**
 * iOS-style passcode keypad: a fixed row of progress dots plus a circular 0-9
 * keypad with a backspace key. The PIN has a fixed length ([pinLength]); once
 * the final digit is entered it is reported through [onSubmit] automatically,
 * so no separate confirm key is needed.
 */
class PinKeypadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val entered = StringBuilder()

    /** Fixed number of digits in the PIN. */
    var pinLength: Int = DEFAULT_PIN_LENGTH
        set(value) {
            field = value
            reset()
        }

    var onSubmit: ((String) -> Unit)? = null

    private val titleView by lazy { findViewById<android.widget.TextView>(R.id.keypadTitle) }
    private val subtitleView by lazy { findViewById<android.widget.TextView>(R.id.keypadSubtitle) }
    private val dotsContainer by lazy { findViewById<LinearLayout>(R.id.dotsContainer) }
    private val errorView by lazy { findViewById<android.widget.TextView>(R.id.keypadError) }

    private val dotSize = dp(16)
    private val dotGap = dp(11)

    init {
        orientation = VERTICAL
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.view_pin_keypad, this, true)
        wireKeys()
        // Fixed-length PIN auto-submits, so the confirm key is unnecessary.
        findViewById<View>(R.id.keyEnter).visibility = View.INVISIBLE
        refreshDots()
    }

    fun setTitle(text: CharSequence) {
        titleView.text = text
    }

    fun setSubtitle(text: CharSequence?) {
        subtitleView.text = text
        subtitleView.isVisible = !text.isNullOrEmpty()
    }

    fun showError(message: CharSequence) {
        errorView.text = message
        reset()
    }

    fun clearError() {
        errorView.text = ""
    }

    /** Clears the entered digits without touching the title/subtitle. */
    fun reset() {
        entered.setLength(0)
        refreshDots()
    }

    private fun wireKeys() {
        val digits = mapOf(
            R.id.key0 to '0', R.id.key1 to '1', R.id.key2 to '2', R.id.key3 to '3',
            R.id.key4 to '4', R.id.key5 to '5', R.id.key6 to '6', R.id.key7 to '7',
            R.id.key8 to '8', R.id.key9 to '9'
        )
        for ((id, digit) in digits) {
            findViewById<View>(id).setOnClickListener { append(digit) }
        }
        findViewById<View>(R.id.keyBackspace).setOnClickListener { backspace() }
    }

    private fun append(digit: Char) {
        if (entered.length >= pinLength) return
        clearError()
        entered.append(digit)
        refreshDots()
        if (entered.length == pinLength) {
            onSubmit?.invoke(entered.toString())
        }
    }

    private fun backspace() {
        if (entered.isEmpty()) return
        clearError()
        entered.setLength(entered.length - 1)
        refreshDots()
    }

    /** Shows exactly [pinLength] dots, filled up to the number of entered digits. */
    private fun refreshDots() {
        dotsContainer.removeAllViews()
        for (i in 0 until pinLength) {
            val dot = ImageView(context)
            val lp = LayoutParams(dotSize, dotSize)
            lp.marginStart = if (i == 0) 0 else dotGap
            dot.layoutParams = lp
            dot.setImageResource(if (i < entered.length) R.drawable.dot_filled else R.drawable.dot_empty)
            dotsContainer.addView(dot)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val DEFAULT_PIN_LENGTH = 6
    }
}
