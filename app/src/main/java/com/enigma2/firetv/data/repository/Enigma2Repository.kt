package com.enigma2.firetv.data.repository

import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.model.NowNextEvent
import com.enigma2.firetv.data.model.Recording
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.model.Timer
import com.enigma2.firetv.data.model.TimerDeleteResponse
import com.enigma2.firetv.data.model.TimerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody

/**
 * Single source of truth for all Enigma2 data.
 * All network calls are dispatched on [Dispatchers.IO].
 */
class Enigma2Repository {

    // -------------------------------------------------------------------------
    // Bouquets & Channels
    // -------------------------------------------------------------------------

    /**
     * Fetches all top-level bouquets (TV channel lists).
     * Returns an empty list on error.
     */
    suspend fun getBouquets(): List<Bouquet> = withContext(Dispatchers.IO) {
        ApiClient.service.getAllServices().bouquets ?: emptyList()
    }

    /**
     * Fetches child services (channels) for a given bouquet reference.
     * Propagates exceptions so callers can surface a real error message.
     */
    suspend fun getChannels(bouquetRef: String): List<Service> = withContext(Dispatchers.IO) {
        ApiClient.service.getServices(bouquetRef).services ?: emptyList()
    }
    // -------------------------------------------------------------------------
    // Recordings
    // -------------------------------------------------------------------------

    /**
     * Fetches all recordings from the receiver's default recording location.
     * Returns an empty list on error.
     */
    suspend fun getRecordings(): List<Recording> = withContext(Dispatchers.IO) {
        ApiClient.service.getMovieList().movies ?: emptyList()
    }

    /**
     * Fetches video files from a specific directory on the receiver (e.g. /media/hdd/video).
     * Uses the same movielist API with a dirname filter.
     * Returns an empty list on error.
     */
    suspend fun getVideoFiles(dirname: String): List<Recording> = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.getMovieList(dirname).movies ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "getVideoFiles failed", e)
            emptyList()
        }
    }
    // -------------------------------------------------------------------------
    // EPG
    // -------------------------------------------------------------------------

    /**
     * Returns the full EPG schedule for a single service.
     */
    suspend fun getEpgForService(serviceRef: String): List<EpgEvent> = withContext(Dispatchers.IO) {
        ApiClient.service.getEpgForService(serviceRef).events ?: emptyList()
    }

    /**
     * Returns EPG events for all services in a bouquet (multi-service EPG).
     */
    suspend fun getMultiEpg(bouquetRef: String): List<EpgEvent> = withContext(Dispatchers.IO) {
        ApiClient.service.getMultiEpg(bouquetRef).events ?: emptyList()
    }

    /**
     * Returns now/next event pairs for each service in a bouquet.
     * Fetches api/epgnow and api/epgnext in parallel then merges by service ref.
     */
    suspend fun getNowNext(bouquetRef: String): List<NowNextEvent> = withContext(Dispatchers.IO) {
        try {
            val nowEvents = ApiClient.service.getEpgNow(bouquetRef).events ?: emptyList()
            val nextEvents = ApiClient.service.getEpgNext(bouquetRef).events ?: emptyList()
            val nowMap = nowEvents.associateBy { it.serviceRef }
            val nextMap = nextEvents.associateBy { it.serviceRef }
            val allRefs = (nowEvents.map { it.serviceRef } + nextEvents.map { it.serviceRef }).distinct()
            allRefs.map { ref ->
                NowNextEvent(
                    nowEvent = nowMap[ref],
                    nextEvent = nextMap[ref],
                    serviceRef = ref,
                    serviceName = nowMap[ref]?.serviceName ?: nextMap[ref]?.serviceName ?: ""
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Timers
    // -------------------------------------------------------------------------

    /**
     * Adds a recording timer on the receiver for the given EPG event.
     * Returns a [TimerResponse] with [TimerResponse.result] == true on success.
     */
    suspend fun addTimer(event: EpgEvent): TimerResponse = withContext(Dispatchers.IO) {
        ApiClient.service.addTimer(
            sRef = event.serviceRef,
            begin = event.beginTimestamp,
            end = event.endTimestamp,
            name = event.title,
            eit = event.id
        )
    }

    /**
     * Returns all timers currently scheduled on the receiver.
     */
    suspend fun getTimers(): List<Timer> = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.getTimerList().timers ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "getTimers failed", e)
            emptyList()
        }
    }

    /**
     * Deletes a timer from the receiver.
     */
    suspend fun deleteTimer(timer: Timer): TimerDeleteResponse = withContext(Dispatchers.IO) {
        ApiClient.service.deleteTimer(
            sRef = timer.serviceRef,
            begin = timer.beginTimestamp,
            end = timer.endTimestamp
        )
    }

    /**
     * Toggles a timer's enabled/disabled state on the receiver.
     */
    suspend fun toggleTimer(timer: Timer): TimerDeleteResponse = withContext(Dispatchers.IO) {
        ApiClient.service.toggleTimerStatus(
            sRef = timer.serviceRef,
            begin = timer.beginTimestamp,
            end = timer.endTimestamp
        )
    }

    /**
     * Deletes a recording on the receiver.
     */
    suspend fun deleteRecording(recording: Recording): TimerDeleteResponse = withContext(Dispatchers.IO) {
        val sRef = recording.serviceRef ?: return@withContext TimerDeleteResponse(false, "missing service reference")
        ApiClient.service.deleteMovie(sRef)
    }

    /**
     * Searches EPG across all services for the given query string.
     */
    suspend fun searchEpg(query: String): List<EpgEvent> = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.searchEpg(query).events ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "searchEpg failed", e)
            emptyList()
        }
    }

    /**
     * Fetches a raw screenshot JPEG from the receiver.
     */
    suspend fun getScreenshot(): ResponseBody? = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.getScreenshot()
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "getScreenshot failed", e)
            null
        }
    }

    /**
     * Zaps (tunes) the receiver to the given service reference.
     */
    suspend fun zap(serviceRef: String) = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.zapToService(serviceRef)
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "zap failed", e)
        }
    }

    // -------------------------------------------------------------------------
    // AutoTimer plugin
    // -------------------------------------------------------------------------

    /**
     * Returns all AutoTimer rules. Empty list when the plugin is not installed
     * or the request fails — caller can detect "no rules" but not "plugin missing"
     * (HTTP 404 also lands here).
     */
    suspend fun getAutoTimers(): List<com.enigma2.firetv.data.model.AutoTimer> = withContext(Dispatchers.IO) {
        try {
            ApiClient.service.getAutoTimers().byteStream().use {
                com.enigma2.firetv.data.api.AutoTimerXml.parseList(it)
            }
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "getAutoTimers failed", e)
            emptyList()
        }
    }

    /**
     * Adds (when [com.enigma2.firetv.data.model.AutoTimer.id] is -1) or updates
     * an AutoTimer rule. Returns the parsed `<e2simplexmlresult>`.
     */
    suspend fun saveAutoTimer(rule: com.enigma2.firetv.data.model.AutoTimer): com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult =
        withContext(Dispatchers.IO) {
            ApiClient.service.editAutoTimer(
                id = rule.id,
                name = rule.name,
                match = rule.match,
                enabled = if (rule.enabled) "yes" else "no",
                searchType = rule.searchType,
                searchCase = rule.searchCase,
                from = rule.after,
                to = rule.before,
                services = rule.services.takeIf { it.isNotEmpty() }
            ).byteStream().use { com.enigma2.firetv.data.api.AutoTimerXml.parseSimpleResult(it) }
        }

    /**
     * Removes an AutoTimer rule by id.
     */
    suspend fun removeAutoTimer(id: Int): com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult =
        withContext(Dispatchers.IO) {
            ApiClient.service.removeAutoTimer(id).byteStream().use {
                com.enigma2.firetv.data.api.AutoTimerXml.parseSimpleResult(it)
            }
        }

    /**
     * Asks the receiver to scan the EPG and schedule timers for any matching events.
     */
    suspend fun parseAutoTimers(): com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult =
        withContext(Dispatchers.IO) {
            ApiClient.service.parseAutoTimers().byteStream().use {
                com.enigma2.firetv.data.api.AutoTimerXml.parseSimpleResult(it)
            }
        }

    // -------------------------------------------------------------------------
    // Box / About
    // -------------------------------------------------------------------------

    /**
     * Returns the receiver's `info` map (brand, model, image version, kernel, tuners,
     * HDDs, network interfaces…). Returns `null` on any error so callers can show a
     * friendly empty-state message.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun getBoxInfo(): Map<String, Any?>? = withContext(Dispatchers.IO) {
        try {
            val root = ApiClient.service.getAbout()
            (root["info"] as? Map<String, Any?>) ?: root
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "getBoxInfo failed", e)
            null
        }
    }
}
