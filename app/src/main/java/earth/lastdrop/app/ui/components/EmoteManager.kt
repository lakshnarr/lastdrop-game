package com.example.lastdrop.ui.components

import android.content.Context
import android.util.Log
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

/**
 * EmoteManager - Centralized animation controller for Cloudie and Drop characters
 * 
 * Supports loading from:
 * - Local res/raw/ JSON files (offline)
 * - LottieFiles.com URLs (online, faster setup)
 */
class EmoteManager(private val context: Context) {

    companion object {
        private const val TAG = "EmoteManager"
        
        // Animation URLs from LottieFiles.com (can be changed anytime)
        // Format: https://lottie.host/[id]/[hash].json or .lottie
        
        // Cloudie animations
        const val CLOUDIE_IDLE = "https://lottie.host/embed/YOUR_IDLE_ID/YOUR_HASH.json"
        const val CLOUDIE_SPEAKING = "https://lottie.host/embed/YOUR_SPEAKING_ID/YOUR_HASH.json"
        const val CLOUDIE_CELEBRATE = "https://lottie.host/embed/YOUR_CELEBRATE_ID/YOUR_HASH.json"
        const val CLOUDIE_WARNING = "https://lottie.host/embed/YOUR_WARNING_ID/YOUR_HASH.json"
        const val CLOUDIE_SAD = "https://lottie.host/embed/YOUR_SAD_ID/YOUR_HASH.json"
        const val CLOUDIE_THINKING = "https://lottie.host/embed/YOUR_THINKING_ID/YOUR_HASH.json"
        const val CLOUDIE_EXCITED = "https://lottie.host/embed/YOUR_EXCITED_ID/YOUR_HASH.json"
        
        // Drop animations
        const val DROP_IDLE = "https://lottie.host/embed/YOUR_DROP_IDLE_ID/YOUR_HASH.json"
        const val DROP_ROLLING = "https://lottie.host/embed/YOUR_ROLLING_ID/YOUR_HASH.json"
        const val DROP_MOVING = "https://lottie.host/embed/YOUR_MOVING_ID/YOUR_HASH.json"
        const val DROP_WINNING = "https://lottie.host/embed/YOUR_WINNING_ID/YOUR_HASH.json"
        const val DROP_LOSING = "https://lottie.host/embed/YOUR_LOSING_ID/YOUR_HASH.json"
        const val DROP_ELIMINATED = "https://lottie.host/embed/YOUR_ELIMINATED_ID/YOUR_HASH.json"
        const val DROP_REVIVED = "https://lottie.host/embed/YOUR_REVIVED_ID/YOUR_HASH.json"
    }

    /**
     * Play a Cloudie animation by URL
     */
    fun playCloudieEmote(
        view: LottieAnimationView,
        animationUrl: String,
        loop: Boolean = shouldLoop(animationUrl),
        onComplete: (() -> Unit)? = null
    ) {
        playAnimationFromUrl(view, animationUrl, loop, onComplete)
    }

    /**
     * Play a Drop animation by URL
     */
    fun playDropEmote(
        view: LottieAnimationView,
        animationUrl: String,
        loop: Boolean = shouldLoop(animationUrl),
        onComplete: (() -> Unit)? = null
    ) {
        playAnimationFromUrl(view, animationUrl, loop, onComplete)
    }

    /**
     * Load and play animation from LottieFiles URL
     */
    private fun playAnimationFromUrl(
        view: LottieAnimationView,
        url: String,
        loop: Boolean,
        onComplete: (() -> Unit)?
    ) {
        try {
            // Check if URL is a placeholder
            if (url.contains("YOUR_") || url.contains("PLACEHOLDER")) {
                Log.w(TAG, "Placeholder URL detected: $url - using fallback")
                showFallback(view, url)
                return
            }

            // Load from URL
            view.setAnimationFromUrl(url)
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
            Log.d(TAG, "Loading animation from URL: $url (loop=$loop)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading animation from URL $url: ${e.message}")
            showFallback(view, url)
        }
    }

    /**
     * Load animation from local res/raw/ file (fallback for offline use)
     */
    fun playLocalAnimation(
        view: LottieAnimationView,
        resourceName: String,
        loop: Boolean = true
    ) {
        try {
            val resId = context.resources.getIdentifier(
                resourceName,
                "raw",
                context.packageName
            )

            if (resId == 0) {
                Log.w(TAG, "Local animation not found: $resourceName.json")
                showFallback(view, resourceName)
                return
            }

            view.setAnimation(resId)
            view.repeatCount = if (loop) LottieDrawable.INFINITE else 0
            view.playAnimation()
            Log.d(TAG, "Playing local animation: $resourceName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing local animation: ${e.message}")
            showFallback(view, resourceName)
        }
    }

    /**
     * Show fallback for missing animations
     */
    private fun showFallback(view: LottieAnimationView, identifier: String) {
        view.cancelAnimation()
        view.alpha = 0.3f // Dim to indicate placeholder
        Log.d(TAG, "Fallback mode for: $identifier")
    }

    /**
     * Stop all animations
     */
    fun stopAnimation(view: LottieAnimationView) {
        view.cancelAnimation()
    }

    /**
     * Resume animation
     */
    fun resumeAnimation(view: LottieAnimationView) {
        if (!view.isAnimating) {
            view.playAnimation()
        }
    }

    /**
     * Pause animation
     */
    fun pauseAnimation(view: LottieAnimationView) {
        view.pauseAnimation()
    }

    /**
     * Determine if animation should loop based on URL pattern
     */
    private fun shouldLoop(identifier: String): Boolean {
        return when {
            identifier.contains("idle", ignoreCase = true) -> true
            identifier.contains("speaking", ignoreCase = true) -> true
            identifier.contains("thinking", ignoreCase = true) -> true
            identifier.contains("rolling", ignoreCase = true) -> true
            identifier.contains("celebrate", ignoreCase = true) -> false
            identifier.contains("warning", ignoreCase = true) -> false
            identifier.contains("sad", ignoreCase = true) -> false
            identifier.contains("excited", ignoreCase = true) -> false
            identifier.contains("moving", ignoreCase = true) -> false
            identifier.contains("winning", ignoreCase = true) -> false
            identifier.contains("losing", ignoreCase = true) -> false
            identifier.contains("eliminated", ignoreCase = true) -> false
            identifier.contains("revived", ignoreCase = true) -> false
            else -> true // Default to loop
        }
    }

    /**
     * Chain animations (play one after another)
     */
    fun chainAnimations(
        view: LottieAnimationView,
        animationUrls: List<String>,
        finalLoop: Boolean = false
    ) {
        if (animationUrls.isEmpty()) return
        playAnimationChain(view, animationUrls, 0, finalLoop)
    }

    private fun playAnimationChain(
        view: LottieAnimationView,
        urls: List<String>,
        index: Int,
        finalLoop: Boolean
    ) {
        if (index >= urls.size) return
        
        val isLast = index == urls.size - 1
        val shouldLoop = isLast && finalLoop
        
        playAnimationFromUrl(view, urls[index], shouldLoop) {
            if (!isLast) {
                playAnimationChain(view, urls, index + 1, finalLoop)
            }
        }
    }
}
