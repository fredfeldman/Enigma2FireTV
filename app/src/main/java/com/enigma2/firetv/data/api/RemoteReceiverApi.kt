package com.enigma2.firetv.data.api

import com.enigma2.firetv.data.model.DeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Lightweight OkHttp client for targeting a specific [DeviceProfile] that is
 * NOT the currently active Retrofit client. Used by:
 * - Phase 6.1 "Zap on…"  — zap a secondary receiver to a service ref.
 * - Phase 6.4 "Send to…" — send an on-screen message to a secondary receiver.
 *
 * v1.2.0 Phase 0.3 — ported from Enigma2Android RemoteReceiverApi.
 */
object RemoteReceiverApi {

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun baseUrl(p: DeviceProfile): String {
        val scheme = if (p.useHttps) "https" else "http"
        return "$scheme://${p.host}:${p.port}"
    }

    private fun execute(p: DeviceProfile, path: String): Boolean {
        val creds = if (p.username.isNotBlank())
            Credentials.basic(p.username, p.password) else null
        val req = Request.Builder()
            .url("${baseUrl(p)}/$path")
            .apply { if (creds != null) addHeader("Authorization", creds) }
            .build()
        return try {
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Zaps [p] to [sRef]. Returns true on success. */
    suspend fun zap(p: DeviceProfile, sRef: String): Boolean = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(sRef, "UTF-8")
        execute(p, "api/zap?sRef=$encoded")
    }

    /** Sends an on-screen message to [p]. Returns true on success. */
    suspend fun message(p: DeviceProfile, text: String, type: Int = 1, timeoutSec: Int = 10): Boolean =
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(text, "UTF-8")
            execute(p, "api/message?text=$encoded&type=$type&timeout=$timeoutSec")
        }
}
