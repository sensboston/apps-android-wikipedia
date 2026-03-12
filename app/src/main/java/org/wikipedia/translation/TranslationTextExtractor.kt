package org.wikipedia.translation

import org.jsoup.Jsoup

object TranslationTextExtractor {

    private const val CHUNK_ELEMENTS = 7

    data class TextChunk(val indices: List<Int>, val texts: List<String>)

    fun extractChunks(html: String): List<TextChunk> {
        val doc = Jsoup.parse(html)
        val allElements = doc.select("h1, p, h2, h3, h4, li, div.hatnote")

        // Collect only non-empty elements but keep their REAL DOM index
        // so they match JS querySelectorAll('h1,p,h2,h3,h4,li,div.hatnote') positions
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

    // Returns combined HTML with data-wt="domIndex" on each element, and the list of domIndices in order
    fun extractForGoogle(html: String): Pair<String, List<Int>> {
        val doc = Jsoup.parse(html)
        val allElements = doc.select("h1, p, h2, h3, h4, li, div.hatnote")
        val nonEmpty = allElements.mapIndexedNotNull { idx, el ->
            if (el.text().isNotBlank()) Pair(idx, el) else null
        }
        nonEmpty.forEach { (idx, el) -> el.attr("data-wt", idx.toString()) }
        val combined = nonEmpty.joinToString("") { (_, el) -> el.outerHtml() }
        return Pair(combined, nonEmpty.map { it.first })
    }

    fun parseGoogleResponse(translatedHtml: String): Map<Int, String> {
        val doc = Jsoup.parse(translatedHtml)
        val result = mutableMapOf<Int, String>()
        doc.select("[data-wt]").forEach { el ->
            val idx = el.attr("data-wt").toIntOrNull() ?: return@forEach
            el.removeAttr("data-wt")
            result[idx] = el.html()
        }
        return result
    }

    fun buildChunkPrompt(targetLang: String, chunk: TextChunk): String {
        val lines = chunk.indices.zip(chunk.texts).joinToString("\n") { (idx, text) -> "§$idx: $text" }
        return "You are a professional translator. Translate each line below into $targetLang.\n\n" +
            "CRITICAL RULES:\n" +
            "1. Write EVERY Russian word in Cyrillic script only. NEVER use Chinese, Japanese, Korean, Vietnamese, Arabic, Greek characters — not even a single one.\n" +
            "2. NEVER insert English or other European language words where a Russian word is needed. BAD: 'pending утверждения', 'сudden падением', 'którego'. GOOD: 'при условии утверждения', 'внезапным падением', 'которого'.\n" +
            "3. NEVER mix Cyrillic and Latin letters within a single word. BAD: 'ДURRELL', 'entwickanный'. GOOD: 'Даррелл', 'разработанный'.\n" +
            "4. Proper names of people and cities — transliterate to Cyrillic. Brand names, product names, technical abbreviations (Lucasfilm, THX, ILM) — keep in Latin as-is.\n" +
            "5. Keep §N markers and all HTML tags exactly unchanged — translate only the visible text between tags. NEVER translate HTML attribute values (href, title, src, class, id, etc.).\n" +
            "6. Output ONLY lines in format §N: translated text — nothing else.\n\n$lines"
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

    // Remove CJK, Greek, Vietnamese diacritics, Arabic and other non-target characters that LLM may inject
    private fun sanitize(text: String): String {
        return text.replace(Regex("[\\u2E80-\\u9FFF\\uAC00-\\uD7AF\\uF900-\\uFAFF\\u1E00-\\u1EFF\\u0600-\\u06FF\\u0370-\\u03FF]"), "")
            // Fix words that mix Cyrillic and Latin scripts — remove the Latin chars from such words
            .replace(Regex("[\\p{L}']+")) { mr ->
                val word = mr.value
                val hasCyrillic = word.any { it in '\u0400'..'\u04FF' }
                val hasLatin = word.any { it in 'a'..'z' || it in 'A'..'Z' }
                if (hasCyrillic && hasLatin) word.replace(Regex("[a-zA-Z]"), "") else word
            }
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}
