package org.wikipedia.translation

import org.jsoup.Jsoup

object TranslationTextExtractor {

    private const val CHUNK_ELEMENTS = 7

    data class TextChunk(val indices: List<Int>, val texts: List<String>)

    fun extractChunks(html: String): List<TextChunk> {
        val doc = Jsoup.parse(html)
        val allElements = doc.select("h1, p, h2, h3, h4, li")

        // Collect only non-empty elements but keep their REAL DOM index
        // so they match JS querySelectorAll('h1,p,h2,h3,h4,li') positions
        val nonEmpty = allElements.mapIndexedNotNull { idx, el ->
            if (el.text().isNotBlank()) Pair(idx, el.html().trim()) else null
        }

        val chunks = mutableListOf<TextChunk>()
        var i = 0
        while (i < nonEmpty.size) {
            val slice = nonEmpty.subList(i, minOf(i + CHUNK_ELEMENTS, nonEmpty.size))
            chunks.add(TextChunk(slice.map { it.first }, slice.map { it.second }))
            i += CHUNK_ELEMENTS
        }
        return chunks
    }

    fun buildChunkPrompt(targetLang: String, chunk: TextChunk): String {
        val lines = chunk.indices.zip(chunk.texts).joinToString("\n") { (idx, text) -> "§$idx: $text" }
        return "You are a professional translator. Translate each line below into $targetLang.\n\n" +
            "CRITICAL RULES:\n" +
            "1. Write EVERY word in $targetLang using Cyrillic script. NEVER use Chinese, Japanese, Korean, Vietnamese, Arabic characters — not even a single one. If you know a concept in Chinese/Japanese, write its $targetLang equivalent in Cyrillic instead.\n" +
            "2. NEVER mix Cyrillic and Latin letters within a single word. BAD examples: 'ДURRELL', 'Бournemouth'. GOOD: 'Даррелл', 'Борнмут'.\n" +
            "3. Transliterate all proper names (people, cities) consistently using Cyrillic throughout. For 'Durrell' always use 'Даррелл'.\n" +
            "4. Keep §N markers and all HTML tags exactly unchanged — translate only the visible text.\n" +
            "5. Output ONLY lines in format §N: translated text — nothing else.\n\n$lines"
    }

    fun parseResponse(response: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        response.lines().forEach { line ->
            val match = Regex("^§(\\d+):\\s*(.+)$").find(line.trim())
            if (match != null) {
                result[match.groupValues[1].toInt()] = sanitize(match.groupValues[2])
            }
        }
        return result
    }

    // Remove CJK, Vietnamese diacritics, Arabic and other non-target characters that LLM may inject
    private fun sanitize(text: String): String {
        return text.replace(Regex("[\\u2E80-\\u9FFF\\uAC00-\\uD7AF\\uF900-\\uFAFF\\u1E00-\\u1EFF\\u0600-\\u06FF]"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}
