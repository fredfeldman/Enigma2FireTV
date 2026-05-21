package com.enigma2.firetv.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.enigma2.firetv.data.model.EpgEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Phase 4 offline cache for multi-service EPG payloads.
 *
 * Stores a JSON snapshot of the most recently fetched EPG per
 * (active device, bouquet) pair so a flaky network or an offline receiver
 * still yields a usable guide on app open. The viewmodel writes on every
 * successful fetch and falls back to the cache on failure.
 *
 * Pref keys deliberately match the Enigma2Android sibling for parity.
 */
class EpgCacheStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /** Persists [events] under the (device, bouquet) key with a write timestamp. */
    fun save(deviceId: String, bouquetRef: String, events: List<EpgEvent>) {
        val key = makeKey(deviceId, bouquetRef)
        prefs.edit()
            .putString(key, gson.toJson(events))
            .putLong(timeKey(key), System.currentTimeMillis())
            .apply()
    }

    /** Returns the cached EPG list, or `null` if no snapshot exists for this key. */
    fun load(deviceId: String, bouquetRef: String): List<EpgEvent>? {
        val key = makeKey(deviceId, bouquetRef)
        val json = prefs.getString(key, null) ?: return null
        return runCatching {
            val type = object : TypeToken<List<EpgEvent>>() {}.type
            gson.fromJson<List<EpgEvent>>(json, type)
        }.getOrNull()
    }

    /** Epoch-ms of the last successful save, or 0 if no cache exists. */
    fun savedAtMs(deviceId: String, bouquetRef: String): Long =
        prefs.getLong(timeKey(makeKey(deviceId, bouquetRef)), 0L)

    /** Convenience: minutes since [savedAtMs]; -1 if no cache. */
    fun ageMinutes(deviceId: String, bouquetRef: String): Long {
        val saved = savedAtMs(deviceId, bouquetRef)
        if (saved <= 0L) return -1
        return (System.currentTimeMillis() - saved) / 60_000L
    }

    private fun makeKey(deviceId: String, bouquetRef: String): String =
        "epg|" + deviceId.ifBlank { "default" } + "|" + bouquetRef

    private fun timeKey(key: String): String = "$key|ts"

    companion object {
        const val PREFS_NAME = "epg_cache"
    }
}
