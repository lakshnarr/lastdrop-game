package earth.lastdrop.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * StatusLedBar - Shows connection status for Board, Dice, and Web
 * Simple LED indicators with text labels
 */
class StatusLedBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class LedState {
        OFFLINE,    // Red
        CONNECTING, // Yellow (blinking)
        ONLINE      // Green
    }

    data class LedItem(
        var label: String,
        var state: LedState = LedState.OFFLINE,
        var extraText: String = "" // e.g., "87%" for battery
    )

    private val ledItems = mutableListOf(
        LedItem("Board"),
        LedItem("Dice"),
        LedItem("Web")
    )

    private var twoDiceMode = false
    private var dice1Battery = -1
    private var dice2Battery = -1

    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3a3f50")
        style = Paint.Style.FILL
    }

    private var blinkAlpha = 1f
    private var blinkAnimator: ValueAnimator? = null

    init {
        startBlinkAnimation()
    }

    private fun startBlinkAnimation() {
        blinkAnimator = ValueAnimator.ofFloat(1f, 0.3f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { 
                blinkAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blinkAnimator?.cancel()
    }

    fun setBoardState(state: LedState) {
        ledItems[0].state = state
        invalidate()
    }

    fun setDiceState(state: LedState, battery1: Int = -1, battery2: Int = -1) {
        ledItems[1].state = state
        dice1Battery = battery1
        dice2Battery = battery2
        
        // Update extra text
        ledItems[1].extraText = when {
            twoDiceMode && battery1 >= 0 && battery2 >= 0 -> "$battery1%/$battery2%"
            battery1 >= 0 -> "$battery1%"
            else -> ""
        }
        invalidate()
    }

    fun setWebState(state: LedState) {
        ledItems[2].state = state
        invalidate()
    }

    fun setTwoDiceMode(enabled: Boolean) {
        twoDiceMode = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val ledRadius = 12f
        val itemWidth = width / (ledItems.size + if (twoDiceMode) 1 else 0).toFloat()
        val centerY = height / 2f

        ledItems.forEachIndexed { index, item ->
            val centerX = itemWidth * (index + 0.5f)

            // Draw LED circle
            ledPaint.color = when (item.state) {
                LedState.ONLINE -> Color.parseColor("#4CAF50")     // Green
                LedState.CONNECTING -> Color.parseColor("#FFC107") // Yellow
                LedState.OFFLINE -> Color.parseColor("#F44336")    // Red
            }
            
            // Apply blink for connecting state
            if (item.state == LedState.CONNECTING) {
                ledPaint.alpha = (blinkAlpha * 255).toInt()
            } else {
                ledPaint.alpha = 255
            }

            // LED glow
            ledPaint.setShadowLayer(8f, 0f, 0f, ledPaint.color)
            canvas.drawCircle(centerX - 30f, centerY - 8f, ledRadius, ledPaint)
            ledPaint.clearShadowLayer()

            // Label
            canvas.drawText(item.label, centerX + 10f, centerY - 4f, labelPaint)

            // Extra text (battery)
            if (item.extraText.isNotEmpty()) {
                textPaint.textSize = 20f
                canvas.drawText(item.extraText, centerX + 10f, centerY + 18f, textPaint)
            }
        }

        // Draw 2-Dice badge if enabled
        if (twoDiceMode) {
            val badgeX = width - 60f
            val badgeY = centerY
            
            badgePaint.color = Color.parseColor("#3a3f50")
            canvas.drawRoundRect(
                badgeX - 35f, badgeY - 16f,
                badgeX + 35f, badgeY + 16f,
                8f, 8f, badgePaint
            )
            
            textPaint.textSize = 22f
            textPaint.color = Color.parseColor("#4FC3F7")
            canvas.drawText("🎲🎲 2-Dice", badgeX, badgeY + 7f, textPaint)
            textPaint.color = Color.WHITE
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = 56
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }
}
