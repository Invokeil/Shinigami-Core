package com.invokeil.shinigami.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/* ---------------------------------------------------------------------------
 * Room entities. Credential VALUES never live here — only boolean markers.
 * Real secrets are held by SecretVault behind string references (§96).
 * --------------------------------------------------------------------------*/

enum class ProviderType { OPENAI, GEMINI, MISTRAL, GLM, OLLAMA, OPENAI_COMPATIBLE }

enum class AuthType { NONE, BEARER, API_KEY_HEADER, BASIC }

@Entity(tableName = "provider_profiles")
data class ProviderProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val model: String,
    val authType: AuthType = AuthType.BEARER,
    val apiKeyHeader: String = "Authorization",
    val username: String? = null,
    val hasApiKey: Boolean = false,
    val hasPassword: Boolean = false,
    val extraHeadersJson: String = "{}",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val customInstructions: String = "",
    val isDefault: Boolean = false,
    val allowCleartext: Boolean = false,
    val supportsStreaming: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val temporary: Boolean = false,
)

enum class MessageRole { USER, ASSISTANT, SYSTEM, TOOL }

@Entity(
    tableName = "messages",
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val providerId: Long? = null,
    val model: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val latencyMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

enum class ActionSource { OFFLINE_PARSER, AI_AGENT, ROUTINE, USER_UI }

@Entity(tableName = "audit_events")
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val source: ActionSource,
    val result: String, // SUCCESS | FAILED | DENIED | CONFIRMED | CANCELLED
    val detail: String? = null,
    val targetPackage: String? = null,
    val riskLevel: RiskLevel = RiskLevel.LOW,
)

@Entity(tableName = "usage_records", indices = [Index("timestamp")])
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val latencyMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "custom_commands", indices = [Index(value = ["phrase"], unique = true)])
data class CustomCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phrase: String,
    val toolId: String,
    val argumentsJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "memory_entries")
data class MemoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // PREFERENCE | FREQUENT_COMMAND | ALIAS | FACT
    val key: String,
    val value: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "protected_apps")
data class ProtectedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val addedAt: Long = System.currentTimeMillis(),
)
