package org.wikipedia.translation

import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.settings.Prefs

object TranslationManager {

    fun getProvider(): TranslationProvider {
        val apiKey = Prefs.autoTranslateApiKey
        return when (TranslationProviderType.fromId(Prefs.autoTranslateProvider)) {
            TranslationProviderType.GEMINI -> GeminiTranslationProvider(apiKey)
            TranslationProviderType.CLAUDE -> ClaudeTranslationProvider(apiKey)
            TranslationProviderType.OPENAI -> OpenAITranslationProvider(apiKey)
            TranslationProviderType.DEEPSEEK -> DeepSeekTranslationProvider(apiKey)
            TranslationProviderType.GPT4FREE -> Gpt4FreeTranslationProvider()
            TranslationProviderType.GROQ -> GroqTranslationProvider(apiKey)
        }
    }

    fun buildPrompt(sourceLang: String, targetLang: String): String {
        val template = Prefs.autoTranslatePrompt.ifEmpty {
            WikipediaApp.instance.getString(R.string.auto_translate_default_prompt)
        }
        return template.replace("{source}", sourceLang).replace("{target}", targetLang)
    }
}
