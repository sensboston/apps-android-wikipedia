package org.wikipedia.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.wikipedia.dataclient.gemini.GeminiContent
import org.wikipedia.dataclient.gemini.GeminiPart
import org.wikipedia.dataclient.gemini.GeminiRequest
import org.wikipedia.dataclient.gemini.GeminiResponse
import org.wikipedia.dataclient.gemini.GeminiTranslationService
import android.util.Log
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GeminiTranslationProvider(private val apiKey: String) : TranslationProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun translate(html: String, sourceLang: String, targetLang: String, prompt: String): String {
        val fullPrompt = if (prompt.isEmpty()) html else "$prompt\n\n$html"
        val request = GeminiRequest(listOf(GeminiContent(listOf(GeminiPart(fullPrompt)))))
        val requestBody = json.encodeToString(request).toByteArray(Charsets.UTF_8)
        return withContext(Dispatchers.IO) {
            val connection = URL("${GeminiTranslationService.GENERATE_URL}?key=$apiKey")
                .openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(requestBody) }
                val responseBody = connection.inputStream.bufferedReader().readText()
                val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
                geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IllegalStateException("Empty response from Gemini")
            } catch (e: Exception) {
                val errorCode = runCatching { connection.responseCode }.getOrDefault(-1)
                val errorBody = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull()
                Log.e("GeminiTranslation", "Error $errorCode: $errorBody", e)
                throw IllegalStateException("Gemini error $errorCode: $errorBody", e)
            } finally {
                connection.disconnect()
            }
        }
    }
}
