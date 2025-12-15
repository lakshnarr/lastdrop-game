package com.example.lastdrop.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.random.Random

/**
 * ParticleEffectView - Overlay view for particle effects (confetti, sparkles, etc.)
 * 
 * Features:
 * - Confetti burst effect for victories
 * - Sparkle effects for score gains
 * - Rain drops for sad moments
 * - Customizable particle colors and physics
 * 
 * Usage:
 * ```
 * particleView.burstConfetti()
 * particleView.spawnSparkles(x, y)
 * particleView.startRain()
 * ```
 */
class ParticleEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val CONFETTI_COUNT = 50
        private const val SPARKLE_COUNT = 20
        private const val RAIN_DROP_COUNT = 30
        private const val PARTICLE_LIFETIME_MS = 3000L
    }

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    // Particle data class
    private data class Particle(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        var alpha: Int,
        var color: Int,
        var size: Float,
        var lifetime: Float,
        val shape: Shape
    )

    enum class Shape {
        RECTANGLE,  // Confetti
        CIRCLE,     // Sparkles
        RAINDROP    // Rain
    }

    init {
        // Transparent background - overlay only
        setBackgroundColor(0x00000000)
    }

    /**
     * Burst confetti effect - victory celebration
     * Particles explode from center and fall down
     */
    fun burstConfetti() {
        particles.clear()
        
        val centerX = width / 2f
        val centerY = height / 3f
        
        // Generate confetti particles
        for (i in 0 until CONFETTI_COUNT) {
            val angle = Random.nextFloat() * 2 * Math.PI
            val speed = Random.nextFloat() * 400 + 200
            
            particles.add(
                Particle(
                    x = centerX,
                    y = centerY,
                    velocityX = (Math.cos(angle) * speed).toFloat(),
                    velocityY = (Math.sin(angle) * speed - 300).toFloat(), // Initial upward velocity
                    rotation = Random.nextFloat() * 360,
                    rotationSpeed = Random.nextFloat() * 10 - 5,
                    alpha = 255,
                    color = getRandomColor(),
                    size = Random.nextFloat() * 15 + 10,
                    lifetime = 1.0f,
                    shape = Shape.RECTANGLE
                )
            )
        }
        
        startAnimation()
    }

    /**
     * Spawn sparkles at specific location - score gain effect
     */
    fun spawnSparkles(x: Float, y: Float) {
        for (i in 0 until SPARKLE_COUNT) {
            val angle = Random.nextFloat() * 2 * Math.PI
            val speed = Random.nextFloat() * 150 + 50
            
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    velocityX = (Math.cos(angle) * speed).toFloat(),
                    velocityY = (Math.sin(angle) * speed).toFloat(),
                    rotation = 0f,
                    rotationSpeed = 0f,
                    alpha = 255,
                    color = 0xFFFFD700.toInt(), // Gold sparkles
                    size = Random.nextFloat() * 8 + 4,
                    lifetime = 1.0f,
                    shape = Shape.CIRCLE
                )
            )
        }
        
        if (animator == null || !animator!!.isRunning) {
            startAnimation()
        }
    }

    /**
     * Start rain effect - sad moment
     */
    fun startRain() {
        particles.clear()
        
        for (i in 0 until RAIN_DROP_COUNT) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * width,
                    y = -Random.nextFloat() * 200, // Start above screen
                    velocityX = Random.nextFloat() * 20 - 10,
                    velocityY = Random.nextFloat() * 300 + 200,
                    rotation = 0f,
                    rotationSpeed = 0f,
                    alpha = 200,
                    color = 0xFF4682B4.toInt(), // Steel blue rain
                    size = Random.nextFloat() * 5 + 3,
                    lifetime = 1.0f,
                    shape = Shape.RAINDROP
                )
            )
        }
        
        startAnimation()
    }

    /**
     * Clear all particles and stop animation
     */
    fun clearParticles() {
        particles.clear()
        animator?.cancel()
        animator = null
        invalidate()
    }

    private fun startAnimation() {
        animator?.cancel()
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PARTICLE_LIFETIME_MS
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                updateParticles(progress)
                invalidate()
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    particles.clear()
                    invalidate()
                }
            })
            
            start()
        }
    }

    private fun updateParticles(progress: Float) {
        val deltaTime = 0.016f // Assume ~60 FPS
        val gravity = 800f // Pixels per second squared
        
        particles.forEach { particle ->
            // Update position
            particle.x += particle.velocityX * deltaTime
            particle.y += particle.velocityY * deltaTime
            
            // Apply gravity (except for rain which has constant velocity)
            if (particle.shape != Shape.RAINDROP) {
                particle.velocityY += gravity * deltaTime
            }
            
            // Update rotation
            particle.rotation += particle.rotationSpeed
            
            // Fade out over lifetime
            particle.lifetime = 1.0f - progress
            particle.alpha = (particle.lifetime * 255).toInt().coerceIn(0, 255)
            
            // Dampen horizontal velocity (air resistance)
            particle.velocityX *= 0.98f
        }
        
        // Remove particles that are off-screen or faded
        particles.removeAll { particle ->
            particle.alpha <= 0 || 
            particle.x < -100 || particle.x > width + 100 ||
            particle.y > height + 100
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        particles.forEach { particle ->
            paint.color = particle.color
            paint.alpha = particle.alpha
            
            canvas.save()
            canvas.translate(particle.x, particle.y)
            canvas.rotate(particle.rotation)
            
            when (particle.shape) {
                Shape.RECTANGLE -> {
                    // Confetti rectangle
                    canvas.drawRect(
                        -particle.size / 2,
                        -particle.size,
                        particle.size / 2,
                        particle.size,
                        paint
                    )
                }
                Shape.CIRCLE -> {
                    // Sparkle circle
                    canvas.drawCircle(0f, 0f, particle.size, paint)
                }
                Shape.RAINDROP -> {
                    // Raindrop teardrop shape
                    val path = Path().apply {
                        moveTo(0f, -particle.size)
                        quadTo(particle.size / 2, 0f, 0f, particle.size)
                        quadTo(-particle.size / 2, 0f, 0f, -particle.size)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }
            }
            
            canvas.restore()
        }
    }

    private fun getRandomColor(): Int {
        val colors = intArrayOf(
            0xFFFF6B6B.toInt(), // Red
            0xFF4ECDC4.toInt(), // Turquoise
            0xFFFFE66D.toInt(), // Yellow
            0xFF95E1D3.toInt(), // Mint
            0xFFF38181.toInt(), // Pink
            0xFFAA96DA.toInt(), // Purple
            0xFFFCBF49.toInt(), // Orange
            0xFF06FFA5.toInt()  // Green
        )
        return colors[Random.nextInt(colors.size)]
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearParticles()
    }
}
