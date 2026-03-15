package org.wikipedia.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GoogleTranslationProvider : TranslationProvider {

    override suspend fun translate(html: String, sourceLang: String, targetLang: String, prompt: String): String {
        val postData = "client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=${URLEncoder.encode(html, "UTF-8")}"
        val connection = withContext(Dispatchers.IO) {
            (URL("https://translate.googleapis.com/translate_a/single").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "close")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
                outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = withContext(Dispatchers.IO) { connection.responseCode }
        if (code !in 200..299) {
            withContext(Dispatchers.IO) { connection.errorStream?.close() }
            throw Exception("Translation failed: HTTP $code")
        }
        val response = withContext(Dispatchers.IO) { connection.inputStream.bufferedReader().readText() }
        // Response format: [[["translated_segment", "original", ...], ...], null, "en", ...]
        val segments = JSONArray(response).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until segments.length()) {
            sb.append(segments.getJSONArray(i).optString(0))
        }
        val result = sb.toString()
        return result
    }
}
