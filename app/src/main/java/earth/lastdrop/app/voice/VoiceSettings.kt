package earth.lastdrop.app.voice

import android.content.Context
import android.content.SharedPreferences

data class VoiceSettings(
    val voiceEnabled: Boolean = true,
    val useElevenLabs: Boolean = true,
    val elevenLabsApiKey: String = "",
    val elevenLabsVoiceId: String = "pNInz6obpgDQGcFmaJgB", // Default: Adam voice
    val elevenLabsStability: Float = 0.5f, // 0-1: lower = more variable/emotional
    val elevenLabsSimilarityBoost: Float = 0.75f, // 0-1: higher = closer to original voice
    val elevenLabsSpeed: Float = 1.0f, // 0.25-4.0: speech speed multiplier
    val ttsPitch: Float = 1.1f,
    val ttsSpeechRate: Float = 0.95f,
    val ttsLocale: String = "en_IN" // TTS language: en_IN (Indian), en_GB (British), en_US (American)
)

class VoiceSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
    
    fun getSettings(): VoiceSettings {
        return VoiceSettings(
            voiceEnabled = prefs.getBoolean("voice_enabled", true),
            useElevenLabs = prefs.getBoolean("use_elevenlabs", false), // Default to false (use local TTS)
            elevenLabsApiKey = prefs.getString("elevenlabs_api_key", "") ?: "",
            elevenLabsVoiceId = prefs.getString("elevenlabs_voice_id", "pNInz6obpgDQGcFmaJgB") ?: "pNInz6obpgDQGcFmaJgB",
            elevenLabsStability = prefs.getFloat("elevenlabs_stability", 0.5f),
            elevenLabsSimilarityBoost = prefs.getFloat("elevenlabs_similarity_boost", 0.75f),
            elevenLabsSpeed = prefs.getFloat("elevenlabs_speed", 1.0f),
            ttsPitch = prefs.getFloat("tts_pitch", 1.1f),
            ttsSpeechRate = prefs.getFloat("tts_speech_rate", 0.95f),
            ttsLocale = prefs.getString("tts_locale", "en_IN") ?: "en_IN"
        )
    }
    
    fun saveSettings(settings: VoiceSettings) {
        prefs.edit().apply {
            putBoolean("voice_enabled", settings.voiceEnabled)
            putBoolean("use_elevenlabs", settings.useElevenLabs)
            putString("elevenlabs_api_key", settings.elevenLabsApiKey)
            putString("elevenlabs_voice_id", settings.elevenLabsVoiceId)
            putFloat("elevenlabs_stability", settings.elevenLabsStability)
            putFloat("elevenlabs_similarity_boost", settings.elevenLabsSimilarityBoost)
            putFloat("elevenlabs_speed", settings.elevenLabsSpeed)
            putFloat("tts_pitch", settings.ttsPitch)
            putFloat("tts_speech_rate", settings.ttsSpeechRate)
            putString("tts_locale", settings.ttsLocale)
            apply()
        }
    }
}
