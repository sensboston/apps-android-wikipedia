package org.wikipedia.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GroqTranslationProvider(private val apiKey: String) : TranslationProvider {

    override suspend fun translate(html: String, sourceLang: String, targetLang: String, prompt: String): String {
        val fullPrompt = if (prompt.isEmpty()) html else "$prompt\n\n$html"
        return withContext(Dispatchers.IO) {
            val connection = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            val jsonBody = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", fullPrompt)
                }))
                put("max_tokens", 32768)
            }.toString()
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val response = connection.inputStream.bufferedReader().readText()
            JSONObject(response).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content").trim()
        }
    }
}
