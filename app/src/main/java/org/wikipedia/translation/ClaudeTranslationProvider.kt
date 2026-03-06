package org.wikipedia.translation

class ClaudeTranslationProvider(private val apiKey: String) : TranslationProvider {
    override suspend fun translate(html: String, sourceLang: String, targetLang: String, prompt: String): String {
        throw NotImplementedError("Claude provider not yet implemented")
    }
}
