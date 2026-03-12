package org.wikipedia.translation

import androidx.room.Entity

@Entity(primaryKeys = ["title", "sourceLang", "targetLang"])
data class TranslationCacheEntry(
    val title: String,
    val sourceLang: String,
    val targetLang: String,
    val revisionId: String,
    val translatedHtml: String,
    val timestamp: Long
)
