package org.wikipedia.translation

import org.wikipedia.database.AppDatabase
import java.util.concurrent.ConcurrentHashMap

object TranslationCache {

    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000  // 30 days

    // In-memory cache: key = "title|sourceLang|targetLang" -> TranslationCacheEntry
    private val memCache = ConcurrentHashMap<String, TranslationCacheEntry>()
    private var evictionDone = false

    private fun cacheKey(title: String, sourceLang: String, targetLang: String) =
        "$title|$sourceLang|$targetLang"

    // Returns cached HTML if revisionId matches, null otherwise (stale or missing)
    suspend fun get(title: String, sourceLang: String, targetLang: String, revisionId: String): String? {
        val key = cacheKey(title, sourceLang, targetLang)
        val entry = memCache[key]
            ?: AppDatabase.instance.translationCacheDao().getEntry(title, sourceLang, targetLang)
                ?.also { memCache[key] = it }
            ?: return null
        return if (entry.revisionId == revisionId) entry.translatedHtml else null
    }

    suspend fun save(title: String, sourceLang: String, targetLang: String, revisionId: String, translatedHtml: String) {
        val now = System.currentTimeMillis()
        val entry = TranslationCacheEntry(title, sourceLang, targetLang, revisionId, translatedHtml, now)
        AppDatabase.instance.translationCacheDao().insert(entry)
        memCache[cacheKey(title, sourceLang, targetLang)] = entry
        if (!evictionDone) {
            evictionDone = true
            AppDatabase.instance.translationCacheDao().deleteOlderThan(now - MAX_AGE_MS)
        }
    }
}
