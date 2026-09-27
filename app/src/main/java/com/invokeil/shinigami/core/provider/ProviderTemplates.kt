package com.invokeil.shinigami.core.provider

import com.invokeil.shinigami.core.data.db.AuthType
import com.invokeil.shinigami.core.data.db.ProviderType

/* ---------------------------------------------------------------------------
 * Built-in provider templates (MASTER SPEC §5). Templates pre-fill the
 * editor; the user always sees and can change every value.
 * --------------------------------------------------------------------------*/

data class ProviderTemplate(
    val type: ProviderType,
    val displayName: String,
    val defaultBaseUrl: String,
    val suggestedModel: String,
    val authType: AuthType,
    val apiKeyHeader: String = "Authorization",
    val note: String,
    val keyHint: String,
)

object ProviderTemplates {

    val ALL = listOf(
        ProviderTemplate(
            type = ProviderType.OPENAI,
            displayName = "OpenAI",
            defaultBaseUrl = "https://api.openai.com/v1",
            suggestedModel = "gpt-4o-mini",
            authType = AuthType.BEARER,
            note = "Uses the OpenAI API (platform.openai.com), not a ChatGPT login.",
            keyHint = "sk-…",
        ),
        ProviderTemplate(
            type = ProviderType.GEMINI,
            displayName = "Google Gemini",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
            suggestedModel = "gemini-1.5-flash",
            authType = AuthType.API_KEY_HEADER,
            apiKeyHeader = "x-goog-api-key",
            note = "Free tier available at aistudio.google.com.",
            keyHint = "AIza…",
        ),
        ProviderTemplate(
            type = ProviderType.MISTRAL,
            displayName = "Mistral",
            defaultBaseUrl = "https://api.mistral.ai/v1",
            suggestedModel = "mistral-small-latest",
            authType = AuthType.BEARER,
            note = "OpenAI-compatible endpoint from Mistral AI.",
            keyHint = "mistral key",
        ),
        ProviderTemplate(
            type = ProviderType.GLM,
            displayName = "GLM / Z.AI",
            defaultBaseUrl = "https://api.z.ai/api/paas/v4",
            suggestedModel = "glm-4-flash",
            authType = AuthType.BEARER,
            note = "Z.AI / Zhipu GLM family (OpenAI-compatible).",
            keyHint = "api key id.secret",
        ),
        ProviderTemplate(
            type = ProviderType.OLLAMA,
            displayName = "Ollama (local)",
            defaultBaseUrl = "http://localhost:11434/v1",
            suggestedModel = "llama3.2",
            authType = AuthType.NONE,
            note = "Runs on your own device or LAN. No key, fully offline.",
            keyHint = "",
        ),
        ProviderTemplate(
            type = ProviderType.OPENAI_COMPATIBLE,
            displayName = "OpenAI-compatible / Custom",
            defaultBaseUrl = "https://",
            suggestedModel = "",
            authType = AuthType.BEARER,
            note = "Any server exposing /chat/completions — LM Studio, vLLM, your VPS…",
            keyHint = "optional",
        ),
    )

    fun forType(type: ProviderType): ProviderTemplate = ALL.first { it.type == type }
}
