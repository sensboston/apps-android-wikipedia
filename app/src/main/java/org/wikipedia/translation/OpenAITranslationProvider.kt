package org.wikipedia.translation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OpenAITranslationProvider(private val apiKey: String) : TranslationProvider {

    override suspend fun translate(html: String, sourceLang: String, targetLang: String, prompt: String): String {
        val fullPrompt = if (prompt.isEmpty()) html else "$prompt\n\n$html"
        val jsonBody = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", fullPrompt)
            }))
            put("max_tokens", 4096)
        }.toString()
        val t0 = System.currentTimeMillis()
        repeat(5) { attempt ->
            val connection = withContext(Dispatchers.IO) {
                (URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Connection", "close")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 120000
                    outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = withContext(Dispatchers.IO) { connection.responseCode }
            if (code in 200..299) {
                val response = withContext(Dispatchers.IO) { connection.inputStream.bufferedReader().readText() }
                Log.d("AutoTranslate", "OpenAI chunk OK in ${System.currentTimeMillis() - t0}ms attempt=${attempt + 1}")
                return JSONObject(response).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
            }
            val error = withContext(Dispatchers.IO) { connection.errorStream?.bufferedReader()?.readText() ?: "HTTP $code" }
            if (code == 429) {
                val retryAfter = Regex("Please try again in ([\\d.]+)s", RegexOption.IGNORE_CASE)
                    .find(error)?.groupValues?.get(1)?.toDoubleOrNull() ?: 10.0
                Log.w("AutoTranslate", "OpenAI 429 retry after ${retryAfter}s (attempt ${attempt + 1})")
                delay(((retryAfter + 1.0) * 1000).toLong())
            } else {
                throw Exception("OpenAI HTTP $code: $error")
            }
        }
        throw Exception("OpenAI: exceeded retry limit")
    }
}
