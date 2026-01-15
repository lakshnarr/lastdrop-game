package earth.lastdrop.app.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Callback interface for speech events
 */
interface SpeechCallback {
    fun onSpeechStart(utteranceId: String)
    fun onSpeechDone(utteranceId: String)
    fun onSpeechError(utteranceId: String, error: String)
}

/**
 * Minimal voice hook placeholder. Replace with real TTS/ElevenLabs later.
 */
interface VoiceService {
    fun speak(text: String)
    fun stop() {}  // Stop any current speech
    fun setSpeechCallback(callback: SpeechCallback?)
    fun getSpeechRate(): Float  // Returns current speech rate for animation sync
    fun shutdown() {}
}

class NoOpVoiceService(private val context: Context) : VoiceService {
    private var speechCallback: SpeechCallback? = null
    
    override fun speak(text: String) {
        Log.d("VoiceService", "(stub) Cloudie would say: $text")
        // Hook real TTS/streaming here. Keep non-blocking.
    }
    
    override fun stop() {
        // No-op for stub
    }
    
    override fun setSpeechCallback(callback: SpeechCallback?) {
        speechCallback = callback
    }
    
    override fun getSpeechRate(): Float = 1.0f
}

/**
 * Hybrid voice service that tries ElevenLabs first, falls back to TTS
 */
class HybridVoiceService(
    private val context: Context,
    private val settings: VoiceSettings,
    private val onReady: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null,
    private val onFallback: ((String) -> Unit)? = null  // Called when falling back from ElevenLabs to TTS
) : VoiceService {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speechCallback: SpeechCallback? = null
    private var currentUtteranceId: String? = null
    private var isSpeaking = false
    
    private val elevenLabs: ElevenLabsService? = if (settings.useElevenLabs && settings.elevenLabsApiKey.isNotBlank()) {
        val voiceId = settings.elevenLabsVoiceId.ifBlank { "pNInz6obpgDQGcFmaJgB" }
        Log.d("HybridVoice", "✓ ElevenLabs enabled")
        Log.d("HybridVoice", "  API key: ${settings.elevenLabsApiKey.take(8)}...${settings.elevenLabsApiKey.takeLast(4)}")
        Log.d("HybridVoice", "  Voice ID: $voiceId")
        Log.d("HybridVoice", "  Stability: ${settings.elevenLabsStability}")
        Log.d("HybridVoice", "  Similarity: ${settings.elevenLabsSimilarityBoost}")
        Log.d("HybridVoice", "  Speed: ${settings.elevenLabsSpeed}")
        ElevenLabsService(settings.elevenLabsApiKey, voiceId)
    } else {
        Log.d("HybridVoice", "✗ ElevenLabs disabled - useElevenLabs: ${settings.useElevenLabs}, hasApiKey: ${settings.elevenLabsApiKey.isNotBlank()}")
        null
    }
    
    private val ttsService: TextToSpeechVoiceService = TextToSpeechVoiceService(
        context,
        pitch = settings.ttsPitch,
        speechRate = settings.ttsSpeechRate,
        localeCode = settings.ttsLocale,
        onReady = onReady,
        onError = onError
    )
    
    private var mediaPlayer: MediaPlayer? = null
    private val audioCache = mutableMapOf<String, File>()
    
    override fun setSpeechCallback(callback: SpeechCallback?) {
        speechCallback = callback
        ttsService.setSpeechCallback(callback)
    }
    
    override fun getSpeechRate(): Float {
        return if (elevenLabs != null) settings.elevenLabsSpeed else settings.ttsSpeechRate
    }
    
    /**
     * Public stop method to halt any current speech
     */
    override fun stop() {
        stopCurrentSpeech()
    }
    
    /**
     * Stop any currently playing speech before starting new one
     */
    private fun stopCurrentSpeech() {
        // Stop MediaPlayer (ElevenLabs audio)
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        
        // Stop TTS
        ttsService.stop()
        
        isSpeaking = false
        currentUtteranceId = null
    }
    
    override fun speak(text: String) {
        if (!settings.voiceEnabled || text.isBlank()) {
            Log.d("HybridVoice", "Speak skipped - voiceEnabled: ${settings.voiceEnabled}, text empty: ${text.isBlank()}")
            return
        }
        
        // IMPORTANT: Stop any current speech before starting new one
        stopCurrentSpeech()
        
        Log.d("HybridVoice", "Speaking: \"$text\" (ElevenLabs: ${elevenLabs != null})")
        isSpeaking = true
        
        if (elevenLabs != null) {
            scope.launch(Dispatchers.Main) {
                try {
                    val cacheFile = audioCache[text] ?: File(context.cacheDir, "elevenlabs_${text.hashCode()}.mp3")
                    
                    val audioFile = withContext(Dispatchers.IO) {
                        if (cacheFile.exists()) {
                            Log.d("HybridVoice", "Using cached audio for: $text")
                            cacheFile
                        } else {
                            Log.d("HybridVoice", "Generating new audio via ElevenLabs...")
                            elevenLabs.textToSpeech(
                                text = text,
                                outputFile = cacheFile,
                                stability = settings.elevenLabsStability,
                                similarityBoost = settings.elevenLabsSimilarityBoost,
                                speed = settings.elevenLabsSpeed
                            )
                        }
                    }
                    
                    // Check if we're still supposed to be speaking (wasn't stopped)
                    if (!isSpeaking) {
                        Log.d("HybridVoice", "Speech was cancelled before playback")
                        return@launch
                    }
                    
                    if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                        audioCache[text] = audioFile
                        Log.d("HybridVoice", "✓ Playing ElevenLabs audio (${audioFile.length() / 1024}KB)")
                        playAudio(audioFile, text)
                    } else {
                        // ElevenLabs failed, notify and fallback to TTS
                        Log.w("HybridVoice", "✗ ElevenLabs failed, using TTS fallback")
                        onFallback?.invoke("ElevenLabs unavailable. Switching to default voice.")
                        ttsService.speak(text)
                    }
                } catch (e: Exception) {
                    Log.e("HybridVoice", "✗ ElevenLabs error: ${e.message}, using TTS fallback", e)
                    onFallback?.invoke("ElevenLabs error: ${e.message}. Using default voice.")
                    ttsService.speak(text)
                }
            }
        } else {
            // No ElevenLabs configured, use TTS directly
            Log.d("HybridVoice", "Using TTS directly (ElevenLabs not configured)")
            ttsService.speak(text)
        }
    }
    
    private fun playAudio(file: File, utteranceId: String) {
        try {
            // Stop and release any existing MediaPlayer
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            
            currentUtteranceId = utteranceId
            
            // Notify speech start
            speechCallback?.onSpeechStart(utteranceId)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    isSpeaking = false
                    // Notify speech done when audio finishes
                    speechCallback?.onSpeechDone(utteranceId)
                    currentUtteranceId = null
                }
                setOnErrorListener { _, what, extra ->
                    isSpeaking = false
                    speechCallback?.onSpeechError(utteranceId, "MediaPlayer error: $what, $extra")
                    currentUtteranceId = null
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            isSpeaking = false
            Log.e("HybridVoice", "Audio playback failed", e)
            speechCallback?.onSpeechError(utteranceId, e.message ?: "Unknown error")
        }
    }
    
    override fun shutdown() {
        mediaPlayer?.release()
        mediaPlayer = null
        ttsService.shutdown()
        // Clear old cache files
        audioCache.values.forEach { it.delete() }
        audioCache.clear()
    }
}

/**
 * Lightweight Android Text-to-Speech implementation for Cloudie.
 * Falls back to queuing until the engine is ready; callers should invoke shutdown() in Activity.onDestroy.
 */
class TextToSpeechVoiceService(
    context: Context,
    private val pitch: Float = 1.1f,
    private val speechRate: Float = 0.95f,
    private val localeCode: String = "en_IN", // en_IN (Indian), en_GB (British), en_US (American)
    private val onReady: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) : VoiceService, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts: TextToSpeech = TextToSpeech(appContext, this)
    private var ready = false
    private val pending = ArrayDeque<String>()
    private var speechCallback: SpeechCallback? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Parse locale code (e.g., "en_IN" -> Locale("en", "IN"))
            val locale = parseLocale(localeCode)
            Log.d("TTS", "Setting TTS locale to: $locale (from $localeCode)")
            
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TTS", "Locale $locale not available, falling back to default")
                // Try fallback to US English
                tts.setLanguage(Locale.US)
            }
            
            // Try to select a female voice if available
            selectFemaleVoice(locale)
            
            // Configure for more emotional/expressive speech
            tts.setPitch(pitch)  // Slightly higher pitch for friendlier tone (0.5-2.0 range)
            tts.setSpeechRate(speechRate)  // Slightly slower for clarity and emphasis (0.5-2.0 range)
            
            // Set up utterance progress listener for speech callbacks
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.let { speechCallback?.onSpeechStart(it) }
                }
                
                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { speechCallback?.onSpeechDone(it) }
                }
                
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { speechCallback?.onSpeechError(it, "TTS error") }
                }
                
                override fun onError(utteranceId: String?, errorCode: Int) {
                    utteranceId?.let { speechCallback?.onSpeechError(it, "TTS error code: $errorCode") }
                }
            })
            
            ready = true
            // Flush any queued lines now that TTS is ready
            while (pending.isNotEmpty()) {
                speakInternal(pending.removeFirst())
            }
            onReady?.invoke()
        } else {
            onError?.invoke("TTS init failed: status=$status")
        }
    }
    
    override fun setSpeechCallback(callback: SpeechCallback?) {
        speechCallback = callback
    }
    
    override fun getSpeechRate(): Float = speechRate

    override fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pending.addLast(text)
            // Attempt lazy re-init if engine failed earlier
            if (pending.size == 1) {
                runCatching { tts.language = Locale.UK }
            }
            return
        }
        speakInternal(text)
    }

    private fun speakInternal(text: String) {
        runCatching {
            val utteranceId = "cloudie-${System.currentTimeMillis()}"
            // Use Bundle for params (required for utterance callbacks)
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            // QUEUE_FLUSH stops any current speech before starting new one (prevents overlapping voices)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }.onFailure { error ->
            Log.e("VoiceService", "TTS speak failed", error)
            onError?.invoke("TTS speak failed: ${error.message}")
        }
    }
    
    /**
     * Stop any currently playing speech
     */
    override fun stop() {
        runCatching { tts.stop() }
        pending.clear()
    }

    override fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }
    
    /**
     * Parse locale code like "en_IN" into a Locale object
     */
    private fun parseLocale(localeCode: String): Locale {
        val parts = localeCode.split("_")
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            else -> Locale.UK // fallback
        }
    }
    
    /**
     * Attempt to select a female voice for the given locale
     */
    private fun selectFemaleVoice(locale: Locale) {
        try {
            val voices = tts.voices ?: return
            
            // Find voices matching our locale
            val matchingVoices = voices.filter { voice ->
                voice.locale.language == locale.language &&
                (locale.country.isEmpty() || voice.locale.country == locale.country) &&
                !voice.isNetworkConnectionRequired
            }
            
            // Try to find a female voice (common naming patterns)
            val femaleVoice = matchingVoices.find { voice ->
                val name = voice.name.lowercase()
                name.contains("female") || 
                name.contains("woman") ||
                // Google TTS often uses letters - voices ending in 'a' or 'e' are often female
                name.endsWith("-a-local") ||
                name.contains("wavenet-a") ||
                name.contains("wavenet-c") ||
                name.contains("wavenet-e")
            }
            
            if (femaleVoice != null) {
                tts.voice = femaleVoice
                Log.d("VoiceService", "Selected female voice: ${femaleVoice.name}")
            } else if (matchingVoices.isNotEmpty()) {
                // Use first available voice for locale
                tts.voice = matchingVoices.first()
                Log.d("VoiceService", "Using first available voice: ${matchingVoices.first().name}")
            }
            
            // Log all available voices for debugging
            Log.d("VoiceService", "Available voices for ${locale}: ${matchingVoices.map { it.name }}")
            
        } catch (e: Exception) {
            Log.e("VoiceService", "Failed to select female voice", e)
        }
    }
}
