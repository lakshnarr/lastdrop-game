package earth.lastdrop.app.ai.cloudie

import android.content.res.Resources
import androidx.annotation.RawRes
import org.json.JSONObject
import java.io.BufferedReader
import kotlin.random.Random

/**
 * Lightweight phrase library loader for Cloudie personas backed by a JSON file in res/raw.
 * Maps CloudieEventType -> list of templates per persona and picks one at random.
 */
class CloudiePhraseBook(
    private val phrases: Map<String, Map<CloudieEventType, List<String>>>
) {
    fun lineFor(persona: String, event: CloudieEventType, data: Map<String, String>): String? {
        val personaKey = persona.lowercase()
        val options = phrases[personaKey]?.get(event) ?: phrases[DEFAULT_PERSONA]?.get(event)
        if (options.isNullOrEmpty()) return null
        val template = options.random(Random.Default)
        return render(template, data)
    }

    private fun render(template: String, data: Map<String, String>): String {
        var out = template
        data.forEach { (key, value) ->
            out = out.replace("{$key}", value)
        }
        return out
    }

    companion object {
        private const val DEFAULT_PERSONA = "cloudie"

        fun load(resources: Resources, @RawRes resId: Int): CloudiePhraseBook {
            val text = resources.openRawResource(resId).bufferedReader().use(BufferedReader::readText)
            val root = JSONObject(text)
            val personaMap = mutableMapOf<String, Map<CloudieEventType, List<String>>>()

            val personas = root.getJSONArray("personas")
            for (i in 0 until personas.length()) {
                val personaObj = personas.getJSONObject(i)
                val name = personaObj.getString("name").lowercase()
                val eventsObj = personaObj.getJSONObject("events")
                val eventMap = mutableMapOf<CloudieEventType, List<String>>()
                CloudieEventType.values().forEach { type ->
                    if (eventsObj.has(type.name)) {
                        val arr = eventsObj.getJSONArray(type.name)
                        val list = mutableListOf<String>()
                        for (j in 0 until arr.length()) {
                            list.add(arr.getString(j))
                        }
                        if (list.isNotEmpty()) {
                            eventMap[type] = list
                        }
                    }
                }
                if (eventMap.isNotEmpty()) {
                    personaMap[name] = eventMap
                }
            }

            return CloudiePhraseBook(personaMap)
        }
    }
}
