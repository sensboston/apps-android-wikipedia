package org.wikipedia.translation

interface TranslationProvider {
    suspend fun translate(html: String, sourceLang: String, targetLang: String, prompt: String): String
}

enum class TranslationProviderType(val id: String, val displayName: String) {
    GOOGLE("google", "Google Translate"),
    OPENAI("openai", "GPT-4o mini"),
    GEMINI("gemini", "Gemini Flash 2.0");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}
