package org.wikipedia.translation

import org.jsoup.Jsoup

object TranslationTextExtractor {

    private const val CHUNK_ELEMENTS = 15

    data class TextChunk(val indices: List<Int>, val texts: List<String>)

    fun extractChunks(html: String): List<TextChunk> {
        val doc = Jsoup.parse(html)
        val allElements = doc.select("p, h2, h3, h4, li")

        // Collect only non-empty elements but keep their REAL DOM index
        // so they match JS querySelectorAll('p,h2,h3,h4,li') positions
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
        return "Translate each line to $targetLang. Keep §N markers and all HTML tags unchanged — translate only the text between tags. Output only §N: translated content lines, one per line, nothing else:\n$lines"
    }

    fun parseResponse(response: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        response.lines().forEach { line ->
            val match = Regex("^§(\\d+):\\s*(.+)$").find(line.trim())
            if (match != null) {
                result[match.groupValues[1].toInt()] = match.groupValues[2]
            }
        }
        return result
    }
}
