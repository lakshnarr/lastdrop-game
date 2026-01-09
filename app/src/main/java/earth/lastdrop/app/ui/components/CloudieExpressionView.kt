package earth.lastdrop.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import earth.lastdrop.app.R

/**
 * CloudieExpressionView - Simple expression-based Cloudie face
 * Uses drawable states instead of Lottie for reliability
 * 
 * Expressions:
 * - IDLE: Default calm face
 * - HAPPY: Big smile (score gain)
 * - SAD: Frown (score loss)
 * - EXCITED: Eyes wide, huge smile (big win)
 * - THINKING: Looking up/side (waiting for roll)
 * - WORRIED: Concerned expression (low score warning)
 */
class CloudieExpressionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Expression {
        IDLE,
        HAPPY,
        SAD,
        EXCITED,
        THINKING,
        WORRIED
    }

    private var currentExpression = Expression.IDLE
    private var transitionProgress = 1f
    private var bounceScale = 1f

    // Face drawing paints - Warm, friendly colors!
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#87CEEB") // Sky blue - friendly cloud color
        style = Paint.Style.FILL
    }
    private val faceHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0E0FF") // Lighter blue for highlight
        style = Paint.Style.FILL
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E4057") // Dark navy eyes - warmer than pure black
        style = Paint.Style.FILL
    }
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E91E63") // Pink happy mouth
        style = Paint.Style.FILL  // Filled mouth for friendlier look
    }
    private val mouthStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C2185B") // Darker pink outline
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60FF8A80") // Rosy pink blush - more visible
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30000000")
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5DA9E9") // Slightly darker blue outline
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // Animation state
    private var eyeOffsetX = 0f
    private var eyeOffsetY = 0f
    private var mouthCurve = 0.4f  // Positive = smile, negative = frown - start happier!
    private var eyeScale = 1f
    private var showBlush = true  // Always show blush by default for cute look
    private var eyebrowAngle = 0f
    
    // Blink animation state
    private var blinkProgress = 0f  // 0 = open, 1 = closed
    private var isBlinking = false
    
    // Talking animation state
    private var isTalking = false
    private var mouthOpenAmount = 0f  // 0 = closed, 1 = fully open
    private var talkAnimator: ValueAnimator? = null
    private var blinkAnimator: ValueAnimator? = null

    private var idleAnimator: ValueAnimator? = null

    init {
        startIdleAnimation()
        startBlinkLoop()
    }

    private fun startIdleAnimation() {
        idleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { 
                val progress = it.animatedValue as Float
                // Subtle breathing/floating effect
                bounceScale = 1f + (progress * 0.02f)
                // Subtle eye movement (only when not talking)
                if (!isTalking && (currentExpression == Expression.IDLE || currentExpression == Expression.THINKING)) {
                    eyeOffsetX = (progress - 0.5f) * 4f
                }
                invalidate()
            }
            start()
        }
    }
    
    private fun startBlinkLoop() {
        // Random blink every 2-5 seconds
        postDelayed({
            if (!isBlinking && !isTalking) {
                doBlink()
            }
            startBlinkLoop()
        }, (2000L + (Math.random() * 3000).toLong()))
    }
    
    private fun doBlink() {
        isBlinking = true
        blinkAnimator?.cancel()
        blinkAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 150  // Quick blink
            addUpdateListener {
                blinkProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(animation: android.animation.Animator) { isBlinking = false }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) { isBlinking = false }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            start()
        }
    }
    
    /**
     * Start talking animation - call when voice starts speaking
     * @param speechRate Voice speech rate (0.5-2.0), affects mouth animation speed
     */
    fun startTalking(speechRate: Float = 1.0f) {
        if (isTalking) return
        isTalking = true
        
        // Adjust mouth animation speed based on speech rate
        // Faster speech = faster mouth movement
        val mouthDuration = (120 / speechRate.coerceIn(0.5f, 2.0f)).toLong().coerceIn(60, 200)
        
        talkAnimator?.cancel()
        talkAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = mouthDuration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                mouthOpenAmount = it.animatedValue as Float
                // Slight head bob while talking
                bounceScale = 1f + (mouthOpenAmount * 0.015f)
                invalidate()
            }
            start()
        }
    }
    
    /**
     * Stop talking animation - call when voice stops speaking
     */
    fun stopTalking() {
        isTalking = false
        talkAnimator?.cancel()
        mouthOpenAmount = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        idleAnimator?.cancel()
        talkAnimator?.cancel()
        blinkAnimator?.cancel()
    }

    fun setExpression(expression: Expression, animate: Boolean = true) {
        if (expression == currentExpression) return
        
        currentExpression = expression
        
        when (expression) {
            Expression.IDLE -> {
                mouthCurve = 0.35f  // Always smiling a bit!
                eyeScale = 1f
                showBlush = true  // Always cute
                eyeOffsetX = 0f
                eyeOffsetY = 0f
                eyebrowAngle = 0f
            }
            Expression.HAPPY -> {
                mouthCurve = 0.6f  // Big happy smile
                eyeScale = 0.7f  // More squinted happy eyes
                showBlush = true
                eyeOffsetX = 0f
                eyeOffsetY = 2f
                eyebrowAngle = -5f  // Slightly raised happy eyebrows
            }
            Expression.SAD -> {
                mouthCurve = -0.2f  // Not too sad
                eyeScale = 1.1f
                showBlush = true  // Still cute when sad
                eyeOffsetX = 0f
                eyeOffsetY = 4f
                eyebrowAngle = 12f  // Worried eyebrows
            }
            Expression.EXCITED -> {
                mouthCurve = 0.7f  // Super big smile!
                eyeScale = 1.4f  // Big sparkly excited eyes
                showBlush = true
                eyeOffsetX = 0f
                eyeOffsetY = -2f
                eyebrowAngle = -12f  // Raised happy eyebrows
            }
            Expression.THINKING -> {
                mouthCurve = 0.15f  // Small thoughtful smile
                eyeScale = 1f
                showBlush = true
                eyeOffsetX = 8f
                eyeOffsetY = -4f
                eyebrowAngle = 8f  // Curious eyebrows
            }
            Expression.WORRIED -> {
                mouthCurve = -0.1f
                eyeScale = 1.1f
                showBlush = false
                eyeOffsetX = 0f
                eyeOffsetY = 0f
                eyebrowAngle = 20f
            }
        }

        if (animate) {
            // Quick bounce animation
            ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { 
                    bounceScale = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) / 2.5f

        canvas.save()
        canvas.scale(bounceScale, bounceScale, centerX, centerY)

        // Shadow (soft drop shadow)
        canvas.drawCircle(centerX + 3f, centerY + 5f, radius + 2f, shadowPaint)

        // Main cloud face - fluffy cloud shape!
        canvas.drawCircle(centerX, centerY, radius, facePaint)
        
        // Cloud bumps on top (make it look fluffy)
        val bumpRadius = radius * 0.38f
        canvas.drawCircle(centerX - radius * 0.55f, centerY - radius * 0.55f, bumpRadius, facePaint)
        canvas.drawCircle(centerX + radius * 0.55f, centerY - radius * 0.55f, bumpRadius, facePaint)
        canvas.drawCircle(centerX, centerY - radius * 0.72f, bumpRadius * 0.95f, facePaint)
        // Extra bumps for fluffier look
        canvas.drawCircle(centerX - radius * 0.3f, centerY - radius * 0.7f, bumpRadius * 0.7f, facePaint)
        canvas.drawCircle(centerX + radius * 0.3f, centerY - radius * 0.7f, bumpRadius * 0.7f, facePaint)
        
        // Highlight on face (makes it look 3D and shiny)
        canvas.drawCircle(centerX - radius * 0.3f, centerY - radius * 0.2f, radius * 0.25f, faceHighlightPaint)
        
        // Subtle outline for definition
        canvas.drawCircle(centerX, centerY, radius, outlinePaint)

        // Eyes - Big cute anime-style eyes with blink!
        val eyeY = centerY - radius * 0.1f
        val eyeSpacing = radius * 0.35f
        val eyeRadius = radius * 0.18f * eyeScale
        
        // Calculate eye height based on blink (squash vertically when blinking)
        val eyeHeightScale = 1f - (blinkProgress * 0.9f)  // Eyes close to 10% when blinking
        
        if (eyeHeightScale > 0.2f) {
            // Draw eyes normally (not fully closed)
            
            // Left eye white
            canvas.save()
            canvas.scale(1f, eyeHeightScale, centerX - eyeSpacing + eyeOffsetX, eyeY + eyeOffsetY)
            canvas.drawCircle(
                centerX - eyeSpacing + eyeOffsetX,
                eyeY + eyeOffsetY,
                eyeRadius * 1.3f,
                eyeWhitePaint
            )
            // Left eye pupil (dark)
            canvas.drawCircle(
                centerX - eyeSpacing + eyeOffsetX,
                eyeY + eyeOffsetY,
                eyeRadius,
                eyePaint
            )
            // Left eye sparkle (big)
            canvas.drawCircle(
                centerX - eyeSpacing + eyeOffsetX + eyeRadius * 0.3f,
                eyeY + eyeOffsetY - eyeRadius * 0.3f,
                eyeRadius * 0.35f,
                pupilPaint
            )
            // Left eye sparkle (small)
            canvas.drawCircle(
                centerX - eyeSpacing + eyeOffsetX - eyeRadius * 0.2f,
                eyeY + eyeOffsetY + eyeRadius * 0.3f,
                eyeRadius * 0.15f,
                pupilPaint
            )
            canvas.restore()

            // Right eye white
            canvas.save()
            canvas.scale(1f, eyeHeightScale, centerX + eyeSpacing + eyeOffsetX, eyeY + eyeOffsetY)
            canvas.drawCircle(
                centerX + eyeSpacing + eyeOffsetX,
                eyeY + eyeOffsetY,
                eyeRadius * 1.3f,
                eyeWhitePaint
            )
            // Right eye pupil (dark)
            canvas.drawCircle(
                centerX + eyeSpacing + eyeOffsetX,
                eyeY + eyeOffsetY,
                eyeRadius,
                eyePaint
            )
            // Right eye sparkle (big)
            canvas.drawCircle(
                centerX + eyeSpacing + eyeOffsetX + eyeRadius * 0.3f,
                eyeY + eyeOffsetY - eyeRadius * 0.3f,
                eyeRadius * 0.35f,
                pupilPaint
            )
            // Right eye sparkle (small)
            canvas.drawCircle(
                centerX + eyeSpacing + eyeOffsetX - eyeRadius * 0.2f,
                eyeY + eyeOffsetY + eyeRadius * 0.3f,
                eyeRadius * 0.15f,
                pupilPaint
            )
            canvas.restore()
        } else {
            // Eyes closed - draw curved lines (like ^_^)
            mouthStrokePaint.strokeWidth = 4f
            mouthStrokePaint.color = Color.parseColor("#2E4057")
            
            // Left closed eye arc
            val closedPath = android.graphics.Path()
            closedPath.moveTo(centerX - eyeSpacing - eyeRadius, eyeY + eyeOffsetY)
            closedPath.quadTo(centerX - eyeSpacing, eyeY + eyeOffsetY - eyeRadius * 0.5f, 
                              centerX - eyeSpacing + eyeRadius, eyeY + eyeOffsetY)
            canvas.drawPath(closedPath, mouthStrokePaint)
            
            // Right closed eye arc
            closedPath.reset()
            closedPath.moveTo(centerX + eyeSpacing - eyeRadius, eyeY + eyeOffsetY)
            closedPath.quadTo(centerX + eyeSpacing, eyeY + eyeOffsetY - eyeRadius * 0.5f,
                              centerX + eyeSpacing + eyeRadius, eyeY + eyeOffsetY)
            canvas.drawPath(closedPath, mouthStrokePaint)
            
            mouthStrokePaint.color = Color.parseColor("#C2185B")
        }

        // Eyebrows (cute little arcs)
        if (eyebrowAngle != 0f) {
            mouthStrokePaint.strokeWidth = 3f
            mouthStrokePaint.color = Color.parseColor("#5DA9E9")
            
            canvas.save()
            canvas.rotate(eyebrowAngle, centerX - eyeSpacing, eyeY - eyeRadius * 1.5f - 6f)
            canvas.drawLine(
                centerX - eyeSpacing - 10f, eyeY - eyeRadius * 1.5f - 6f,
                centerX - eyeSpacing + 10f, eyeY - eyeRadius * 1.5f - 6f,
                mouthStrokePaint
            )
            canvas.restore()

            canvas.save()
            canvas.rotate(-eyebrowAngle, centerX + eyeSpacing, eyeY - eyeRadius * 1.5f - 6f)
            canvas.drawLine(
                centerX + eyeSpacing - 10f, eyeY - eyeRadius * 1.5f - 6f,
                centerX + eyeSpacing + 10f, eyeY - eyeRadius * 1.5f - 6f,
                mouthStrokePaint
            )
            canvas.restore()
            
            mouthStrokePaint.color = Color.parseColor("#C2185B")
        }

        // Rosy blush (always cute!)
        if (showBlush) {
            canvas.drawCircle(centerX - eyeSpacing - 8f, eyeY + 22f, 10f, blushPaint)
            canvas.drawCircle(centerX + eyeSpacing + 8f, eyeY + 22f, 10f, blushPaint)
        }

        // Mouth - Cute filled smile with talking animation!
        val mouthY = centerY + radius * 0.35f
        val mouthWidth = radius * 0.4f
        val curveAmount = mouthCurve * radius * 0.35f
        
        // Add talking animation - mouth opens up and down
        val talkOpenAmount = mouthOpenAmount * radius * 0.2f  // How much mouth opens when talking
        
        val path = android.graphics.Path()
        if (isTalking && mouthOpenAmount > 0.1f) {
            // Talking mouth - open oval shape
            val mouthOpenWidth = mouthWidth * 0.6f
            val mouthOpenHeight = talkOpenAmount
            
            // Draw an oval mouth opening
            path.addOval(
                centerX - mouthOpenWidth,
                mouthY - mouthOpenHeight * 0.3f,
                centerX + mouthOpenWidth,
                mouthY + mouthOpenHeight,
                android.graphics.Path.Direction.CW
            )
            canvas.drawPath(path, mouthPaint)
            canvas.drawPath(path, mouthStrokePaint)
        } else if (mouthCurve > 0) {
            // Happy smile - filled arc (not talking)
            path.moveTo(centerX - mouthWidth, mouthY)
            path.quadTo(centerX, mouthY + curveAmount, centerX + mouthWidth, mouthY)
            path.quadTo(centerX, mouthY + curveAmount * 0.3f, centerX - mouthWidth, mouthY)
            path.close()
            canvas.drawPath(path, mouthPaint)
            canvas.drawPath(path, mouthStrokePaint)
        } else {
            // Sad/worried - just a line
            path.moveTo(centerX - mouthWidth * 0.7f, mouthY)
            path.quadTo(centerX, mouthY + curveAmount, centerX + mouthWidth * 0.7f, mouthY)
            mouthStrokePaint.strokeWidth = 5f
            canvas.drawPath(path, mouthStrokePaint)
            mouthStrokePaint.strokeWidth = 4f
        }

        canvas.restore()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 160
        val width = resolveSize(desiredSize, widthMeasureSpec)
        val height = resolveSize(desiredSize, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
