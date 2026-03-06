package org.wikipedia.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class Gpt4FreeTranslationProvider : TranslationProvider {

    override suspend fun translate(html: String, sourceLang: String, targetLang: String, prompt: String): String {
        val fullPrompt = if (prompt.isEmpty()) html else "$prompt\n\n$html"
        return withContext(Dispatchers.IO) {
            val connection = URL("https://senssoft.com/gptapi/").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            val jsonBody = org.json.JSONObject().apply {
                put("provider", "CohereForAI_C4AI_Command")
                put("q", fullPrompt)
            }.toString()
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            connection.inputStream.bufferedReader().readText().trim()
        }
    }
}
