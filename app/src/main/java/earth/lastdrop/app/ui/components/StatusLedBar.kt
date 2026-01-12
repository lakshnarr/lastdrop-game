package earth.lastdrop.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.MotionEvent

/**
 * StatusLedBar - Shows connection status for Board, Dice, and Web
 * Simple LED indicators with text labels
 * Each LED is clickable for connect/disconnect actions
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

    enum class LedType {
        BOARD,
        DICE,
        WEB
    }

    interface OnLedClickListener {
        fun onLedClick(ledType: LedType, currentState: LedState)
    }

    private var ledClickListener: OnLedClickListener? = null

    fun setOnLedClickListener(listener: OnLedClickListener) {
        ledClickListener = listener
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
    private var dice1Color = ""
    private var dice2Color = ""

    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f  // Increased from 28f
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 38f  // Increased from 24f
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

    fun setDiceState(
        state: LedState,
        battery1: Int = -1,
        battery2: Int = -1,
        color1: String = "",
        color2: String = ""
    ) {
        ledItems[1].state = state
        dice1Battery = battery1
        dice2Battery = battery2
        if (color1.isNotBlank()) dice1Color = color1
        if (color2.isNotBlank()) dice2Color = color2
        
        ledItems[1].extraText = when (state) {
            LedState.OFFLINE -> {
                dice1Color = ""
                dice2Color = ""
                ""
            }
            else -> buildDiceExtraText()
        }
        invalidate()
    }

    fun setWebState(state: LedState) {
        ledItems[2].state = state
        invalidate()
    }

    fun setDiceBatteryLevel(level: Int) {
        if (level > 0) {
            ledItems[1].extraText = "$level%"
        } else {
            ledItems[1].extraText = ""
        }
        invalidate()
    }

    fun setTwoDiceMode(enabled: Boolean) {
        twoDiceMode = enabled
        ledItems[1].extraText = buildDiceExtraText()
        invalidate()
    }

    private fun buildDiceExtraText(): String {
        return if (twoDiceMode) {
            listOf(
                formatDiceInfo(dice1Color, dice1Battery),
                formatDiceInfo(dice2Color, dice2Battery)
            ).filter { it.isNotEmpty() }
                .joinToString(", ")
        } else {
            val primaryColor = if (dice1Color.isNotBlank()) dice1Color else dice2Color
            val primaryBattery = when {
                dice1Battery >= 0 -> dice1Battery
                dice2Battery >= 0 -> dice2Battery
                else -> -1
            }
            formatDiceInfo(primaryColor, primaryBattery)
        }
    }

    private fun formatDiceInfo(color: String, battery: Int): String {
        // Abbreviate color names for compact display
        val abbreviatedColor = when (color.trim().lowercase()) {
            "red" -> "R"
            "green" -> "G"
            "blue" -> "B"
            "yellow" -> "Y"
            "orange" -> "O"
            "black" -> "Bk"
            else -> ""
        }

        return when {
            abbreviatedColor.isNotEmpty() && battery >= 0 -> "$abbreviatedColor:$battery%"
            abbreviatedColor.isNotEmpty() -> abbreviatedColor
            battery >= 0 -> "$battery%"
            else -> ""
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val ledRadius = 18f
        // Reserve more space for 2-dice badge to prevent overlap
        val badgeSpace = if (twoDiceMode) 140f else 0f
        val availableWidth = width - badgeSpace
        val itemWidth = availableWidth / ledItems.size.toFloat()
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
            ledPaint.setShadowLayer(12f, 0f, 0f, ledPaint.color)
            canvas.drawCircle(centerX - 40f, centerY - 10f, 22f, ledPaint)
            ledPaint.clearShadowLayer()

            // Label
            canvas.drawText(item.label, centerX + 20f, centerY - 5f, labelPaint)

            // Extra text (battery) - use smaller font for dice info to prevent overlap
            if (item.extraText.isNotEmpty()) {
                textPaint.textSize = if (index == 1 && twoDiceMode) 24f else 28f  // Smaller for 2-dice mode
                canvas.drawText(item.extraText, centerX + 20f, centerY + 26f, textPaint)
            }
        }

        // Draw 2-Dice badge if enabled (larger and more visible)
        if (twoDiceMode) {
            val badgeX = width - 70f
            val badgeY = centerY
            
            badgePaint.color = Color.parseColor("#3a3f50")
            canvas.drawRoundRect(
                badgeX - 65f, badgeY - 28f,  // Larger badge
                badgeX + 65f, badgeY + 28f,
                12f, 12f, badgePaint
            )
            
            textPaint.textSize = 38f  // Larger text
            textPaint.color = Color.parseColor("#4FC3F7")
            canvas.drawText("🎲🎲", badgeX, badgeY - 4f, textPaint)  // Dice emoji
            textPaint.textSize = 24f
            canvas.drawText("2-Dice", badgeX, badgeY + 18f, textPaint)  // Text below
            textPaint.color = Color.WHITE
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = 110  // Slightly taller to accommodate 2-line badge
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val clickedLed = detectLedClick(event.x, event.y)
            if (clickedLed != null) {
                val ledIndex = when (clickedLed) {
                    LedType.BOARD -> 0
                    LedType.DICE -> 1
                    LedType.WEB -> 2
                }
                ledClickListener?.onLedClick(clickedLed, ledItems[ledIndex].state)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun detectLedClick(x: Float, y: Float): LedType? {
        val badgeSpace = if (twoDiceMode) 140f else 0f
        val availableWidth = width - badgeSpace
        val itemWidth = availableWidth / ledItems.size.toFloat()

        return when {
            x < itemWidth -> LedType.BOARD
            x < itemWidth * 2 -> LedType.DICE
            x < itemWidth * 3 -> LedType.WEB
            else -> null
        }
    }
}
