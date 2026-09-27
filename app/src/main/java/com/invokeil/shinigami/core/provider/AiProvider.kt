package com.invokeil.shinigami.core.provider

import com.invokeil.shinigami.core.util.AppError
import kotlinx.coroutines.flow.Flow

/* ---------------------------------------------------------------------------
 * Common provider abstraction (MASTER SPEC §11).
 * UI and agent code depend ONLY on this interface — never on provider
 * specific request/response shapes.
 * --------------------------------------------------------------------------*/

data class ChatMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { SYSTEM, USER, ASSISTANT, TOOL }
}

data class AiRequest(
    val messages: List<ChatMessage>,
    val model: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val forceJsonEnvelope: Boolean = true,
)

data class AiResponse(
    val text: String,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val latencyMs: Long = 0,
    val model: String,
)

sealed interface AiStreamEvent {
    data class Delta(val text: String) : AiStreamEvent
    data class Usage(val inputTokens: Int?, val outputTokens: Int?) : AiStreamEvent
    data object Done : AiStreamEvent
    data class Failed(val error: AppError) : AiStreamEvent
}

data class AiModel(
    val id: String,
    val displayName: String = id,
    val supportsStreaming: Boolean = true,
    val supportsToolCalling: Boolean = false,
    val supportsVision: Boolean = false,
    val contextLength: Long? = null,
)

data class ProviderCapabilities(
    val streaming: Boolean = true,
    val modelDiscovery: Boolean = true,
    val toolCalling: Boolean = false,
    val vision: Boolean = false,
    val jsonMode: Boolean = false,
)

sealed interface ProviderTestResult {
    data class Success(
        val latencyMs: Long,
        val modelChecked: String?,
        val streamingSupported: Boolean?,
        val detail: String,
    ) : ProviderTestResult

    data class Failure(
        val error: AppError,
        val technicalDetails: String,
    ) : ProviderTestResult
}

/**
 * Fully resolved, provider-agnostic runtime configuration. Built from a
 * [com.invokeil.shinigami.core.data.db.ProviderProfileEntity] + SecretVault.
 */
data class ProviderConfig(
    val profileId: Long,
    val name: String,
    val type: com.invokeil.shinigami.core.data.db.ProviderType,
    val baseUrl: String,
    val model: String,
    val authType: com.invokeil.shinigami.core.data.db.AuthType,
    val apiKeyHeader: String,
    val apiKey: String?,
    val username: String?,
    val password: String?,
    val extraHeaders: Map<String, String>,
    val temperature: Float,
    val maxTokens: Int,
    val allowCleartext: Boolean,
    val customInstructions: String,
)

interface AiProvider {
    val capabilities: ProviderCapabilities

    suspend fun testConnection(): ProviderTestResult

    suspend fun getModels(): List<AiModel>

    suspend fun complete(request: AiRequest): com.invokeil.shinigami.core.util.AppResult<AiResponse>

    fun stream(request: AiRequest): Flow<AiStreamEvent>
}
