package com.invokeil.shinigami.core.provider

import com.invokeil.shinigami.core.util.AppError
import com.invokeil.shinigami.core.util.Redactor
import com.invokeil.shinigami.core.util.ShiniLog
import java.io.IOException
import java.net.URI
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/* ---------------------------------------------------------------------------
 * Shared plumbing for HTTP based providers: header construction, URL
 * normalisation, error mapping and SSE reading.
 * --------------------------------------------------------------------------*/

object ProviderHttp {

    val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    fun validateBaseUrl(raw: String, allowCleartext: Boolean): AppError? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return AppError.InvalidConfig("Base URL is empty.")
        val uri: URI = try {
            URI.create(trimmed)
        } catch (t: Exception) {
            return AppError.InvalidConfig("Base URL is not a valid URL.")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return AppError.InvalidConfig("Base URL must start with http:// or https://")
        }
        if (scheme == "http" && !isLocalAddress(uri.host) && !allowCleartext) {
            return AppError.InvalidConfig(
                "Non-HTTPS endpoint. Enable the LAN/cleartext allowance for this provider first.",
            )
        }
        return null
    }

    fun isLocalAddress(host: String?): Boolean {
        if (host == null) return false
        return host == "localhost" || host == "127.0.0.1" || host.startsWith("192.168.") ||
            host.startsWith("10.") || host.startsWith("172.16.") || host.startsWith("172.17.") ||
            host.startsWith("172.18.") || host.startsWith("172.19.") || host.startsWith("172.2") ||
            host.startsWith("172.30.") || host.startsWith("172.31.") || host.endsWith(".local")
    }

    fun applyAuth(builder: Request.Builder, config: ProviderConfig) {
        config.extraHeaders.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) builder.header(k.trim(), v.trim())
        }
        when (config.authType) {
            com.invokeil.shinigami.core.data.db.AuthType.NONE -> Unit
            com.invokeil.shinigami.core.data.db.AuthType.BEARER -> config.apiKey?.let {
                builder.header("Authorization", "Bearer $it")
            }

            com.invokeil.shinigami.core.data.db.AuthType.API_KEY_HEADER -> {
                val header = config.apiKeyHeader.ifBlank { "x-api-key" }
                config.apiKey?.let { builder.header(header, it) }
            }

            com.invokeil.shinigami.core.data.db.AuthType.BASIC -> {
                val user = config.username.orEmpty()
                val pass = config.password.orEmpty()
                val token = okhttp3.Credentials.basic(user, pass)
                builder.header("Authorization", token)
            }
        }
    }

    fun postJson(
        client: OkHttpClient,
        url: String,
        config: ProviderConfig,
        body: String,
    ): Response {
        val builder = Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA))
        applyAuth(builder, config)
        return client.newCall(builder.build()).execute()
    }

    fun get(
        client: OkHttpClient,
        url: String,
        config: ProviderConfig,
    ): Response {
        val builder = Request.Builder().url(url).get()
        applyAuth(builder, config)
        return client.newCall(builder.build()).execute()
    }

    /** Maps a failed HTTP response to a typed [AppError]; closes the response. */
    fun errorFor(response: Response): AppError {
        val code = response.code
        val bodyText = try {
            response.body?.string().orEmpty().take(2000)
        } catch (_: Exception) {
            ""
        } finally {
            response.close()
        }
        val detail = Redactor.redact(
            bodyText.lineSequence().firstOrNull { it.isNotBlank() } ?: "HTTP $code",
        )
        return AppError.Http(code, "HTTP $code", detail)
    }

    /** Throws [AppError] mapped exceptions for I/O failures. */
    inline fun <T> guardIo(tag: String, block: () -> T): T = try {
        block()
    } catch (e: java.net.SocketTimeoutException) {
        ShiniLog.w(tag, "provider timeout")
        throw AppError.Timeout
    } catch (e: IOException) {
        ShiniLog.w(tag, "provider io failure: ${e.message}")
        throw AppError.Network(e.message ?: "network error")
    }

    /** Reads an SSE stream, invoking [onEvent] for every `data:` payload. */
    inline fun readSse(
        response: Response,
        crossinline onEvent: (payload: String) -> Unit,
    ) {
        response.use { resp ->
            if (!resp.isSuccessful) throw errorFor(resp)
            val source = resp.body?.source() ?: return
            while (true) {
                val line = try {
                    source.readUtf8Line() ?: break
                } catch (e: IOException) {
                    // Server closed mid-stream (common with local servers after [DONE]).
                    break
                }
                if (line.startsWith("data:")) {
                    onEvent(line.removePrefix("data:").trim())
                } else if (line.startsWith("event:") || line.startsWith(":")) {
                    // keep-alive comments and named events are ignored
                }
            }
        }
    }

    fun trimBase(baseUrl: String): String = baseUrl.trim().trimEnd('/')

    fun joinUrl(base: String, path: String): String {
        val b = trimBase(base)
        return if (path.startsWith("/")) b + path else "$b/$path"
    }
}
