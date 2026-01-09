package earth.lastdrop.app.voice

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * ElevenLabs Text-to-Speech API Client
 * Generates high-quality emotional voice audio
 */
class ElevenLabsService(
    private val apiKey: String,
    voiceIdParam: String = "pNInz6obpgDQGcFmaJgB" // Default: Adam (British male)
) {
    private val voiceId: String = voiceIdParam
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val baseUrl = "https://api.elevenlabs.io/v1"
    
    /**
     * Convert text to speech and save to file
     * @return File path if successful, null if failed
     */
    suspend fun textToSpeech(
        text: String,
        outputFile: File,
        stability: Float = 0.5f, // 0-1, lower = more variable/emotional
        similarityBoost: Float = 0.75f, // 0-1, higher = closer to original voice
        speed: Float = 1.0f // 0.25-4.0, speech speed multiplier
    ): File? = withContext(Dispatchers.IO) {
        try {
            Log.d("ElevenLabs", "🎙️ TTS Request - VoiceID: '$voiceId', Stability: $stability, Similarity: $similarityBoost, Speed: $speed")
            Log.d("ElevenLabs", "Text (${text.length} chars): ${text.take(50)}...")
            
            val json = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_monolingual_v1")
                put("voice_settings", JSONObject().apply {
                    put("stability", stability)
                    put("similarity_boost", similarityBoost)
                    put("speed", speed)
                })
            }
            
            val request = Request.Builder()
                .url("$baseUrl/text-to-speech/$voiceId")
                .addHeader("xi-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "audio/mpeg")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d("ElevenLabs", "Calling API: $baseUrl/text-to-speech/$voiceId")
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error details"
                    Log.e("ElevenLabs", "API call failed: ${response.code} ${response.message}")
                    Log.e("ElevenLabs", "Error body: $errorBody")
                    return@withContext null
                }
                
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (outputFile.exists() && outputFile.length() > 0) {
                    Log.d("ElevenLabs", "✓ Audio saved: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                    outputFile
                } else {
                    Log.e("ElevenLabs", "✗ Output file empty or not created")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("ElevenLabs", "✗ TTS generation failed: ${e.message}", e)
            null
        }
    }
    
    /**
     * Check if API key is valid
     */
    suspend fun validateApiKey(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/user")
                .addHeader("xi-api-key", apiKey)
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("ElevenLabs", "API key validation failed", e)
            false
        }
    }
}
