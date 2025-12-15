package com.example.lastdrop.ui.components

import android.content.Context
import android.util.Log
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

/**
 * EmoteManager - Centralized animation controller for Cloudie and Drop characters
 * 
 * Handles loading and playing Lottie animations from res/raw/
 * Falls back gracefully if animation files are missing
 */
class EmoteManager(private val context: Context) {

    companion object {
        private const val TAG = "EmoteManager"
        
        // Cloudie animation names (must match files in res/raw/)
        const val CLOUDIE_IDLE = "cloudie_idle"
        const val CLOUDIE_SPEAKING = "cloudie_speaking"
        const val CLOUDIE_CELEBRATE = "cloudie_celebrate"
        const val CLOUDIE_WARNING = "cloudie_warning"
        const val CLOUDIE_SAD = "cloudie_sad"
        const val CLOUDIE_THINKING = "cloudie_thinking"
        const val CLOUDIE_EXCITED = "cloudie_excited"
        
        // Drop animation names
        const val DROP_IDLE = "drop_idle"
        const val DROP_ROLLING = "drop_rolling"
        const val DROP_MOVING = "drop_moving"
        const val DROP_WINNING = "drop_winning"
        const val DROP_LOSING = "drop_losing"
        const val DROP_ELIMINATED = "drop_eliminated"
        const val DROP_REVIVED = "drop_revived"
    }

    /**
     * Play a Cloudie animation by name
     * @param view LottieAnimationView to animate
     * @param emote Animation name constant (e.g., CLOUDIE_IDLE)
     * @param loop Whether to loop the animation (default true for idle, false for one-shots)
     * @param onComplete Optional callback when animation finishes
     */
    fun playCloudieEmote(
        view: LottieAnimationView,
        emote: String,
        loop: Boolean = shouldLoop(emote),
        onComplete: (() -> Unit)? = null
    ) {
        playAnimation(view, emote, loop, onComplete)
    }

    /**
     * Play a Drop animation by name
     * @param view LottieAnimationView to animate
     * @param emote Animation name constant (e.g., DROP_IDLE)
     * @param loop Whether to loop the animation
     * @param onComplete Optional callback when animation finishes
     */
    fun playDropEmote(
        view: LottieAnimationView,
        emote: String,
        loop: Boolean = shouldLoop(emote),
        onComplete: (() -> Unit)? = null
    ) {
        playAnimation(view, emote, loop, onComplete)
    }

    /**
     * Internal animation player with fallback handling
     */
    private fun playAnimation(
        view: LottieAnimationView,
        animationName: String,
        loop: Boolean,
        onComplete: (() -> Unit)?
    ) {
        try {
            // Get resource ID from res/raw/
            val resId = context.resources.getIdentifier(
                animationName,
                "raw",
                context.packageName
            )

            if (resId == 0) {
                Log.w(TAG, "Animation not found: $animationName.json - using fallback")
                showFallback(view, animationName)
                return
            }

            // Load and play animation
            view.setAnimation(resId)
            view.repeatCount = if (loop) LottieDrawable.INFINITE else 0
            
            // Add completion listener if provided
            if (onComplete != null && !loop) {
                view.addAnimatorUpdateListener { animator ->
                    if (animator.animatedFraction >= 1.0f) {
                        onComplete()
                    }
                }
            }
            
            view.playAnimation()
            Log.d(TAG, "Playing animation: $animationName (loop=$loop)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing animation $animationName: ${e.message}")
            showFallback(view, animationName)
        }
    }

    /**
     * Show fallback for missing animations (solid color pulse)
     */
    private fun showFallback(view: LottieAnimationView, animationName: String) {
        view.cancelAnimation()
        // Could show a simple colored square or icon as fallback
        Log.d(TAG, "Fallback displayed for: $animationName")
    }

    /**
     * Stop all animations on a view
     */
    fun stopAnimation(view: LottieAnimationView) {
        view.cancelAnimation()
    }

    /**
     * Resume playing current animation
     */
    fun resumeAnimation(view: LottieAnimationView) {
        if (!view.isAnimating) {
            view.playAnimation()
        }
    }

    /**
     * Pause current animation
     */
    fun pauseAnimation(view: LottieAnimationView) {
        view.pauseAnimation()
    }

    /**
     * Determine if animation should loop by default
     */
    private fun shouldLoop(emote: String): Boolean {
        return when (emote) {
            // Loop these animations
            CLOUDIE_IDLE, CLOUDIE_SPEAKING, CLOUDIE_THINKING,
            DROP_IDLE, DROP_ROLLING -> true
            
            // One-shot animations
            CLOUDIE_CELEBRATE, CLOUDIE_WARNING, CLOUDIE_SAD, CLOUDIE_EXCITED,
            DROP_MOVING, DROP_WINNING, DROP_LOSING, DROP_ELIMINATED, DROP_REVIVED -> false
            
            else -> true // Default to loop for unknown animations
        }
    }

    /**
     * Chain animations (play one after another)
     * @param view Target LottieAnimationView
     * @param animations List of animation names to play in sequence
     * @param finalLoop Whether the last animation should loop
     */
    fun chainAnimations(
        view: LottieAnimationView,
        animations: List<String>,
        finalLoop: Boolean = false
    ) {
        if (animations.isEmpty()) return
        
        playAnimationChain(view, animations, 0, finalLoop)
    }

    private fun playAnimationChain(
        view: LottieAnimationView,
        animations: List<String>,
        index: Int,
        finalLoop: Boolean
    ) {
        if (index >= animations.size) return
        
        val isLast = index == animations.size - 1
        val shouldLoop = isLast && finalLoop
        
        playAnimation(view, animations[index], shouldLoop) {
            if (!isLast) {
                playAnimationChain(view, animations, index + 1, finalLoop)
            }
        }
    }
}
