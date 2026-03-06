package org.wikipedia.dataclient.gemini

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface GeminiTranslationService {

    @POST
    suspend fun generateContent(
        @Url url: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    companion object {
        const val API_URL = "https://generativelanguage.googleapis.com/v1beta/"
        const val GENERATE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent"
    }
}
