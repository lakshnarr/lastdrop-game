package com.example.lastdrop.ui.components

import android.animation.ValueAnimator
import android.animation.AnimatorSet
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
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
    
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, android.R.color.black)
        alpha = 40
    }

    private val bounds = RectF()
    private var currentScore = 0
    private var animationRunning = false
    private var shimmerAlpha = 0f

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
            return
        }

        val oldScore = currentScore
        val delta = newScore - oldScore
        currentScore = newScore

        if (oldScore == newScore) {
            text = newScore.toString()
            return
        }

        animationRunning = true
        
        // Number count animation
        val countAnimator = ValueAnimator.ofInt(oldScore, newScore).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animator ->
                text = (animator.animatedValue as Int).toString()
            }
        }
        
        // Bounce scale animation (more dramatic for large changes)
        val scaleAmount = if (Math.abs(delta) > 5) 1.3f else 1.15f
        val scaleAnimator = ValueAnimator.ofFloat(1.0f, scaleAmount, 1.0f).apply {
            this.duration = duration
            interpolator = BounceInterpolator()
            
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                scaleX = scale
                scaleY = scale
            }
        }
        
        // Color flash for significant changes
        if (Math.abs(delta) > 3) {
            val flashColor = if (delta > 0) 0x4400FF00 else 0x44FF0000 // Green or red tint
            val originalColor = backgroundPaint.color
            
            val colorAnimator = ValueAnimator.ofArgb(originalColor, flashColor, originalColor).apply {
                this.duration = duration
                addUpdateListener { animator ->
                    backgroundPaint.color = animator.animatedValue as Int
                    invalidate()
                }
            }
            
            AnimatorSet().apply {
                playTogether(countAnimator, scaleAnimator, colorAnimator)
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        animationRunning = false
                        text = newScore.toString()
                        scaleX = 1.0f
                        scaleY = 1.0f
                    }
                })
                start()
            }
        } else {
            AnimatorSet().apply {
                playTogether(countAnimator, scaleAnimator)
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        animationRunning = false
                        text = newScore.toString()
                        scaleX = 1.0f
                        scaleY = 1.0f
                    }
                })
                start()
            }
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
     * Now with overshoot effect for more dynamic appearance
     */
    fun startPulseAnimation() {
        animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
    }
    
    /**
     * Continuous shimmer effect for active player
     */
    fun startShimmer() {
        ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener { animator ->
                shimmerAlpha = animator.animatedValue as Float
                invalidate()
            }
            
            start()
        }
    }
    
    /**
     * Stop shimmer effect
     */
    fun stopShimmer() {
        shimmerAlpha = 0f
        invalidate()
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
        
        // Draw shadow (offset slightly)
        val shadowBounds = RectF(
            bounds.left + 4,
            bounds.top + 4,
            bounds.right + 4,
            bounds.bottom + 4
        )
        canvas.drawRoundRect(shadowBounds, 20f, 20f, shadowPaint)

        // Draw background
        canvas.drawRoundRect(bounds, 20f, 20f, backgroundPaint)
        
        // Draw shimmer overlay if active
        if (shimmerAlpha > 0) {
            val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xFFFFFF
                alpha = (shimmerAlpha * 80).toInt() // Max 80 alpha
            }
            canvas.drawRoundRect(bounds, 20f, 20f, shimmerPaint)
        }

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
