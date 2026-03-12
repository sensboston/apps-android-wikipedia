package org.wikipedia.translation

object TranslationManager {

    fun getProvider(): TranslationProvider = GoogleTranslationProvider()

    fun isGoogleProvider() = true
}
