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
        color = Color.parseColor("#B8E4F9") // Light sky blue - like reference
        style = Paint.Style.FILL
    }
    private val faceHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D6F0FC") // Even lighter blue for highlight spots
        style = Paint.Style.FILL
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A") // Almost black for eyes
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
        color = Color.parseColor("#E57B8D") // Soft pink mouth like reference
        style = Paint.Style.FILL
    }
    private val mouthStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A") // Black outline
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#70FFB6C1") // Light pink blush
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#20000000")
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A") // Black outline like reference
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
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
    
    // Floating animation state - smooth X,Y movement
    private var floatAnimatorX: ValueAnimator? = null
    private var floatAnimatorY: ValueAnimator? = null
    private var floatOffsetX = 0f  // Translation offset in X
    private var floatOffsetY = 0f  // Translation offset in Y
    private val floatAmplitudeX = 15f  // Max horizontal float distance in dp
    private val floatAmplitudeY = 10f   // Max vertical float distance in dp
    
    // Bounds for floating (set by parent to constrain movement)
    private var floatBoundsTop = 0f
    private var floatBoundsBottom = 0f
    
    /** Get the center position of the cloud (for water drop animations) */
    fun getCloudCenter(): Pair<Float, Float> {
        val location = IntArray(2)
        getLocationInWindow(location)
        return Pair(location[0] + width / 2f + translationX, location[1] + height / 2f + translationY)
    }
    
    /** Set the vertical bounds for floating animation */
    fun setFloatBounds(topBound: Float, bottomBound: Float) {
        floatBoundsTop = topBound
        floatBoundsBottom = bottomBound
    }

    init {
        startIdleAnimation()
        startBlinkLoop()
        startFloatingAnimation()
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
    
    private fun startFloatingAnimation() {
        // Horizontal floating - slower, wider movement
        floatAnimatorX = ValueAnimator.ofFloat(-floatAmplitudeX, floatAmplitudeX).apply {
            duration = 4500  // 4.5 seconds for one direction
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()  // Smooth ease in/out
            addUpdateListener { 
                if (!isTalking) {
                    floatOffsetX = it.animatedValue as Float
                    translationX = floatOffsetX * resources.displayMetrics.density
                }
            }
            start()
        }
        
        // Vertical floating - different timing for organic feel
        floatAnimatorY = ValueAnimator.ofFloat(-floatAmplitudeY, floatAmplitudeY).apply {
            duration = 3200  // 3.2 seconds - different from X for natural feel
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { 
                if (!isTalking) {
                    floatOffsetY = it.animatedValue as Float
                    translationY = floatOffsetY * resources.displayMetrics.density
                }
            }
            start()
        }
    }
    
    private fun pauseFloatingAnimation() {
        floatAnimatorX?.pause()
        floatAnimatorY?.pause()
    }
    
    private fun resumeFloatingAnimation() {
        floatAnimatorX?.resume()
        floatAnimatorY?.resume()
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
        
        // Pause floating animation during speech
        pauseFloatingAnimation()
        
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
        
        // Resume floating animation
        resumeFloatingAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        idleAnimator?.cancel()
        talkAnimator?.cancel()
        blinkAnimator?.cancel()
        floatAnimatorX?.cancel()
        floatAnimatorY?.cancel()
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
        val cloudW = width * 0.46f   // Wider cloud
        val cloudH = height * 0.30f  // Taller cloud

        canvas.save()
        canvas.scale(bounceScale, bounceScale, centerX, centerY)

        // ========== CARTOONY CLOUD - SMOOTH PATH ==========
        val cloudPath = createCloudPath(centerX, centerY, cloudW, cloudH)
        
        // Shadow
        canvas.save()
        canvas.translate(4f, 5f)
        canvas.drawPath(cloudPath, shadowPaint)
        canvas.restore()

        // Fill cloud
        canvas.drawPath(cloudPath, facePaint)
        
        // Highlight spots (like reference - lighter bubbles)
        canvas.drawCircle(centerX - cloudW * 0.45f, centerY - cloudH * 0.1f, cloudH * 0.25f, faceHighlightPaint)
        canvas.drawCircle(centerX - cloudW * 0.2f, centerY - cloudH * 0.35f, cloudH * 0.18f, faceHighlightPaint)
        canvas.drawCircle(centerX + cloudW * 0.35f, centerY - cloudH * 0.25f, cloudH * 0.12f, faceHighlightPaint)
        
        // Black outline (thick, like reference)
        canvas.drawPath(cloudPath, outlinePaint)

        // ========== CUTE FACE ==========
        val faceY = centerY + cloudH * 0.1f
        
        // Cute eyes (proportional)
        val eyeSpacing = cloudW * 0.24f
        val eyeRadius = cloudH * 0.24f * eyeScale
        val eyeHeightScale = 1f - (blinkProgress * 0.9f)
        
        if (eyeHeightScale > 0.2f) {
            // Left eye
            canvas.save()
            canvas.scale(1f, eyeHeightScale, centerX - eyeSpacing + eyeOffsetX, faceY + eyeOffsetY)
            canvas.drawCircle(centerX - eyeSpacing + eyeOffsetX, faceY + eyeOffsetY, eyeRadius * 1.2f, eyeWhitePaint)
            canvas.drawCircle(centerX - eyeSpacing + eyeOffsetX, faceY + eyeOffsetY, eyeRadius, eyePaint)
            canvas.drawCircle(centerX - eyeSpacing + eyeOffsetX + eyeRadius * 0.28f, faceY + eyeOffsetY - eyeRadius * 0.28f, eyeRadius * 0.32f, pupilPaint)
            canvas.restore()

            // Right eye
            canvas.save()
            canvas.scale(1f, eyeHeightScale, centerX + eyeSpacing + eyeOffsetX, faceY + eyeOffsetY)
            canvas.drawCircle(centerX + eyeSpacing + eyeOffsetX, faceY + eyeOffsetY, eyeRadius * 1.2f, eyeWhitePaint)
            canvas.drawCircle(centerX + eyeSpacing + eyeOffsetX, faceY + eyeOffsetY, eyeRadius, eyePaint)
            canvas.drawCircle(centerX + eyeSpacing + eyeOffsetX + eyeRadius * 0.28f, faceY + eyeOffsetY - eyeRadius * 0.28f, eyeRadius * 0.32f, pupilPaint)
            canvas.restore()
        } else {
            // Closed eyes ^_^
            val closedPaint = Paint(mouthStrokePaint).apply { strokeWidth = 3f; color = Color.parseColor("#1A1A1A") }
            val closedPath = android.graphics.Path()
            closedPath.moveTo(centerX - eyeSpacing - eyeRadius * 0.7f, faceY)
            closedPath.quadTo(centerX - eyeSpacing, faceY - eyeRadius * 0.5f, centerX - eyeSpacing + eyeRadius * 0.7f, faceY)
            canvas.drawPath(closedPath, closedPaint)
            closedPath.reset()
            closedPath.moveTo(centerX + eyeSpacing - eyeRadius * 0.7f, faceY)
            closedPath.quadTo(centerX + eyeSpacing, faceY - eyeRadius * 0.5f, centerX + eyeSpacing + eyeRadius * 0.7f, faceY)
            canvas.drawPath(closedPath, closedPaint)
        }

        // Rosy cheeks (like reference - pink circles)
        if (showBlush) {
            canvas.drawCircle(centerX - eyeSpacing - cloudW * 0.13f, faceY + cloudH * 0.32f, cloudH * 0.16f, blushPaint)
            canvas.drawCircle(centerX + eyeSpacing + cloudW * 0.13f, faceY + cloudH * 0.32f, cloudH * 0.16f, blushPaint)
        }

        // Cute smile mouth
        val mouthY = faceY + cloudH * 0.48f
        val mouthW = cloudW * 0.2f
        val curveAmt = mouthCurve * cloudH * 0.38f
        val talkOpen = mouthOpenAmount * cloudH * 0.3f
        
        val mouthPath = android.graphics.Path()
        if (isTalking && mouthOpenAmount > 0.1f) {
            mouthPath.addOval(centerX - mouthW * 0.5f, mouthY - talkOpen * 0.2f, centerX + mouthW * 0.5f, mouthY + talkOpen, android.graphics.Path.Direction.CW)
            canvas.drawPath(mouthPath, mouthPaint)
            mouthStrokePaint.strokeWidth = 2.5f
            canvas.drawPath(mouthPath, mouthStrokePaint)
        } else if (mouthCurve > 0) {
            mouthPath.moveTo(centerX - mouthW, mouthY)
            mouthPath.quadTo(centerX, mouthY + curveAmt, centerX + mouthW, mouthY)
            mouthPath.quadTo(centerX, mouthY + curveAmt * 0.35f, centerX - mouthW, mouthY)
            mouthPath.close()
            canvas.drawPath(mouthPath, mouthPaint)
            mouthStrokePaint.strokeWidth = 2f
            canvas.drawPath(mouthPath, mouthStrokePaint)
        } else {
            mouthPath.moveTo(centerX - mouthW * 0.6f, mouthY)
            mouthPath.quadTo(centerX, mouthY + curveAmt, centerX + mouthW * 0.6f, mouthY)
            mouthStrokePaint.strokeWidth = 3f
            canvas.drawPath(mouthPath, mouthStrokePaint)
        }

        canvas.restore()
    }
    
    /**
     * Create a smooth cartoony cloud path - matching reference image
     * Has 3 bumps on top and curved bumpy bottom (not flat!)
     */
    private fun createCloudPath(cx: Float, cy: Float, w: Float, h: Float): android.graphics.Path {
        val path = android.graphics.Path()
        
        // Cloud boundaries
        val left = cx - w
        val right = cx + w
        val top = cy - h * 1.05f
        val bottom = cy + h * 0.75f
        
        // Start from bottom center-left
        path.moveTo(cx - w * 0.4f, bottom)
        
        // Bottom left bump (curved, not flat!)
        path.quadTo(cx - w * 0.7f, bottom + h * 0.15f, left + w * 0.1f, bottom - h * 0.1f)
        
        // Left side curve going up
        path.quadTo(left - w * 0.12f, cy + h * 0.15f, left + w * 0.05f, cy - h * 0.15f)
        
        // Left top bump
        path.quadTo(left - w * 0.08f, cy - h * 0.7f, cx - w * 0.48f, top + h * 0.25f)
        
        // Middle top bump (tallest) - smooth curves
        path.quadTo(cx - w * 0.25f, top - h * 0.2f, cx, top - h * 0.05f)
        path.quadTo(cx + w * 0.25f, top - h * 0.2f, cx + w * 0.48f, top + h * 0.25f)
        
        // Right top bump
        path.quadTo(right + w * 0.08f, cy - h * 0.7f, right - w * 0.05f, cy - h * 0.15f)
        
        // Right side curve going down
        path.quadTo(right + w * 0.12f, cy + h * 0.15f, right - w * 0.1f, bottom - h * 0.1f)
        
        // Bottom right bump (curved, not flat!)
        path.quadTo(cx + w * 0.7f, bottom + h * 0.15f, cx + w * 0.4f, bottom)
        
        // Bottom center curve (slight bump)
        path.quadTo(cx, bottom + h * 0.12f, cx - w * 0.4f, bottom)
        
        path.close()
        return path
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 160
        val width = resolveSize(desiredSize, widthMeasureSpec)
        val height = resolveSize(desiredSize, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
