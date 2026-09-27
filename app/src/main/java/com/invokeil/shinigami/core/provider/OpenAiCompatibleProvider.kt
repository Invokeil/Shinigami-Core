package com.invokeil.shinigami.core.provider

import com.invokeil.shinigami.core.network.HttpClientFactory
import com.invokeil.shinigami.core.util.AppError
import java.io.IOException
import com.invokeil.shinigami.core.util.AppResult
import com.invokeil.shinigami.core.util.ShiniLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/* ---------------------------------------------------------------------------
 * OpenAI-compatible provider: covers OpenAI, Mistral, GLM/Z.AI, Ollama,
 * LM Studio, llama.cpp, vLLM and every custom `/v1/chat/completions` server.
 * --------------------------------------------------------------------------*/

@Singleton
class OpenAiCompatibleProvider @Inject constructor(
    private val httpClientFactory: HttpClientFactory,
) : AiProvider {

    private val client: OkHttpClient by lazy { httpClientFactory.create() }
    private val tag = "OpenAiCompat"

    lateinit var config: ProviderConfig

    override val capabilities = ProviderCapabilities(
        streaming = true,
        modelDiscovery = true,
        toolCalling = false, // structured JSON envelope is used for every model (§24)
        vision = false,
        jsonMode = false,
    )

    private fun url(path: String) = ProviderHttp.joinUrl(config.baseUrl, path)

    private fun jsonHeaders(body: JSONObject): String = body.toString()

    override suspend fun testConnection(): ProviderTestResult = withContext(Dispatchers.IO) {
        ProviderHttp.validateBaseUrl(config.baseUrl, config.allowCleartext)?.let {
            return@withContext ProviderTestResult.Failure(it, it.userMessage)
        }        // 1) try model discovery (cheapest authenticated check)
        try {
            ProviderHttp.guardIo(tag) {
                val start = System.currentTimeMillis()
                val resp = ProviderHttp.get(client, url("models"), config)
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    resp.close()
                    val latency = System.currentTimeMillis() - start
                    val ids = parseModelIds(body)
                    val knowsModel = ids.any { it.equals(config.model, ignoreCase = true) }
                    return@withContext ProviderTestResult.Success(
                        latencyMs = latency,
                        modelChecked = config.model.ifBlank { ids.firstOrNull() },
                        streamingSupported = true,
                        detail = buildString {
                            append(if (ids.isEmpty()) "Endpoint reachable" else "Model list received")
                            if (config.model.isNotBlank()) {
                                append(if (knowsModel) " · model listed" else " · model not in list (will be used as-is)")
                            }
                        },
                    )
                }
                resp.close()
            }
        } catch (e: AppError) {
            if (e is AppError.Http && e.code == 404) {
                // Many custom servers do not implement /models — fall through to a tiny completion.
            } else {
                return@withContext ProviderTestResult.Failure(e, e.detail ?: e.userMessage)
            }
        } catch (t: Throwable) {
            return@withContext ProviderTestResult.Failure(
                AppError.Unknown(t.message ?: "unknown"),
                t.toString(),
            )
        }
        // 2) tiny completion probe
        try {
            ProviderHttp.guardIo(tag) {
                val start = System.currentTimeMillis()
                val body = JSONObject().apply {
                    put("model", config.model)
                    put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
                    put("max_tokens", 1)
                    put("stream", false)
                }
                val resp = ProviderHttp.postJson(client, url("chat/completions"), config, jsonHeaders(body))
                if (resp.isSuccessful) {
                    resp.body?.close()
                    return@withContext ProviderTestResult.Success(
                        latencyMs = System.currentTimeMillis() - start,
                        modelChecked = config.model,
                        streamingSupported = true,
                        detail = "Chat completion responded · model available",
                    )
                }
                return@withContext ProviderTestResult.Failure(
                    ProviderHttp.errorFor(resp),
                    "chat/completions probe failed",
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
            val resp = ProviderHttp.get(client, url("models"), config)
            if (!resp.isSuccessful) throw ProviderHttp.errorFor(resp)
            val body = resp.body?.string().orEmpty()
            resp.close()
            parseModels(body)
        }
    }

    internal fun parseModels(body: String): List<AiModel> = try {
        val json = JSONObject(body)
        val data = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
        (0 until data.length())
            .map { data.optJSONObject(it) }
            .filterNotNull()
            .map { obj ->
                val id = obj.optString("id", obj.optString("name", ""))
                AiModel(id = id)
            }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
    } catch (t: Throwable) {
        ShiniLog.w(tag, "model list parse failed")
        emptyList()
    }

    private fun parseModelIds(body: String): List<String> =
        parseModels(body).map { it.id }

    override suspend fun complete(request: AiRequest): AppResult<AiResponse> =
        withContext(Dispatchers.IO) {
            try {
                ProviderHttp.guardIo(tag) {
                    val start = System.currentTimeMillis()
                    val body = requestBody(request, stream = false)
                    val resp = ProviderHttp.postJson(client, url("chat/completions"), config, body)
                    if (!resp.isSuccessful) throw ProviderHttp.errorFor(resp)
                    val text = resp.body?.string().orEmpty()
                    resp.close()
                    val json = JSONObject(text)
                    val choice = json.optJSONArray("choices")?.optJSONObject(0)
                    val content = choice?.optJSONObject("message")?.optString("content").orEmpty()
                    val usage = json.optJSONObject("usage")
                    AppResult.Success(
                        AiResponse(
                            text = content,
                            inputTokens = usage?.optInt("prompt_tokens"),
                            outputTokens = usage?.optInt("completion_tokens"),
                            latencyMs = System.currentTimeMillis() - start,
                            model = json.optString("model", request.model),
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
            val body = requestBody(request, stream = true)
            val resp = withContext(Dispatchers.IO) {
                ProviderHttp.postJson(client, url("chat/completions"), config, body)
            }
            if (!resp.isSuccessful) {
                emit(AiStreamEvent.Failed(ProviderHttp.errorFor(resp)))
                return@flow
            }
            val source = resp.body?.source()
            if (source == null) {
                resp.close()
                emit(AiStreamEvent.Failed(AppError.Network("empty response body")))
                return@flow
            }
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val json = try {
                        JSONObject(payload)
                    } catch (_: Exception) {
                        continue
                    }
                    val delta = json.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("delta")?.optString("content")
                    if (!delta.isNullOrEmpty()) emit(AiStreamEvent.Delta(delta))
                    val usage = json.optJSONObject("usage")
                    if (usage != null) {
                        emit(
                            AiStreamEvent.Usage(
                                usage.optInt("prompt_tokens").takeIf { it > 0 },
                                usage.optInt("completion_tokens").takeIf { it > 0 },
                            ),
                        )
                    }
                }
            } catch (e: IOException) {
                // Local servers often close right after [DONE]; treat as normal end
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

    private fun requestBody(request: AiRequest, stream: Boolean): String {
        val messages = JSONArray()
        request.messages.forEach { m ->
            messages.put(
                JSONObject().put("role", m.role.name.lowercase()).put("content", m.content),
            )
        }
        val body = JSONObject()
            .put("model", request.model)
            .put("messages", messages)
            .put("temperature", request.temperature.toDouble())
            .put("max_tokens", request.maxTokens)
            .put("stream", stream)
        return body.toString()
    }
}
