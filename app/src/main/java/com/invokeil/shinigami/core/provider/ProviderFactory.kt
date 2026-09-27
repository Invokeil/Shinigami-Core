package com.invokeil.shinigami.core.provider

import com.invokeil.shinigami.core.data.db.ProviderProfileEntity
import com.invokeil.shinigami.core.data.db.ProviderType
import com.invokeil.shinigami.core.security.SecretVault
import com.invokeil.shinigami.core.security.providerApiKeyRef
import com.invokeil.shinigami.core.security.providerPasswordRef
import com.invokeil.shinigami.core.util.AppError
import com.invokeil.shinigami.core.util.ShiniLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Builds configured [AiProvider] instances from profiles + secrets. */
@Singleton
class ProviderFactory @Inject constructor(
    private val openAiCompatible: OpenAiCompatibleProvider,
    private val gemini: GeminiProvider,
    private val vault: SecretVault,
) {

    suspend fun build(profile: ProviderProfileEntity): AiProvider = withContext(Dispatchers.IO) {
        val config = configFor(profile)
        when (profile.type) {
            ProviderType.GEMINI -> gemini.also { it.config = config }
            else -> openAiCompatible.also { it.config = config }
        }
    }

    fun configFor(profile: ProviderProfileEntity): ProviderConfig {
        val apiKey = if (profile.hasApiKey) vault.get(providerApiKeyRef(profile.id)) else null
        val password = if (profile.hasPassword) vault.get(providerPasswordRef(profile.id)) else null
        val extraHeaders = parseExtraHeaders(profile.extraHeadersJson)
        return ProviderConfig(
            profileId = profile.id,
            name = profile.name,
            type = profile.type,
            baseUrl = profile.baseUrl.trim(),
            model = profile.model.trim(),
            authType = profile.authType,
            apiKeyHeader = profile.apiKeyHeader,
            apiKey = apiKey,
            username = profile.username,
            password = password,
            extraHeaders = extraHeaders,
            temperature = profile.temperature,
            maxTokens = profile.maxTokens,
            allowCleartext = profile.allowCleartext,
            customInstructions = profile.customInstructions,
        )
    }

    private fun parseExtraHeaders(json: String): Map<String, String> = try {
        val obj = JSONObject(json.ifBlank { "{}" })
        buildMap {
            obj.keys().forEach { key -> put(key, obj.optString(key)) }
        }.filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
    } catch (t: Throwable) {
        ShiniLog.w("ProviderFactory", "extra headers parse failed")
        emptyMap()
    }
}

/** Useful error wrapper so callers never see providers as nullable. */
inline fun <T> withProvider(provider: AiProvider, block: () -> T): T = try {
    block()
} catch (e: AppError) {
    throw e
}
