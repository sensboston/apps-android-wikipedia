package org.wikipedia.translation

import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.settings.Prefs

object TranslationManager {

    fun getProvider(): TranslationProvider {
        val apiKey = Prefs.autoTranslateApiKey
        return when (TranslationProviderType.fromId(Prefs.autoTranslateProvider)) {
            TranslationProviderType.OPENAI -> OpenAITranslationProvider(apiKey)
            TranslationProviderType.GROQ -> GroqTranslationProvider(apiKey)
            TranslationProviderType.GEMINI -> GeminiTranslationProvider(apiKey)
        }
    }

    fun buildPrompt(sourceLang: String, targetLang: String): String {
        val template = Prefs.autoTranslatePrompt.ifEmpty {
            WikipediaApp.instance.getString(R.string.auto_translate_default_prompt)
        }
        return template.replace("{source}", sourceLang).replace("{target}", targetLang)
    }
}
