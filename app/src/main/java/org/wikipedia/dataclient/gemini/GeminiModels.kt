package org.wikipedia.dataclient.gemini

import kotlinx.serialization.Serializable

@Serializable
class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig()
)

@Serializable
class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user"
)

@Serializable
class GeminiPart(
    val text: String
)

@Serializable
class GeminiGenerationConfig(
    val temperature: Float = 0.1f
)

@Serializable
class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
class GeminiCandidate(
    val content: GeminiContent = GeminiContent(emptyList())
)
