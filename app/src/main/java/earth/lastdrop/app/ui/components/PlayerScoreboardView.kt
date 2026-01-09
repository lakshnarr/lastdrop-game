package earth.lastdrop.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.OvershootInterpolator

/**
 * PlayerScoreboardView - Displays all players with their scores
 * Shows active player, eliminated players, and score changes
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
        // Animation state
        var displayScore: Float = 0f,
        var scoreChangeAnim: Float = 0f,
        var lastScoreChange: Int = 0
    )

    private val players = mutableListOf<PlayerData>()
    private var activePlayerIndex = -1

    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
    }
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        textSize = 36f
    }
    private val changeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
    }
    private val outPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 36f
        textAlign = Paint.Align.RIGHT
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4FC3F7")
        textSize = 40f
    }

    private val rowRect = RectF()
    private val rowHeight = 70f
    private val rowMargin = 8f
    private val cornerRadius = 12f

    fun setPlayers(playerList: List<Triple<String, Int, String>>) {
        players.clear()
        playerList.forEach { (name, score, colorHex) ->
            val color = try {
                Color.parseColor(if (colorHex.startsWith("#")) colorHex else "#$colorHex")
            } catch (e: Exception) {
                Color.parseColor("#4FC3F7")
            }
            players.add(PlayerData(name, score, color, displayScore = score.toFloat()))
        }
        invalidate()
        requestLayout()
    }

    fun setActivePlayer(index: Int) {
        activePlayerIndex = index
        players.forEachIndexed { i, player ->
            player.isActive = (i == index)
        }
        invalidate()
    }

    fun updatePlayerScore(index: Int, newScore: Int, animate: Boolean = true) {
        if (index < 0 || index >= players.size) return
        
        val player = players[index]
        val oldScore = player.score
        val change = newScore - oldScore
        
        player.score = newScore
        player.lastScoreChange = change
        
        if (animate && change != 0) {
            // Animate score change
            player.scoreChangeAnim = 1f
            
            ValueAnimator.ofFloat(player.displayScore, newScore.toFloat()).apply {
                duration = 500
                addUpdateListener { 
                    player.displayScore = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            
            // Fade out change indicator
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 2000
                startDelay = 500
                addUpdateListener { 
                    player.scoreChangeAnim = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            player.displayScore = newScore.toFloat()
            invalidate()
        }
    }

    fun eliminatePlayer(index: Int) {
        if (index >= 0 && index < players.size) {
            players[index].isAlive = false
            players[index].score = 0
            players[index].displayScore = 0f
            invalidate()
        }
    }

    fun reset() {
        players.forEach { player ->
            player.score = 0
            player.displayScore = 0f
            player.isAlive = true
            player.isActive = false
            player.scoreChangeAnim = 0f
            player.lastScoreChange = 0
        }
        activePlayerIndex = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 16f
        val startY = padding

        players.forEachIndexed { index, player ->
            val top = startY + index * (rowHeight + rowMargin)
            val bottom = top + rowHeight

            rowRect.set(padding, top, width - padding, bottom)

            // Row background
            rowPaint.color = if (player.isActive) {
                Color.parseColor("#2a3a50") // Highlighted
            } else if (!player.isAlive) {
                Color.parseColor("#1a1a1a") // Dimmed for eliminated
            } else {
                Color.parseColor("#1a2030") // Normal
            }
            canvas.drawRoundRect(rowRect, cornerRadius, cornerRadius, rowPaint)

            // Active player border
            if (player.isActive) {
                rowPaint.style = Paint.Style.STROKE
                rowPaint.strokeWidth = 3f
                rowPaint.color = player.color
                canvas.drawRoundRect(rowRect, cornerRadius, cornerRadius, rowPaint)
                rowPaint.style = Paint.Style.FILL
            }

            // Player color dot
            rowPaint.color = if (player.isAlive) player.color else Color.GRAY
            canvas.drawCircle(padding + 30f, top + rowHeight / 2, 12f, rowPaint)

            // Active indicator arrow
            if (player.isActive) {
                indicatorPaint.color = player.color
                canvas.drawText("▶", padding + 50f, top + rowHeight / 2 + 14f, indicatorPaint)
            }

            // Player name
            namePaint.color = if (player.isAlive) Color.WHITE else Color.parseColor("#666666")
            val nameX = padding + (if (player.isActive) 80f else 60f)
            canvas.drawText(player.name, nameX, top + rowHeight / 2 + 14f, namePaint)

            // Score or OUT
            if (player.isAlive) {
                // Water drop emoji + score
                val scoreText = player.displayScore.toInt().toString()
                scorePaint.color = Color.WHITE
                canvas.drawText(scoreText, width - padding - 20f, top + rowHeight / 2 + 16f, scorePaint)
                
                // Drop icon
                dropPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("💧", width - padding - 60f - scorePaint.measureText(scoreText), top + rowHeight / 2 + 12f, dropPaint)

                // Score change indicator (animated)
                if (player.scoreChangeAnim > 0 && player.lastScoreChange != 0) {
                    changeTextPaint.alpha = (player.scoreChangeAnim * 255).toInt()
                    changeTextPaint.color = if (player.lastScoreChange > 0) {
                        Color.parseColor("#4CAF50") // Green for gain
                    } else {
                        Color.parseColor("#F44336") // Red for loss
                    }
                    val changeText = if (player.lastScoreChange > 0) "+${player.lastScoreChange}" else "${player.lastScoreChange}"
                    canvas.drawText(changeText, width - padding - 120f, top + rowHeight / 2 + 16f, changeTextPaint)
                    changeTextPaint.alpha = 255
                }
            } else {
                // Show OUT for eliminated players
                canvas.drawText("OUT", width - padding - 20f, top + rowHeight / 2 + 12f, outPaint)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (16 + players.size * (rowHeight + rowMargin) + 16).toInt()
        val minHeight = 200 // Minimum height for empty state
        val height = resolveSize(maxOf(desiredHeight, minHeight), heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }
}
