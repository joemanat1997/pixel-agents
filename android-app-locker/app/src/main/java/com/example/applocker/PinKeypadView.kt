package com.example.applocker

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible

/**
 * iOS-style passcode keypad: a fixed row of progress dots plus a circular 0-9
 * keypad with a backspace key. The PIN has a fixed length ([pinLength]); once
 * the final digit is entered it is reported through [onSubmit] automatically.
 *
 * When [shuffleEnabled] is on, the digit positions are randomised every time the
 * keypad is (re)shown, to defeat shoulder-surfing and screen smudges.
 */
class PinKeypadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val entered = StringBuilder()

    var pinLength: Int = DEFAULT_PIN_LENGTH
        set(value) {
            field = value
            reset()
        }

    var shuffleEnabled: Boolean = false
        set(value) {
            field = value
            applyKeypad()
        }

    var onSubmit: ((String) -> Unit)? = null

    private val titleView by lazy { findViewById<TextView>(R.id.keypadTitle) }
    private val subtitleView by lazy { findViewById<TextView>(R.id.keypadSubtitle) }
    private val dotsContainer by lazy { findViewById<LinearLayout>(R.id.dotsContainer) }
    private val errorView by lazy { findViewById<TextView>(R.id.keypadError) }

    private val dotSize = dp(16)
    private val dotGap = dp(11)

    // Visual top-to-bottom order of the ten number keys.
    private val keyPositions = intArrayOf(
        R.id.key1, R.id.key2, R.id.key3,
        R.id.key4, R.id.key5, R.id.key6,
        R.id.key7, R.id.key8, R.id.key9,
        R.id.key0
    )
    private val defaultLetters = mapOf(
        2 to "ABC", 3 to "DEF", 4 to "GHI", 5 to "JKL",
        6 to "MNO", 7 to "PQRS", 8 to "TUV", 9 to "WXYZ"
    )

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.view_pin_keypad, this, true)
        findViewById<View>(R.id.keyBackspace).setOnClickListener { backspace() }
        // Fixed-length PIN auto-submits, so the confirm key is unnecessary.
        findViewById<View>(R.id.keyEnter).visibility = View.INVISIBLE
        applyKeypad()
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

    /** Clears the entered digits (and re-shuffles the keypad when shuffling is on). */
    fun reset() {
        entered.setLength(0)
        if (shuffleEnabled) applyKeypad()
        refreshDots()
    }

    /** Binds each physical key to a digit, shuffling the assignment when enabled. */
    private fun applyKeypad() {
        val digits = if (shuffleEnabled) (0..9).shuffled() else listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
        keyPositions.forEachIndexed { index, keyId ->
            bindKey(keyId, digits[index])
        }
    }

    private fun bindKey(keyId: Int, digit: Int) {
        val key = findViewById<LinearLayout>(keyId)
        val numberView = key.getChildAt(0) as TextView
        val lettersView = key.getChildAt(1) as TextView
        numberView.text = digit.toString()
        if (shuffleEnabled) {
            lettersView.visibility = View.GONE
        } else {
            lettersView.visibility = View.VISIBLE
            lettersView.text = defaultLetters[digit] ?: " "
        }
        key.setOnClickListener { append('0' + digit) }
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
