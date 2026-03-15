package org.wikipedia.translation

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LibreTranslateProvider {

    private const val URL = "https://senssoft.com/libretranslate/translate"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Переводит список строк батчем. Возвращает Map<originalTitle, translatedTitle>.
     * LibreTranslate /translate принимает одну строку, поэтому отправляем строки через \n
     * и разбиваем ответ обратно.
     */
    fun translateBatch(titles: List<String>, sourceLang: String, targetLang: String): Map<String, String> {
        if (titles.isEmpty()) return emptyMap()
        val joined = titles.joinToString("\n")
        val body = JSONObject().apply {
            put("q", joined)
            put("source", sourceLang)
            put("target", targetLang)
            put("format", "text")
        }.toString().toRequestBody(JSON)
        val request = Request.Builder().url(URL).post(body).build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: return emptyMap()
        val translated = JSONObject(responseBody).getString("translatedText")
        val lines = translated.split("\n")
        return titles.zip(lines)
            .filter { (_, t) -> t.isNotBlank() }
            .toMap()
    }
}
