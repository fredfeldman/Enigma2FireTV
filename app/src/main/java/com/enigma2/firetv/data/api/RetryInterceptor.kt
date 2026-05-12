package com.enigma2.firetv.data.api

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * Retries transient network failures with exponential backoff.
 *
 * Only safe (idempotent) methods are retried automatically: GET, HEAD, OPTIONS.
 * Mutating calls (POST/PUT/DELETE) are passed through unchanged so we never
 * accidentally schedule a recording or delete a movie twice.
 *
 * Retries are attempted on:
 *   * [SocketTimeoutException] / [InterruptedIOException] — slow / dropped link
 *   * [SocketException]                                   — connection reset
 *   * Generic [IOException] when the response is missing  — DNS, connect refused, etc.
 *   * 5xx server responses                                — transient OpenWebif failure
 */
class RetryInterceptor(
    private val maxAttempts: Int = 3,
    private val initialBackoffMs: Long = 400L
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isSafeMethod = request.method.let { it == "GET" || it == "HEAD" || it == "OPTIONS" }

        var attempt = 0
        var lastException: IOException? = null
        var lastResponse: Response? = null

        while (attempt < maxAttempts) {
            // Close any prior response body before retrying
            lastResponse?.close()
            lastResponse = null

            try {
                val response = chain.proceed(request)
                if (!isSafeMethod || response.code < 500) return response
                // Retryable 5xx — fall through to backoff
                lastResponse = response
            } catch (e: IOException) {
                if (!isSafeMethod) throw e
                lastException = e
            }

            attempt++
            if (attempt >= maxAttempts) break

            val backoff = initialBackoffMs shl (attempt - 1) // 400, 800, 1600...
            try {
                Thread.sleep(backoff)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during retry backoff", ie)
            }
        }

        return lastResponse
            ?: throw lastException
            ?: IOException("Request failed after $maxAttempts attempts")
    }
}
