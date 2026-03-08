package org.wikipedia.translation

import androidx.room.Entity

@Entity(primaryKeys = ["title", "sourceLang", "targetLang", "sectionIndex"])
data class TranslationCacheEntry(
    val title: String,
    val sourceLang: String,
    val targetLang: String,
    val sectionIndex: Int,
    val translatedHtml: String,
    val timestamp: Long
)
