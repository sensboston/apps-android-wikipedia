package org.wikipedia.translation

import org.wikipedia.database.AppDatabase
import java.util.concurrent.ConcurrentHashMap

object TranslationCache {

    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000  // 30 days

    // In-memory cache: key = "title|sourceLang|targetLang", value = sectionIndex -> translatedHtml
    private val memCache = ConcurrentHashMap<String, Map<Int, String>>()
    private var evictionDone = false

    private fun cacheKey(title: String, sourceLang: String, targetLang: String) =
        "$title|$sourceLang|$targetLang"

    suspend fun get(title: String, sourceLang: String, targetLang: String): Map<Int, String>? {
        val key = cacheKey(title, sourceLang, targetLang)
        memCache[key]?.let { return it }
        val entries = AppDatabase.instance.translationCacheDao().getEntries(title, sourceLang, targetLang)
        if (entries.isEmpty()) return null
        val map = entries.associate { it.sectionIndex to it.translatedHtml }
        memCache[key] = map
        return map
    }

    suspend fun save(title: String, sourceLang: String, targetLang: String, translations: Map<Int, String>) {
        if (translations.isEmpty()) return
        val now = System.currentTimeMillis()
        val entries = translations.map { (idx, html) ->
            TranslationCacheEntry(title, sourceLang, targetLang, idx, html, now)
        }
        AppDatabase.instance.translationCacheDao().insertAll(entries)
        val key = cacheKey(title, sourceLang, targetLang)
        val existing = memCache[key] ?: emptyMap()
        memCache[key] = existing + translations
        if (!evictionDone) {
            evictionDone = true
            AppDatabase.instance.translationCacheDao().deleteOlderThan(now - MAX_AGE_MS)
        }
    }
}
