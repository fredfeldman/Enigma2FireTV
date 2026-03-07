package com.enigma2.firetv.data.api

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton that builds (and caches) the [OpenWebifService] Retrofit client.
 * Call [initialize] whenever the receiver settings change.
 */
object ApiClient {

    private var _service: OpenWebifService? = null

    /** Last configured base URL so we can detect changes. */
    private var currentBaseUrl: String = ""

    val service: OpenWebifService
        get() = _service ?: error("ApiClient not initialized. Call initialize() first.")

    /**
     * @param host        IP address or hostname of the receiver
     * @param port        HTTP port (default 80)
     * @param useHttps    true to use HTTPS
     * @param username    optional HTTP Basic Auth username
     * @param password    optional HTTP Basic Auth password
     */
    fun initialize(
        host: String,
        port: Int = 80,
        useHttps: Boolean = false,
        username: String = "",
        password: String = ""
    ) {
        val scheme = if (useHttps) "https" else "http"
        val baseUrl = "$scheme://$host:$port/"

        if (baseUrl == currentBaseUrl && _service != null) return

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .apply {
                if (username.isNotBlank()) {
                    val credentials = Base64.encodeToString(
                        "$username:$password".toByteArray(), Base64.NO_WRAP
                    )
                    addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("Authorization", "Basic $credentials")
                            .build()
                        chain.proceed(request)
                    }
                }
            }
            .build()

        _service = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWebifService::class.java)

        currentBaseUrl = baseUrl
    }

    /** Builds the HLS/HTTP stream URL for a given service reference. */
    fun streamUrl(host: String, @Suppress("UNUSED_PARAMETER") port: Int = 80, useHttps: Boolean = false, serviceRef: String): String {
        // Enigma2 stream port is usually 8001 (TS over HTTP).
        // OpenWebif can also serve a .m3u redirect at /web/stream.m3u?ref=
        val scheme = if (useHttps) "https" else "http"
        val encodedRef = serviceRef.trim()
        return "$scheme://$host:8001/$encodedRef"
    }

    /** Returns the base URL for building picon image URLs. */
    fun piconBaseUrl(host: String, port: Int = 80, useHttps: Boolean = false): String {
        val scheme = if (useHttps) "https" else "http"
        return "$scheme://$host:$port"
    }
}
