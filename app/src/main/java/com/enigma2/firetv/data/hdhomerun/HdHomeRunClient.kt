package com.enigma2.firetv.data.hdhomerun

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal HDHomeRun network-tuner discovery + lineup fetching.
 *
 * v1.2.0 Phase 5.5 — ported from Enigma2Android HdHomeRunClient verbatim
 * (package renamespaced to com.enigma2.firetv).
 */
object HdHomeRunClient {

    data class Channel(
        val name: String,
        val number: String,
        val url: String,
        val isProtected: Boolean
    )

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Hits `/discover.json` on [host] and returns a short device-info map.
     * Returns null on failure (host unreachable, wrong device type, etc.).
     */
    suspend fun discover(host: String): Map<String, String>? = withContext(Dispatchers.IO) {
        runCatching<Map<String, String>?> {
            val resp = http.newCall(Request.Builder().url("http://$host/discover.json").build()).execute()
            if (!resp.isSuccessful) {
                null
            } else {
                val obj = JSONObject(resp.body!!.string())
                buildMap { for (key in obj.keys()) put(key, obj.optString(key)) }
            }
        }.getOrNull()
    }

    /**
     * Fetches `/lineup.json` from [host] and returns the parsed channel list.
     * Returns an empty list on failure.
     */
    suspend fun fetchLineup(host: String): List<Channel> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = http.newCall(Request.Builder().url("http://$host/lineup.json").build()).execute()
            if (!resp.isSuccessful) return@runCatching emptyList<Channel>()
            val arr = JSONArray(resp.body!!.string())
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = obj.optString("URL").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Channel(
                    name = obj.optString("GuideName", "Channel $i"),
                    number = obj.optString("GuideNumber", ""),
                    url = url,
                    isProtected = obj.optBoolean("DRM", false)
                )
            }
        }.getOrDefault(emptyList())
    }

    // ── Enigma2 ref builder ───────────────────────────────────────────────────

    /**
     * Builds an Enigma2 IPTV service reference from an HDHomeRun [channel].
     * The URL is percent-encoded per Enigma2 convention.
     * [transcode] — optional transcode profile ("heavy", "mobile", etc.); empty = no transcoding.
     */
    fun toEnigma2Ref(channel: Channel, transcode: String = ""): String {
        val rawUrl = if (transcode.isNotBlank()) {
            val sep = if ('?' in channel.url) '&' else '?'
            "${channel.url}${sep}transcode=$transcode"
        } else {
            channel.url
        }
        val encoded = rawUrl
            .replace(":", "%3a")
            .replace("/", "%2f")
            .replace("?", "%3f")
            .replace("=", "%3d")
            .replace("&", "%26")
        return "4097:0:1:0:0:0:0:0:0:0:$encoded:"
    }

    val TRANSCODE_PROFILES = listOf("heavy", "mobile", "internet480", "internet360", "internet240", "none")
}
