package com.invokeil.shinigami.core.provider

import com.invokeil.shinigami.core.network.HttpClientFactory
import com.invokeil.shinigami.core.util.AppError
import com.invokeil.shinigami.core.util.AppResult
import com.invokeil.shinigami.core.util.ShiniLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/* ---------------------------------------------------------------------------
 * Google Gemini provider (Generative Language API).
 * Uses the OpenAI-less native REST surface with SSE streaming.
 * --------------------------------------------------------------------------*/

@Singleton
class GeminiProvider @Inject constructor(
    private val httpClientFactory: HttpClientFactory,
) : AiProvider {

    private val client: OkHttpClient by lazy { httpClientFactory.create() }
    private val tag = "GeminiProvider"

    lateinit var config: ProviderConfig

    override val capabilities = ProviderCapabilities(
        streaming = true,
        modelDiscovery = true,
        toolCalling = false,
        vision = true,
        jsonMode = false,
    )

    private fun base(): String =
        if (config.baseUrl.contains("generativelanguage", ignoreCase = true) || config.baseUrl.isBlank()) {
            DEFAULT_BASE
        } else {
            ProviderHttp.trimBase(config.baseUrl)
        }

    override suspend fun testConnection(): ProviderTestResult = withContext(Dispatchers.IO) {
        try {
            ProviderHttp.guardIo(tag) {
                val start = System.currentTimeMillis()
                val resp = ProviderHttp.get(client, "${base()}/models", config)
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    resp.close()
                    val models = parseModels(body)
                    val knows = models.any { it.id.contains(config.model) } && config.model.isNotBlank()
                    return@withContext ProviderTestResult.Success(
                        latencyMs = System.currentTimeMillis() - start,
                        modelChecked = config.model.ifBlank { models.firstOrNull()?.id },
                        streamingSupported = true,
                        detail = buildString {
                            append("API reachable · ${models.size} models")
                            if (config.model.isNotBlank()) {
                                append(if (knows) " · model listed" else " · model not in list (will be used as-is)")
                            }
                        },
                    )
                }
                ProviderTestResult.Failure(
                    ProviderHttp.errorFor(resp),
                    "GET /models failed",
                )
            }
        } catch (e: AppError) {
            ProviderTestResult.Failure(e, e.detail ?: e.userMessage)
        } catch (t: Throwable) {
            ProviderTestResult.Failure(AppError.Unknown(t.message ?: "unknown"), t.toString())
        }
    }

    override suspend fun getModels(): List<AiModel> = withContext(Dispatchers.IO) {
        ProviderHttp.guardIo(tag) {
            val resp = ProviderHttp.get(client, "${base()}/models", config)
            if (!resp.isSuccessful) throw ProviderHttp.errorFor(resp)
            val body = resp.body?.string().orEmpty()
            resp.close()
            parseModels(body)
        }
    }

    internal fun parseModels(body: String): List<AiModel> = try {
        val models = JSONObject(body).optJSONArray("models") ?: JSONArray()
        (0 until models.length())
            .map { models.optJSONObject(it) }
            .filterNotNull()
            .filter { m ->
                val methods = m.optJSONArray("supportedGenerationMethods")
                if (methods == null) true
                else (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            }
            .map { m ->
                val raw = m.optString("name", "")
                AiModel(
                    id = raw.removePrefix("models/"),
                    displayName = m.optString("displayName", raw.removePrefix("models/")),
                    supportsVision = m.optString("name").contains("vision") ||
                        m.optString("name").contains("flash") || m.optString("name").contains("pro"),
                )
            }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
    } catch (t: Throwable) {
        ShiniLog.w(tag, "gemini model parse failed")
        emptyList()
    }

    override suspend fun complete(request: AiRequest): AppResult<AiResponse> =
        withContext(Dispatchers.IO) {
            try {
                ProviderHttp.guardIo(tag) {
                    val start = System.currentTimeMillis()
                    val endpoint = "${base()}/models/${request.model}:generateContent"
                    val resp = ProviderHttp.postJson(client, endpoint, config, requestBody(request))
                    if (!resp.isSuccessful) throw ProviderHttp.errorFor(resp)
                    val text = resp.body?.string().orEmpty()
                    resp.close()
                    val json = JSONObject(text)
                    val content = extractText(json)
                    val usage = json.optJSONObject("usageMetadata")
                    AppResult.Success(
                        AiResponse(
                            text = content,
                            inputTokens = usage?.optInt("promptTokenCount"),
                            outputTokens = usage?.optInt("candidatesTokenCount"),
                            latencyMs = System.currentTimeMillis() - start,
                            model = request.model,
                        ),
                    )
                }
            } catch (e: AppError) {
                AppResult.Failure(e)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Unknown(t.message ?: t::class.java.simpleName))
            }
        }

    override fun stream(request: AiRequest): Flow<AiStreamEvent> = flow {
        try {
            val endpoint = "${base()}/models/${request.model}:streamGenerateContent?alt=sse"
            val resp = withContext(Dispatchers.IO) {
                ProviderHttp.postJson(client, endpoint, config, requestBody(request))
            }
            if (!resp.isSuccessful) {
                emit(AiStreamEvent.Failed(ProviderHttp.errorFor(resp)))
                return@flow
            }
            try {
                val source = resp.body?.source()
                if (source == null) {
                    resp.close()
                    emit(AiStreamEvent.Failed(AppError.Network("empty response body")))
                    return@flow
                }
                while (true) {
                    val line = try {
                        source.readUtf8Line() ?: break
                    } catch (e: java.io.IOException) {
                        break // server closed — normal SSE end
                    }
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    val json = try {
                        JSONObject(payload)
                    } catch (_: Exception) {
                        continue
                    }
                    val text = extractText(json)
                    if (text.isNotEmpty()) emit(AiStreamEvent.Delta(text))
                    val usage = json.optJSONObject("usageMetadata")
                    if (usage != null) {
                        emit(
                            AiStreamEvent.Usage(
                                usage.optInt("promptTokenCount").takeIf { it > 0 },
                                usage.optInt("candidatesTokenCount").takeIf { it > 0 },
                            ),
                        )
                    }
                }
            } finally {
                runCatching { resp.close() }
            }
            emit(AiStreamEvent.Done)
        } catch (e: AppError) {
            emit(AiStreamEvent.Failed(e))
        } catch (t: Throwable) {
            emit(AiStreamEvent.Failed(AppError.Unknown(t.message ?: t::class.java.simpleName)))
        }
    }.flowOn(Dispatchers.IO)

    private fun requestBody(request: AiRequest): String {
        val contents = JSONArray()
        request.messages
            .filter { it.role != ChatMessage.Role.SYSTEM }
            .forEach { m ->
                val role = when (m.role) {
                    ChatMessage.Role.ASSISTANT -> "model"
                    else -> "user"
                }
                contents.put(
                    JSONObject()
                        .put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", m.content))),
                )
            }
        val body = JSONObject().put("contents", contents)
        val system = request.messages.filter { it.role == ChatMessage.Role.SYSTEM }
        if (system.isNotEmpty()) {
            body.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", system.joinToString("\n") { it.content })),
                ),
            )
        }
        body.put(
            "generationConfig",
            JSONObject()
                .put("temperature", request.temperature.toDouble())
                .put("maxOutputTokens", request.maxTokens),
        )
        return body.toString()
    }

    private fun extractText(json: JSONObject): String = buildString {
        val candidates = json.optJSONArray("candidates") ?: return@buildString
        for (i in 0 until candidates.length()) {
            val parts = candidates.optJSONObject(i)?.optJSONObject("content")?.optJSONArray("parts")
                ?: continue
            for (j in 0 until parts.length()) {
                append(parts.optJSONObject(j)?.optString("text").orEmpty())
            }
        }
    }

    private companion object {
        const val DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}
