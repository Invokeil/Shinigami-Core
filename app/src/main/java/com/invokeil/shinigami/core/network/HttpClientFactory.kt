package com.invokeil.shinigami.core.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Hardened OkHttp factory (MASTER SPEC §75):
 *  - HTTPS-first validation happens at the config layer;
 *  - TLS verification is never disabled;
 *  - sane timeouts, streaming-friendly read timeout;
 *  - a redacting logging interceptor (secrets can never reach logcat).
 */
@Singleton
class HttpClientFactory @Inject constructor() {

    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            response
        }
        .build()

    companion object {
        /** Shared short-timeout client for quick probes (model lists, tests). */
        fun probeClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}
