package earth.lastdrop.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.LifecycleCoroutineScope
import earth.lastdrop.app.voice.ElevenLabsService
import earth.lastdrop.app.voice.VoiceSettings
import earth.lastdrop.app.voice.VoiceSettingsManager
import kotlinx.coroutines.launch

class VoiceSettingsDialog(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    
    private val settingsManager = VoiceSettingsManager(context)
    private var currentSettings = settingsManager.getSettings()
    
    fun show(onSettingsSaved: () -> Unit = {}) {
        val layout = createSettingsLayout()
        
        AlertDialog.Builder(context)
            .setTitle("🎙️ Voice Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                Log.d("VoiceSettings", "💾 Saving settings - VoiceID: '${currentSettings.elevenLabsVoiceId}', UseElevenLabs: ${currentSettings.useElevenLabs}, ApiKey: ${if (currentSettings.elevenLabsApiKey.isNotEmpty()) "present (${currentSettings.elevenLabsApiKey.take(8)}...)" else "empty"}")
                settingsManager.saveSettings(currentSettings)
                onSettingsSaved()
                Toast.makeText(context, "Voice settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun createSettingsLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            
            // Voice Enable/Disable
            addView(createSectionHeader("Enable Voice"))
            addView(Switch(context).apply {
                isChecked = currentSettings.voiceEnabled
                text = if (isChecked) "Voice Enabled" else "Voice Disabled"
                setOnCheckedChangeListener { _, isChecked ->
                    currentSettings = currentSettings.copy(voiceEnabled = isChecked)
                    text = if (isChecked) "Voice Enabled" else "Voice Disabled"
                }
            })
            
            addView(createDivider())
            
            // ElevenLabs Section
            addView(createSectionHeader("ElevenLabs (Premium Voice)"))
            addView(Switch(context).apply {
                isChecked = currentSettings.useElevenLabs
                text = if (isChecked) "Use ElevenLabs" else "Use Phone TTS Only"
                setOnCheckedChangeListener { _, isChecked ->
                    currentSettings = currentSettings.copy(useElevenLabs = isChecked)
                    text = if (isChecked) "Use ElevenLabs" else "Use Phone TTS Only"
                }
            })
            
            addView(TextView(context).apply {
                text = "API Key:"
                setPadding(0, 16, 0, 8)
            })
            
            val apiKeyInput = EditText(context).apply {
                hint = "Enter ElevenLabs API key"
                setText(currentSettings.elevenLabsApiKey)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine()
            }
            addView(apiKeyInput)
            
            addView(Button(context).apply {
                text = "Test API Key"
                setOnClickListener {
                    val apiKey = apiKeyInput.text.toString()
                    if (apiKey.isBlank()) {
                        Toast.makeText(context, "Please enter an API key", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    isEnabled = false
                    text = "Testing..."
                    
                    lifecycleScope.launch {
                        try {
                            val service = ElevenLabsService(apiKey)
                            val isValid = service.validateApiKey()
                            
                            isEnabled = true
                            if (isValid) {
                                text = "✓ Test API Key"
                                currentSettings = currentSettings.copy(elevenLabsApiKey = apiKey)
                                Toast.makeText(context, "API key is valid!", Toast.LENGTH_SHORT).show()
                            } else {
                                text = "Test API Key"
                                Toast.makeText(context, "Invalid API key", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            isEnabled = true
                            text = "Test API Key"
                            Toast.makeText(context, "Test failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
            
            addView(TextView(context).apply {
                text = "Voice ID (optional):"
                setPadding(0, 16, 0, 8)
            })
            
            val voiceIdInput = EditText(context).apply {
                hint = "pNInz6obpgDQGcFmaJgB (Adam - default)"
                setText(currentSettings.elevenLabsVoiceId)
                setSingleLine()
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus && text.isNotBlank()) {
                        val newVoiceId = text.toString().trim()
                        Log.d("VoiceSettings", "✏️ Voice ID changed: '$newVoiceId'")
                        currentSettings = currentSettings.copy(elevenLabsVoiceId = newVoiceId)
                    }
                }
            }
            addView(voiceIdInput)
            
            addView(Button(context).apply {
                text = "Test Voice"
                setOnClickListener {
                    val apiKey = apiKeyInput.text.toString()
                    val voiceId = voiceIdInput.text.toString().ifBlank { "pNInz6obpgDQGcFmaJgB" }
                    
                    if (apiKey.isBlank()) {
                        Toast.makeText(context, "Please enter an API key first", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    isEnabled = false
                    text = "Testing Voice..."
                    
                    lifecycleScope.launch {
                        try {
                            val service = ElevenLabsService(apiKey, voiceId)
                            val testFile = java.io.File(context.cacheDir, "voice_test_${System.currentTimeMillis()}.mp3")
                            val audioFile = service.textToSpeech(
                                text = "Hello, this is a test of the selected voice.",
                                outputFile = testFile,
                                stability = currentSettings.elevenLabsStability,
                                similarityBoost = currentSettings.elevenLabsSimilarityBoost,
                                speed = currentSettings.elevenLabsSpeed
                            )
                            
                            isEnabled = true
                            if (audioFile != null) {
                                text = "✓ Test Voice"
                                currentSettings = currentSettings.copy(elevenLabsVoiceId = voiceId)
                                
                                // Play the audio
                                val mediaPlayer = android.media.MediaPlayer().apply {
                                    setDataSource(audioFile.absolutePath)
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        release()
                                        testFile.delete()
                                    }
                                }
                                
                                Toast.makeText(context, "Voice is valid! Playing sample...", Toast.LENGTH_SHORT).show()
                            } else {
                                text = "Test Voice"
                                Toast.makeText(context, "Invalid voice ID or API error", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            isEnabled = true
                            text = "Test Voice"
                            Toast.makeText(context, "Test failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
            
            addView(TextView(context).apply {
                text = "Stability: ${"%.2f".format(currentSettings.elevenLabsStability)} (Lower = more emotional)"
                setPadding(0, 16, 0, 8)
                textSize = 12f
            })
            
            addView(SeekBar(context).apply {
                max = 100
                progress = (currentSettings.elevenLabsStability * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val stability = progress / 100f
                        currentSettings = currentSettings.copy(elevenLabsStability = stability)
                        (parent as? ViewGroup)?.let { parent ->
                            (0 until parent.childCount).forEach { i ->
                                val child = parent.getChildAt(i)
                                if (child is TextView && child.text.toString().startsWith("Stability:")) {
                                    child.text = "Stability: ${"%.2f".format(stability)} (Lower = more emotional)"
                                }
                            }
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
            
            addView(TextView(context).apply {
                text = "Similarity Boost: ${"%.2f".format(currentSettings.elevenLabsSimilarityBoost)} (Higher = closer to original)"
                setPadding(0, 16, 0, 8)
                textSize = 12f
            })
            
            addView(SeekBar(context).apply {
                max = 100
                progress = (currentSettings.elevenLabsSimilarityBoost * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val similarityBoost = progress / 100f
                        currentSettings = currentSettings.copy(elevenLabsSimilarityBoost = similarityBoost)
                        (parent as? ViewGroup)?.let { parent ->
                            (0 until parent.childCount).forEach { i ->
                                val child = parent.getChildAt(i)
                                if (child is TextView && child.text.toString().startsWith("Similarity Boost:")) {
                                    child.text = "Similarity Boost: ${"%.2f".format(similarityBoost)} (Higher = closer to original)"
                                }
                            }
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
            
            addView(TextView(context).apply {
                text = "Speed: ${"%.2f".format(currentSettings.elevenLabsSpeed)}x (0.25-2.0)"
                setPadding(0, 16, 0, 8)
                textSize = 12f
            })
            
            addView(SeekBar(context).apply {
                max = 175 // 0.25 to 2.0 (range of 1.75, mapped to 0-175)
                progress = ((currentSettings.elevenLabsSpeed - 0.25f) * 100).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val speed = 0.25f + (progress / 100f)
                        currentSettings = currentSettings.copy(elevenLabsSpeed = speed)
                        (parent as? ViewGroup)?.let { parent ->
                            (0 until parent.childCount).forEach { i ->
                                val child = parent.getChildAt(i)
                                if (child is TextView && child.text.toString().startsWith("Speed:")) {
                                    child.text = "Speed: ${"%.2f".format(speed)}x (0.25-2.0)"
                                }
                            }
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
            
            addView(TextView(context).apply {
                text = "Get your API key from elevenlabs.io"
                textSize = 12f
                setTextColor(Color.GRAY)
                setPadding(0, 8, 0, 0)
            })
            
            addView(createDivider())
            
            // Phone TTS Settings
            addView(createSectionHeader("Phone TTS Fallback"))
            
            addView(TextView(context).apply {
                text = "Voice Pitch: ${currentSettings.ttsPitch}"
                setPadding(0, 16, 0, 8)
            })
            
            addView(SeekBar(context).apply {
                max = 100
                progress = ((currentSettings.ttsPitch - 0.5f) * 100 / 1.5f).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val pitch = 0.5f + (progress / 100f * 1.5f)
                        currentSettings = currentSettings.copy(ttsPitch = pitch)
                        (parent as? ViewGroup)?.let { parent ->
                            (0 until parent.childCount).forEach { i ->
                                val child = parent.getChildAt(i)
                                if (child is TextView && child.text.toString().startsWith("Voice Pitch:")) {
                                    child.text = "Voice Pitch: ${"%.2f".format(pitch)}"
                                }
                            }
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
            
            addView(TextView(context).apply {
                text = "Speech Rate: ${currentSettings.ttsSpeechRate}"
                setPadding(0, 16, 0, 8)
            })
            
            addView(SeekBar(context).apply {
                max = 100
                progress = ((currentSettings.ttsSpeechRate - 0.5f) * 100 / 1.5f).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val rate = 0.5f + (progress / 100f * 1.5f)
                        currentSettings = currentSettings.copy(ttsSpeechRate = rate)
                        (parent as? ViewGroup)?.let { parent ->
                            (0 until parent.childCount).forEach { i ->
                                val child = parent.getChildAt(i)
                                if (child is TextView && child.text.toString().startsWith("Speech Rate:")) {
                                    child.text = "Speech Rate: ${"%.2f".format(rate)}"
                                }
                            }
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })
        }
    }
    
    private fun createSectionHeader(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.parseColor("#4CAF50"))
            setPadding(0, 16, 0, 8)
            gravity = Gravity.START
        }
    }
    
    private fun createDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                topMargin = 16
                bottomMargin = 16
            }
            setBackgroundColor(Color.GRAY)
        }
    }
}
