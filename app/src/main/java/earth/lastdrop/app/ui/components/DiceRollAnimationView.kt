package earth.lastdrop.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * DiceRollAnimationView - Animated dice roll display
 * Shows dice value with scale/bounce animation synced with voice
 */
class DiceRollAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var diceValue: Int = 0
    private var secondDiceValue: Int = 0  // For two-dice mode
    private var averageValue: Int = 0
    private var twoDiceMode = false
    private var showingAverage = false
    
    private var scale = 0f
    private var alpha = 0f
    private var playerColor = Color.parseColor("#4FC3F7")
    private var animationPhase = AnimPhase.HIDDEN

    enum class AnimPhase {
        HIDDEN,
        DICE1_SHOWING,
        DICE2_SHOWING,
        AVERAGE_SHOWING,
        FADING
    }

    private val dicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 100f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private val diceRect = RectF()
    private var currentAnimator: AnimatorSet? = null

    fun setPlayerColor(color: Int) {
        playerColor = color
        invalidate()
    }

    fun setTwoDiceMode(enabled: Boolean) {
        twoDiceMode = enabled
    }

    /**
     * Show single dice roll with animation
     */
    fun showRoll(value: Int, onComplete: (() -> Unit)? = null) {
        diceValue = value
        twoDiceMode = false
        showingAverage = false
        animateIn(onComplete)
    }

    /**
     * Show two-dice roll with staged animation
     * First shows dice1, then dice2, then the average
     */
    fun showTwoDiceRoll(dice1: Int, dice2: Int, average: Int, onComplete: (() -> Unit)? = null) {
        diceValue = dice1
        secondDiceValue = dice2
        averageValue = average
        twoDiceMode = true
        showingAverage = false
        
        animateTwoDiceSequence(onComplete)
    }

    private fun animateIn(onComplete: (() -> Unit)? = null) {
        currentAnimator?.cancel()
        
        animationPhase = AnimPhase.DICE1_SHOWING
        
        val scaleAnim = ValueAnimator.ofFloat(0.3f, 1.2f, 1f).apply {
            duration = 600
            interpolator = OvershootInterpolator(2f)
            addUpdateListener { 
                scale = it.animatedValue as Float
                invalidate()
            }
        }

        val alphaAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener { 
                alpha = it.animatedValue as Float
                invalidate()
            }
        }

        currentAnimator = AnimatorSet().apply {
            playTogether(scaleAnim, alphaAnim)
            start()
        }

        // Auto fade out after delay
        postDelayed({
            fadeOut(onComplete)
        }, 2500)
    }

    private fun animateTwoDiceSequence(onComplete: (() -> Unit)? = null) {
        currentAnimator?.cancel()
        
        // Phase 1: Show first dice
        animationPhase = AnimPhase.DICE1_SHOWING
        
        val phase1Scale = ValueAnimator.ofFloat(0.3f, 1.1f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator(1.5f)
            addUpdateListener { 
                scale = it.animatedValue as Float
                invalidate()
            }
        }
        val phase1Alpha = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            addUpdateListener { 
                alpha = it.animatedValue as Float
                invalidate()
            }
        }

        val animSet = AnimatorSet()
        animSet.playTogether(phase1Scale, phase1Alpha)
        animSet.start()

        // Phase 2: Show second dice (after 800ms)
        postDelayed({
            animationPhase = AnimPhase.DICE2_SHOWING
            
            val phase2Scale = ValueAnimator.ofFloat(scale, 1.1f, 1f).apply {
                duration = 300
                interpolator = OvershootInterpolator(1.5f)
                addUpdateListener { 
                    scale = it.animatedValue as Float
                    invalidate()
                }
            }
            phase2Scale.start()
        }, 800)

        // Phase 3: Show average (after 1600ms)
        postDelayed({
            animationPhase = AnimPhase.AVERAGE_SHOWING
            showingAverage = true
            
            val phase3Scale = ValueAnimator.ofFloat(0.5f, 1.3f, 1f).apply {
                duration = 500
                interpolator = OvershootInterpolator(2f)
                addUpdateListener { 
                    scale = it.animatedValue as Float
                    invalidate()
                }
            }
            phase3Scale.start()
        }, 1600)

        // Phase 4: Fade out (after 3500ms)
        postDelayed({
            fadeOut(onComplete)
        }, 3500)
    }

    private fun fadeOut(onComplete: (() -> Unit)? = null) {
        animationPhase = AnimPhase.FADING
        
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { 
                alpha = it.animatedValue as Float
                if (alpha <= 0) {
                    animationPhase = AnimPhase.HIDDEN
                }
                invalidate()
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete?.invoke()
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            start()
        }
    }

    fun hide() {
        currentAnimator?.cancel()
        animationPhase = AnimPhase.HIDDEN
        alpha = 0f
        scale = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (animationPhase == AnimPhase.HIDDEN || alpha <= 0) return

        val centerX = width / 2f
        val centerY = height / 2f

        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)

        if (twoDiceMode && !showingAverage) {
            // Draw two dice side by side
            val diceSize = 80f
            val spacing = 20f

            // First dice
            diceRect.set(
                centerX - diceSize - spacing/2, centerY - diceSize/2,
                centerX - spacing/2, centerY + diceSize/2
            )
            dicePaint.color = adjustAlpha(playerColor, alpha)
            canvas.drawRoundRect(diceRect, 12f, 12f, dicePaint)
            borderPaint.alpha = (alpha * 255).toInt()
            canvas.drawRoundRect(diceRect, 12f, 12f, borderPaint)
            
            valuePaint.textSize = 60f
            valuePaint.alpha = (alpha * 255).toInt()
            canvas.drawText(diceValue.toString(), centerX - diceSize/2 - spacing/2, centerY + 20f, valuePaint)

            // Second dice (if in phase 2+)
            if (animationPhase != AnimPhase.DICE1_SHOWING) {
                diceRect.set(
                    centerX + spacing/2, centerY - diceSize/2,
                    centerX + diceSize + spacing/2, centerY + diceSize/2
                )
                canvas.drawRoundRect(diceRect, 12f, 12f, dicePaint)
                canvas.drawRoundRect(diceRect, 12f, 12f, borderPaint)
                canvas.drawText(secondDiceValue.toString(), centerX + diceSize/2 + spacing/2, centerY + 20f, valuePaint)
            }

            // Label below
            labelPaint.alpha = (alpha * 255).toInt()
            if (animationPhase == AnimPhase.DICE2_SHOWING) {
                canvas.drawText("($diceValue + $secondDiceValue) ÷ 2 = ?", centerX, centerY + 80f, labelPaint)
            }

        } else {
            // Draw single large dice (or average result)
            val diceSize = 120f
            diceRect.set(
                centerX - diceSize/2, centerY - diceSize/2,
                centerX + diceSize/2, centerY + diceSize/2
            )

            dicePaint.color = adjustAlpha(playerColor, alpha)
            canvas.drawRoundRect(diceRect, 16f, 16f, dicePaint)
            
            borderPaint.alpha = (alpha * 255).toInt()
            canvas.drawRoundRect(diceRect, 16f, 16f, borderPaint)

            // Dice value
            valuePaint.textSize = 80f
            valuePaint.alpha = (alpha * 255).toInt()
            val displayValue = if (showingAverage) averageValue else diceValue
            canvas.drawText(displayValue.toString(), centerX, centerY + 28f, valuePaint)

            // Label for average
            if (showingAverage && twoDiceMode) {
                labelPaint.alpha = (alpha * 255).toInt()
                canvas.drawText("($diceValue + $secondDiceValue) ÷ 2 = $averageValue", centerX, centerY + 80f, labelPaint)
            }
        }

        canvas.restore()
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 200
        val width = resolveSize(desiredSize, widthMeasureSpec)
        val height = resolveSize(desiredSize, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
