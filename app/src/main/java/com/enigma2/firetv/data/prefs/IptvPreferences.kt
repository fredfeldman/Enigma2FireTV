package com.enigma2.firetv.data.prefs

import android.content.Context
import com.enigma2.firetv.data.model.IptvSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class IptvPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Legacy single-source fields (kept for backward compat with standalone IptvActivity)
    var m3uUrl: String
        get() = prefs.getString("m3u_url", "") ?: ""
        set(v) = prefs.edit().putString("m3u_url", v).apply()

    var epgUrl: String
        get() = prefs.getString("epg_url", DEFAULT_EPG_URL) ?: DEFAULT_EPG_URL
        set(v) = prefs.edit().putString("epg_url", v).apply()

    // -------------------------------------------------------------------------
    // Multi-source support
    // -------------------------------------------------------------------------

    fun getSources(): List<IptvSource> {
        val json = prefs.getString("sources", null)
        if (!json.isNullOrBlank()) {
            return try {
                val type = object : TypeToken<List<IptvSource>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }
        // Backward compat: if the old single m3u_url is set, migrate it as a source
        val oldUrl = m3uUrl
        if (oldUrl.isNotBlank()) {
            val migrated = IptvSource(name = "IPTV", m3uUrl = oldUrl, epgUrl = epgUrl)
            saveSources(listOf(migrated))
            return listOf(migrated)
        }
        return emptyList()
    }

    fun addSource(source: IptvSource) {
        val current = getSources().toMutableList()
        current.add(source)
        saveSources(current)
    }

    fun removeSource(sourceId: String) {
        val current = getSources().toMutableList()
        current.removeAll { it.id == sourceId }
        saveSources(current)
    }

    private fun saveSources(sources: List<IptvSource>) {
        prefs.edit().putString("sources", gson.toJson(sources)).apply()
    }

    companion object {
        /** Default EPG provider — full combined XML from EPGSHARE01. */
        const val DEFAULT_EPG_URL =
            "http://epgshare01.online/epgshare01/epg_ripper_ALL_SOURCES1.xml.gz"
    }
}
