package org.wikipedia.translation

class OpenAITranslationProvider(private val apiKey: String) : TranslationProvider {
    override suspend fun translate(html: String, sourceLang: String, targetLang: String, prompt: String): String {
        throw NotImplementedError("OpenAI provider not yet implemented")
    }
}
