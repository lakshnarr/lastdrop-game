package earth.lastdrop.app.voice

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Minimal voice hook placeholder. Replace with real TTS/ElevenLabs later.
 */
interface VoiceService {
    fun speak(text: String)
    fun shutdown() {}
}

class NoOpVoiceService(private val context: Context) : VoiceService {
    override fun speak(text: String) {
        Log.d("VoiceService", "(stub) Cloudie would say: $text")
        // Hook real TTS/streaming here. Keep non-blocking.
    }
}

/**
 * Hybrid voice service that tries ElevenLabs first, falls back to TTS
 */
class HybridVoiceService(
    private val context: Context,
    private val settings: VoiceSettings,
    private val onReady: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) : VoiceService {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val elevenLabs: ElevenLabsService? = if (settings.useElevenLabs && settings.elevenLabsApiKey.isNotBlank()) {
        Log.d("HybridVoice", "✓ ElevenLabs enabled with API key: ${settings.elevenLabsApiKey.take(8)}... voiceId: ${settings.elevenLabsVoiceId}")
        ElevenLabsService(settings.elevenLabsApiKey, settings.elevenLabsVoiceId)
    } else {
        Log.d("HybridVoice", "✗ ElevenLabs disabled - useElevenLabs: ${settings.useElevenLabs}, hasApiKey: ${settings.elevenLabsApiKey.isNotBlank()}")
        null
    }
    
    private val ttsService: TextToSpeechVoiceService = TextToSpeechVoiceService(
        context,
        pitch = settings.ttsPitch,
        speechRate = settings.ttsSpeechRate,
        onReady = onReady,
        onError = onError
    )
    
    private var mediaPlayer: MediaPlayer? = null
    private val audioCache = mutableMapOf<String, File>()
    
    override fun speak(text: String) {
        if (!settings.voiceEnabled || text.isBlank()) {
            Log.d("HybridVoice", "Speak skipped - voiceEnabled: ${settings.voiceEnabled}, text empty: ${text.isBlank()}")
            return
        }
        
        Log.d("HybridVoice", "Speaking: \"$text\" (ElevenLabs: ${elevenLabs != null})")
        
        if (elevenLabs != null) {
            scope.launch {
                try {
                    val cacheFile = audioCache[text] ?: File(context.cacheDir, "elevenlabs_${text.hashCode()}.mp3")
                    
                    val audioFile = if (cacheFile.exists()) {
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
                    
                    if (audioFile != null) {
                        audioCache[text] = audioFile
                        Log.d("HybridVoice", "✓ Playing ElevenLabs audio (${audioFile.length() / 1024}KB)")
                        playAudio(audioFile)
                    } else {
                        // ElevenLabs failed, fallback to TTS
                        Log.w("HybridVoice", "✗ ElevenLabs failed, using TTS fallback")
                        ttsService.speak(text)
                    }
                } catch (e: Exception) {
                    Log.e("HybridVoice", "✗ ElevenLabs error: ${e.message}, using TTS fallback", e)
                    ttsService.speak(text)
                }
            }
        } else {
            // No ElevenLabs configured, use TTS directly
            Log.d("HybridVoice", "Using TTS directly (ElevenLabs not configured)")
            ttsService.speak(text)
        }
    }
    
    private fun playAudio(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("HybridVoice", "Audio playback failed", e)
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
    private val onReady: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) : VoiceService, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts: TextToSpeech = TextToSpeech(appContext, this)
    private var ready = false
    private val pending = ArrayDeque<String>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Use UK English for British accent
            val result = tts.setLanguage(Locale.UK)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                onError?.invoke("TTS language not supported")
                return
            }
            
            // Configure for more emotional/expressive speech
            tts.setPitch(pitch)  // Slightly higher pitch for friendlier tone (0.5-2.0 range)
            tts.setSpeechRate(speechRate)  // Slightly slower for clarity and emphasis (0.5-2.0 range)
            
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
            // QUEUE_ADD prevents cutting off previous lines; intros and dialogs often enqueue multiple lines.
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "cloudie-${System.currentTimeMillis()}")
        }.onFailure { error ->
            Log.e("VoiceService", "TTS speak failed", error)
            onError?.invoke("TTS speak failed: ${error.message}")
        }
    }

    override fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }
}
