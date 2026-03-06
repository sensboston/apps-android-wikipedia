package org.wikipedia.translation

interface TranslationProvider {
    suspend fun translate(html: String, sourceLang: String, targetLang: String, prompt: String): String
}

enum class TranslationProviderType(val id: String, val displayName: String) {
    GEMINI("gemini", "Gemini Flash 2.0"),
    CLAUDE("claude", "Claude Haiku"),
    OPENAI("openai", "GPT-4o mini"),
    DEEPSEEK("deepseek", "DeepSeek V3"),
    GPT4FREE("gpt4free", "GPT4Free (local)"),
    GROQ("groq", "Groq Llama 3.3 70B");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}
