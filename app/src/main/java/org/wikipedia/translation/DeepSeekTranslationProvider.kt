package org.wikipedia.translation

class DeepSeekTranslationProvider(private val apiKey: String) : TranslationProvider {
    override suspend fun translate(html: String, sourceLang: String, targetLang: String, prompt: String): String {
        throw NotImplementedError("DeepSeek provider not yet implemented")
    }
}
