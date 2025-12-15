package com.example.lastdrop.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat

/**
 * Custom scorecard badge component with animated number transitions
 * Displays player scores with color-matched borders and smooth count-up/down animations
 */
class ScorecardBadge @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, android.R.color.darker_gray)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = ContextCompat.getColor(context, android.R.color.white)
    }

    private val bounds = RectF()
    private var currentScore = 0
    private var animationRunning = false

    init {
        // Set default text appearance
        textSize = 18f
        setTextColor(ContextCompat.getColor(context, android.R.color.white))
        gravity = android.view.Gravity.CENTER
        text = "0"
        
        // Add padding for better appearance
        setPadding(24, 16, 24, 16)
    }

    /**
     * Set the border color to match player color
     */
    fun setBorderColor(color: Int) {
        borderPaint.color = color
        invalidate()
    }

    /**
     * Set the background color
     */
    fun setBadgeBackgroundColor(color: Int) {
        backgroundPaint.color = color
        invalidate()
    }

    /**
     * Update score with smooth animation
     * @param newScore The target score value
     * @param duration Animation duration in milliseconds (default 800ms)
     */
    fun animateToScore(newScore: Int, duration: Long = 800) {
        if (animationRunning) {
            // Cancel any running animation
            return
        }

        val oldScore = currentScore
        currentScore = newScore

        if (oldScore == newScore) {
            text = newScore.toString()
            return
        }

        animationRunning = true
        ValueAnimator.ofInt(oldScore, newScore).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animator ->
                text = (animator.animatedValue as Int).toString()
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animationRunning = false
                    text = newScore.toString()
                }
            })
            
            start()
        }
    }

    /**
     * Set score immediately without animation
     */
    fun setScore(score: Int) {
        currentScore = score
        text = score.toString()
    }

    /**
     * Get current score value
     */
    fun getScore(): Int = currentScore

    /**
     * Pulse animation for highlighting (e.g., current player turn)
     */
    fun startPulseAnimation() {
        animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(300)
            .withEndAction {
                animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .start()
            }
            .start()
    }

    /**
     * Stop any running animations
     */
    fun stopAnimations() {
        animate().cancel()
        scaleX = 1.0f
        scaleY = 1.0f
        animationRunning = false
    }

    override fun onDraw(canvas: Canvas) {
        // Calculate bounds for rounded rectangle
        bounds.set(
            borderPaint.strokeWidth / 2,
            borderPaint.strokeWidth / 2,
            width - borderPaint.strokeWidth / 2,
            height - borderPaint.strokeWidth / 2
        )

        // Draw background
        canvas.drawRoundRect(bounds, 20f, 20f, backgroundPaint)

        // Draw border
        canvas.drawRoundRect(bounds, 20f, 20f, borderPaint)

        // Draw text (handled by parent TextView)
        super.onDraw(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        
        // Ensure minimum dimensions
        val minWidth = 80
        val minHeight = 60
        
        val measuredWidth = measuredWidth.coerceAtLeast(minWidth)
        val measuredHeight = measuredHeight.coerceAtLeast(minHeight)
        
        setMeasuredDimension(measuredWidth, measuredHeight)
    }
}
