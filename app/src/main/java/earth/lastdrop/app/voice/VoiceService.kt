package earth.lastdrop.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
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
 * Lightweight Android Text-to-Speech implementation for Cloudie.
 * Falls back to queuing until the engine is ready; callers should invoke shutdown() in Activity.onDestroy.
 */
class TextToSpeechVoiceService(
    context: Context,
    private val onReady: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) : VoiceService, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts: TextToSpeech = TextToSpeech(appContext, this)
    private var ready = false
    private val pending = ArrayDeque<String>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                onError?.invoke("TTS language not supported")
                return
            }
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
                runCatching { tts.language = Locale.US }
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
