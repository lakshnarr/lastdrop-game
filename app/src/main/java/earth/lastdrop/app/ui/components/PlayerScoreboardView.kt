package earth.lastdrop.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.animation.AnimatorSet
import android.view.animation.OvershootInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.sin

/**
 * PlayerScoreboardView - Kid-friendly cartoony scoreboard
 * Features bouncy animations, player-colored rows, sorted by score
 * Water drop animations for score changes
 */
class PlayerScoreboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class PlayerData(
        var name: String,
        var score: Int,
        var color: Int,
        var isAlive: Boolean = true,
        var isActive: Boolean = false,
        var originalIndex: Int = 0,
        // Animation state
        var displayScore: Float = 0f,
        var scoreChangeAnim: Float = 0f,
        var lastScoreChange: Int = 0,
        var bounceAnim: Float = 0f,
        var wiggleAnim: Float = 0f,
        var displayY: Float = 0f, // For smooth position animation
        var targetY: Float = 0f
    )
    
    // Water drop animation data
    data class WaterDropAnim(
        var playerIndex: Int,
        var startX: Float,
        var startY: Float,
        var targetX: Float,
        var targetY: Float,
        var progress: Float = 0f,
        var isGaining: Boolean = true,  // true = falling down, false = evaporating up
        var alpha: Float = 1f,
        var scale: Float = 1f,
        var dropIndex: Int = 0  // For staggered animation
    )

    private val players = mutableListOf<PlayerData>()
    private val waterDropAnims = mutableListOf<WaterDropAnim>()
    private var activePlayerIndex = -1
    private var globalWiggle = 0f
    private var wiggleAnimator: ValueAnimator? = null
    
    // Callback to get cloud position from parent
    var cloudCenterProvider: (() -> Pair<Float, Float>)? = null

    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#50000000")
    }
    // White text with strong shadow for contrast on any color
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 46f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(6f, 2f, 2f, Color.parseColor("#CC000000"))
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 58f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
        setShadowLayer(6f, 2f, 2f, Color.parseColor("#CC000000"))
    }
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 44f
        setShadowLayer(4f, 1f, 1f, Color.parseColor("#80000000"))
    }
    private val changeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 38f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 1f, 1f, Color.parseColor("#80000000"))
    }
    private val outPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.parseColor("#80000000"))
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        textSize = 48f
        setShadowLayer(3f, 1f, 1f, Color.parseColor("#80000000"))
    }
    private val waterDropAnimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val rowRect = RectF()
    private val shadowRect = RectF()
    private val rowHeight = 82f
    private val rowMargin = 14f
    private val cornerRadius = 28f
    private val padding = 20f
    
    // Fixed positions for alignment
    private val dropIconX = 0.62f  // Water drop at 62% of row width
    private val scoreX = 0.88f    // Score at 88% of row width

    init {
        startIdleAnimation()
    }

    private fun startIdleAnimation() {
        wiggleAnimator?.cancel()
        wiggleAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                globalWiggle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        wiggleAnimator?.cancel()
    }

    fun setPlayers(playerList: List<Triple<String, Int, String>>) {
        players.clear()
        playerList.forEachIndexed { index, (name, score, colorHex) ->
            val color = try {
                Color.parseColor(if (colorHex.startsWith("#")) colorHex else "#$colorHex")
            } catch (e: Exception) {
                Color.parseColor("#4FC3F7")
            }
            players.add(PlayerData(
                name = name,
                score = score,
                color = color,
                displayScore = score.toFloat(),
                originalIndex = index
            ))
        }
        // Initialize display positions
        updateTargetPositions(animate = false)
        invalidate()
        requestLayout()
    }

    fun setActivePlayer(index: Int) {
        val oldActive = activePlayerIndex
        activePlayerIndex = index
        players.forEach { player ->
            player.isActive = (player.originalIndex == index)
            if (player.originalIndex == index && oldActive != index) {
                // Bounce animation for newly active player
                ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                    duration = 400
                    interpolator = BounceInterpolator()
                    addUpdateListener {
                        player.bounceAnim = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        }
        invalidate()
    }

    fun updatePlayerScore(index: Int, newScore: Int, animate: Boolean = true) {
        val player = players.find { it.originalIndex == index } ?: return
        
        val oldScore = player.score
        val change = newScore - oldScore
        
        player.score = newScore
        player.lastScoreChange = change
        
        if (animate && change != 0) {
            // Trigger water drop animation
            animateWaterDrops(player, change)
            
            // Animate score change with bounce
            player.scoreChangeAnim = 1f
            
            ValueAnimator.ofFloat(player.displayScore, newScore.toFloat()).apply {
                duration = 600
                interpolator = OvershootInterpolator(1.5f)
                addUpdateListener { 
                    player.displayScore = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            
            // Pop animation for score bubble
            ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                duration = 300
                addUpdateListener {
                    player.wiggleAnim = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            
            // Fade out change indicator
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 2500
                startDelay = 800
                addUpdateListener { 
                    player.scoreChangeAnim = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            
            // Re-sort and animate positions
            updateTargetPositions(animate = true)
        } else {
            player.displayScore = newScore.toFloat()
            updateTargetPositions(animate = false)
            invalidate()
        }
    }

    private fun updateTargetPositions(animate: Boolean) {
        // Sort by score descending (highest first), then by original index for ties
        val sorted = players.sortedWith(compareByDescending<PlayerData> { it.score }.thenBy { it.originalIndex })
        
        sorted.forEachIndexed { sortedIndex, player ->
            val newTargetY = padding + sortedIndex * (rowHeight + rowMargin)
            
            if (animate && player.targetY != newTargetY && player.targetY != 0f) {
                // Animate to new position
                val startY = player.displayY
                ValueAnimator.ofFloat(startY, newTargetY).apply {
                    duration = 500
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        player.displayY = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            } else {
                player.displayY = newTargetY
            }
            player.targetY = newTargetY
        }
    }

    fun eliminatePlayer(index: Int) {
        val player = players.find { it.originalIndex == index } ?: return
        player.isAlive = false
        player.score = 0
        player.displayScore = 0f
        updateTargetPositions(animate = true)
        invalidate()
    }

    fun reset() {
        players.forEach { player ->
            player.score = 0
            player.displayScore = 0f
            player.isAlive = true
            player.isActive = false
            player.scoreChangeAnim = 0f
            player.lastScoreChange = 0
            player.bounceAnim = 0f
            player.wiggleAnim = 0f
        }
        activePlayerIndex = -1
        updateTargetPositions(animate = false)
        invalidate()
    }

    // Darken a color for gradient effect
    private fun darkenColor(color: Int, factor: Float = 0.7f): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    // Lighten a color for highlight
    private fun lightenColor(color: Int, factor: Float = 1.3f): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
    
    /**
     * Animate water drops falling from cloud (gaining) or evaporating up (losing)
     */
    private fun animateWaterDrops(player: PlayerData, change: Int) {
        val absChange = kotlin.math.abs(change)
        val isGaining = change > 0
        
        // Get player row position for animation target/source (in local coordinates)
        val rowCenterY = player.displayY + rowHeight / 2
        val dropTargetX = width * dropIconX  // Aligned drop icon position
        
        // Get cloud position in window coordinates, then convert to our local coordinates
        val cloudWindowPos = cloudCenterProvider?.invoke()
        
        // Get our own position in window coordinates
        val myLocation = IntArray(2)
        getLocationInWindow(myLocation)
        
        // Convert cloud position from window to local coordinates
        val cloudLocalX = (cloudWindowPos?.first ?: (width / 2f)) - myLocation[0]
        val cloudLocalY = (cloudWindowPos?.second ?: -100f) - myLocation[1]
        
        // Create water drop animations (max 5 visible at once)
        val dropCount = minOf(absChange, 5)
        
        for (i in 0 until dropCount) {
            val drop = if (isGaining) {
                // Drops fall from cloud (above) DOWN to player row
                WaterDropAnim(
                    playerIndex = player.originalIndex,
                    startX = cloudLocalX + (i - dropCount / 2f) * 20f,  // Spread from cloud
                    startY = cloudLocalY + 80f,  // Bottom of cloud (cloud is above us, negative Y typically)
                    targetX = dropTargetX,
                    targetY = rowCenterY,
                    isGaining = true,
                    dropIndex = i
                )
            } else {
                // Drops evaporate from player row UP to cloud height
                WaterDropAnim(
                    playerIndex = player.originalIndex,
                    startX = dropTargetX,
                    startY = rowCenterY,
                    targetX = cloudLocalX + (i - dropCount / 2f) * 30f,  // Spread as they rise
                    targetY = cloudLocalY + 60f,  // Near cloud (UP means smaller Y in Android)
                    isGaining = false,
                    dropIndex = i
                )
            }
            waterDropAnims.add(drop)
            
            // Animate this drop with stagger
            val staggerDelay = i * 150L
            
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (isGaining) 800L else 1200L  // Evaporation slower and more visible
                startDelay = staggerDelay
                // For evaporation: start fast, slow down at top (DecelerateInterpolator)
                // For gaining: accelerate as it falls (AccelerateInterpolator)
                interpolator = if (isGaining) AccelerateInterpolator(1.5f) else DecelerateInterpolator(2.0f)
                addUpdateListener { animator ->
                    drop.progress = animator.animatedValue as Float
                    
                    // Fade effect
                    drop.alpha = if (isGaining) {
                        // Falling: fade out on impact
                        if (drop.progress > 0.85f) 1f - (drop.progress - 0.85f) * 6.67f else 1f
                    } else {
                        // Evaporating: fade out gradually as it rises
                        1f - drop.progress * 0.8f
                    }
                    
                    // Scale effect
                    drop.scale = if (isGaining) {
                        // Grow slightly as it falls, then shrink on impact
                        if (drop.progress < 0.7f) 0.7f + drop.progress * 0.5f 
                        else 1.2f - (drop.progress - 0.7f) * 1.5f
                    } else {
                        // Shrink as it evaporates (gets smaller going up)
                        1f - drop.progress * 0.6f
                    }
                    
                    invalidate()
                }
                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        waterDropAnims.remove(drop)
                        invalidate()
                    }
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationCancel(animation: android.animation.Animator) { waterDropAnims.remove(drop) }
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                start()
            }
        }
    }
    
    /** Get the screen position of a player's drop icon area (for cloud animation coordination) */
    fun getPlayerDropIconPosition(playerIndex: Int): Pair<Float, Float>? {
        val player = players.find { it.originalIndex == playerIndex } ?: return null
        val x = width * dropIconX
        val y = player.displayY + rowHeight / 2
        return Pair(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Sort for drawing (to handle overlap during animation)
        val sortedForDrawing = players.sortedBy { it.displayY }

        sortedForDrawing.forEach { player ->
            // Slight wiggle for active player
            val wiggleOffset = if (player.isActive) {
                sin(globalWiggle.toDouble()).toFloat() * 3f
            } else 0f
            
            val bounceOffset = player.bounceAnim * 8f
            
            val top = player.displayY - bounceOffset + wiggleOffset
            val bottom = top + rowHeight

            // Shadow (offset down-right)
            shadowRect.set(padding + 4f, top + 6f, width - padding + 4f, bottom + 6f)
            canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)

            rowRect.set(padding, top, width - padding, bottom)

            // Use player's color for the row (grayed out if eliminated)
            val baseColor = if (player.isAlive) player.color else Color.parseColor("#555555")
            val darkColor = if (player.isAlive) darkenColor(player.color, 0.65f) else Color.parseColor("#333333")
            
            rowPaint.shader = LinearGradient(
                rowRect.left, rowRect.top,
                rowRect.left, rowRect.bottom,
                baseColor, darkColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rowRect, cornerRadius, cornerRadius, rowPaint)
            rowPaint.shader = null

            // White outline for active player (thicker, glowing)
            if (player.isActive) {
                outlinePaint.strokeWidth = 5f
                outlinePaint.color = Color.WHITE
                canvas.drawRoundRect(rowRect, cornerRadius, cornerRadius, outlinePaint)
                
                // Inner glow
                outlinePaint.strokeWidth = 2f
                outlinePaint.color = Color.parseColor("#80FFFFFF")
                val innerRect = RectF(rowRect.left + 4f, rowRect.top + 4f, rowRect.right - 4f, rowRect.bottom - 4f)
                canvas.drawRoundRect(innerRect, cornerRadius - 4f, cornerRadius - 4f, outlinePaint)
            }

            // Star icon for active player
            val nameStartX = if (player.isActive) {
                canvas.drawText("⭐", padding + 18f, top + rowHeight / 2 + 16f, starPaint)
                padding + 70f
            } else {
                padding + 24f
            }

            // Player name (no dot needed - row is the color)
            canvas.drawText(player.name, nameStartX, top + rowHeight / 2 + 16f, namePaint)

            // Score bubble or OUT
            if (player.isAlive) {
                // FIXED: Water drop emoji at consistent position (aligned across all rows)
                val dropX = width * dropIconX
                val dropY = top + rowHeight / 2 + 14f + sin((globalWiggle + player.originalIndex).toDouble()).toFloat() * 2f
                dropPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("💧", dropX, dropY, dropPaint)
                
                // Score with bounce effect at consistent position
                val scoreScale = 1f + player.wiggleAnim * 0.15f
                val scoreText = player.displayScore.toInt().toString()
                
                scorePaint.textSize = 58f * scoreScale
                val scoreXPos = width * scoreX
                scorePaint.textAlign = Paint.Align.CENTER
                canvas.drawText(scoreText, scoreXPos, top + rowHeight / 2 + 20f, scorePaint)
                scorePaint.textSize = 58f

                // Score change popup (floating up)
                if (player.scoreChangeAnim > 0 && player.lastScoreChange != 0) {
                    val floatUp = (1f - player.scoreChangeAnim) * 40f
                    changeTextPaint.alpha = (player.scoreChangeAnim * 255).toInt()
                    changeTextPaint.color = if (player.lastScoreChange > 0) {
                        Color.parseColor("#4CAF50")
                    } else {
                        Color.parseColor("#FF5252")
                    }
                    val changeText = if (player.lastScoreChange > 0) "+${player.lastScoreChange}" else "${player.lastScoreChange}"
                    canvas.drawText(changeText, dropX, top + rowHeight / 2 - floatUp, changeTextPaint)
                    changeTextPaint.alpha = 255
                }
            } else {
                // Kid-friendly "OUT!" text with sleeping emoji
                canvas.drawText("😴 OUT!", width - padding - 24f, top + rowHeight / 2 + 14f, outPaint)
            }
        }
        
        // Draw animated water drops
        waterDropAnims.forEach { drop ->
            val progress = drop.progress
            
            // Calculate current position with slight curve
            val curveOffset = sin(progress * Math.PI).toFloat() * 20f * (if (drop.isGaining) 1f else -1f)
            val currentX = drop.startX + (drop.targetX - drop.startX) * progress + curveOffset
            val currentY = drop.startY + (drop.targetY - drop.startY) * progress
            
            waterDropAnimPaint.alpha = (drop.alpha * 255).toInt()
            waterDropAnimPaint.textSize = 40f * drop.scale
            
            // Draw water drop with slight rotation for evaporation
            canvas.save()
            if (!drop.isGaining) {
                // Slight wobble for evaporation
                val wobble = sin(progress * Math.PI * 4).toFloat() * 10f
                canvas.rotate(wobble, currentX, currentY)
            }
            canvas.drawText("💧", currentX, currentY, waterDropAnimPaint)
            canvas.restore()
        }
        waterDropAnimPaint.alpha = 255
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (20 + players.size * (rowHeight + rowMargin) + 20).toInt()
        val minHeight = 200
        val height = resolveSize(maxOf(desiredHeight, minHeight), heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }
}
