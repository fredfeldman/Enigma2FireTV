package com.enigma2.firetv.data.repository

import android.content.Context
import com.enigma2.firetv.data.model.IptvChannel
import com.enigma2.firetv.data.model.IptvEpgEvent
import com.enigma2.firetv.data.parser.M3uParser
import com.enigma2.firetv.data.parser.XmltvParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class IptvRepository(private val context: Context) {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // -------------------------------------------------------------------------
    // Channels
    // -------------------------------------------------------------------------

    suspend fun fetchChannels(m3uUrl: String): List<IptvChannel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(m3uUrl).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw IOException("Empty M3U response")
        M3uParser.parse(body)
    }

    fun loadCachedChannels(): List<IptvChannel>? {
        val file = File(context.filesDir, "iptv_channels.json")
        if (!file.exists()) return null
        return try {
            val type = object : TypeToken<List<IptvChannel>>() {}.type
            gson.fromJson(file.readText(), type)
        } catch (_: Exception) { null }
    }

    fun saveCachedChannels(channels: List<IptvChannel>) {
        File(context.filesDir, "iptv_channels.json").writeText(gson.toJson(channels))
    }

    /** Per-source cache keyed by [sourceId]. */
    fun loadCachedChannels(sourceId: String): List<IptvChannel>? {
        val file = File(context.filesDir, "iptv_channels_$sourceId.json")
        if (!file.exists()) return null
        return try {
            val type = object : TypeToken<List<IptvChannel>>() {}.type
            gson.fromJson(file.readText(), type)
        } catch (_: Exception) { null }
    }

    fun saveCachedChannels(sourceId: String, channels: List<IptvChannel>) {
        File(context.filesDir, "iptv_channels_$sourceId.json").writeText(gson.toJson(channels))
    }

    // -------------------------------------------------------------------------
    // EPG
    // -------------------------------------------------------------------------

    /**
     * Fetch and parse an XMLTV EPG feed (plain or gzip-compressed).
     * Gzip detection is done via magic bytes so it works regardless of whether
     * OkHttp's transparent decompression ran or not.
     */
    suspend fun fetchEpg(
        epgUrl: String,
        channelIds: Set<String>
    ): Map<String, List<IptvEpgEvent>> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(epgUrl).build()
        val response = client.newCall(request).execute()
        val bodyStream = response.body?.byteStream()
            ?: throw IOException("Empty EPG response")
        val buffered = BufferedInputStream(bodyStream)
        // Peek at magic bytes to detect gzip without relying on headers
        buffered.mark(2)
        val magic = ByteArray(2)
        val read = buffered.read(magic)
        buffered.reset()
        val stream = if (read == 2 &&
            magic[0] == 0x1f.toByte() &&
            magic[1] == 0x8b.toByte()
        ) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }
        XmltvParser.parse(stream, channelIds)
    }

    fun loadCachedEpg(): Map<String, List<IptvEpgEvent>>? {
        val file = File(context.filesDir, "iptv_epg.json")
        if (!file.exists()) return null
        return try {
            val type = object : TypeToken<Map<String, List<IptvEpgEvent>>>() {}.type
            gson.fromJson(file.readText(), type)
        } catch (_: Exception) { null }
    }

    fun saveCachedEpg(epg: Map<String, List<IptvEpgEvent>>) {
        File(context.filesDir, "iptv_epg.json").writeText(gson.toJson(epg))
    }

    /** Per-source EPG cache keyed by [sourceId]. */
    fun loadCachedEpg(sourceId: String): Map<String, List<IptvEpgEvent>>? {
        val file = File(context.filesDir, "iptv_epg_$sourceId.json")
        if (!file.exists()) return null
        return try {
            val type = object : TypeToken<Map<String, List<IptvEpgEvent>>>() {}.type
            gson.fromJson(file.readText(), type)
        } catch (_: Exception) { null }
    }

    fun saveCachedEpg(sourceId: String, epg: Map<String, List<IptvEpgEvent>>) {
        File(context.filesDir, "iptv_epg_$sourceId.json").writeText(gson.toJson(epg))
    }

    /** Returns milliseconds since the EPG cache was last written (MAX_VALUE if absent). */
    fun epgCacheAgeMs(): Long {
        val file = File(context.filesDir, "iptv_epg.json")
        if (!file.exists()) return Long.MAX_VALUE
        return System.currentTimeMillis() - file.lastModified()
    }
}
