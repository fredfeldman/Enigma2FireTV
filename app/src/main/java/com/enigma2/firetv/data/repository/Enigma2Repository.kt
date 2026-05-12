package com.enigma2.firetv.data.repository

import android.content.Context
import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.model.NowNextEvent
import com.enigma2.firetv.data.model.Recording
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.model.Timer
import com.enigma2.firetv.data.model.TimerDeleteResponse
import com.enigma2.firetv.data.model.TimerResponse
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody

/**
 * Single source of truth for all Enigma2 data.
 * All network calls are dispatched on [Dispatchers.IO].
 *
 * When constructed with a [Context], local bouquet overrides stored in
 * [ReceiverPreferences] are automatically applied to [getChannels] results
 * (filter removed services, then sort to match the stored order with unknown
 * refs appended). Pass `null` (the default) to bypass the override layer —
 * useful inside the bouquet editor itself.
 */
class Enigma2Repository(private val context: Context? = null) {

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
     *
     * Applies the local [ReceiverPreferences.BouquetOverride] (if any) by
     * filtering out removed refs and sorting to match the stored order;
     * unknown refs (e.g. newly added on the receiver) keep their server order
     * and are appended after the user-ordered ones.
     */
    suspend fun getChannels(bouquetRef: String): List<Service> = withContext(Dispatchers.IO) {
        val raw = ApiClient.service.getServices(bouquetRef).services ?: emptyList()
        val ctx = context ?: return@withContext raw
        val override = ReceiverPreferences(ctx).getBouquetOverride(bouquetRef)
            ?: return@withContext raw
        applyOverride(raw, override)
    }

    /**
     * Pure helper exposed for unit tests: returns [services] with [override]
     * applied (removed refs filtered, then ordered per [override.order];
     * unknown refs appended at the end in their original order).
     */
    fun applyOverride(
        services: List<Service>,
        override: ReceiverPreferences.BouquetOverride
    ): List<Service> {
        if (override.isEmpty()) return services
        val removed = override.removed.toSet()
        val filtered = services.filter { it.ref !in removed }
        if (override.order.isEmpty()) return filtered
        val byRef = filtered.associateBy { it.ref }
        val ordered = mutableListOf<Service>()
        val taken = mutableSetOf<String>()
        for (ref in override.order) {
            byRef[ref]?.let {
                ordered.add(it)
                taken.add(ref)
            }
        }
        for (s in filtered) if (s.ref !in taken) ordered.add(s)
        return ordered
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

    // -------------------------------------------------------------------------
    // BouquetEditor plugin
    // -------------------------------------------------------------------------

    /** Distinguishes "plugin available, can mutate on the box" from "no plugin, local-only fallback". */
    enum class BouquetEditorCapability { Available, Missing }

    /**
     * Probes the BouquetEditor plugin by calling `bouqueteditor/api/getservices`
     * and checking that the response is a parseable services list. Returns
     * [BouquetEditorCapability.Missing] for HTTP 404 / non-JSON / network errors.
     */
    suspend fun probeBouquetEditor(): BouquetEditorCapability = withContext(Dispatchers.IO) {
        try {
            // The probe is "did the call succeed and return a usable shape?"
            // An empty list is still treated as Available — plugin responded.
            ApiClient.service.getBouquetEditorBouquets()
            BouquetEditorCapability.Available
        } catch (e: Exception) {
            android.util.Log.i("Enigma2Repo", "BouquetEditor plugin not available: ${e.message}")
            BouquetEditorCapability.Missing
        }
    }

    /**
     * Returns the list of editable user bouquets (TV + Radio merged).
     * Channels are not populated — callers re-fetch them via the standard
     * `getservices` flow when needed.
     */
    suspend fun getUserBouquets(): List<Bouquet> = withContext(Dispatchers.IO) {
        val tv = try { ApiClient.service.getBouquetEditorBouquets().bouquets ?: emptyList() }
                 catch (e: Exception) { android.util.Log.e("Enigma2Repo", "getUserBouquets(tv) failed", e); emptyList() }
        val radio = try { ApiClient.service.getBouquetEditorBouquets(BOUQUETS_RADIO_ROOT).bouquets ?: emptyList() }
                 catch (_: Exception) { emptyList() }
        tv + radio
    }

    suspend fun addBouquet(name: String, mode: Int = MODE_TV): com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult =
        withContext(Dispatchers.IO) {
            ApiClient.service.addBouquet(name, mode).byteStream().use {
                com.enigma2.firetv.data.api.BouquetEditorXml.parseSimpleResult(it)
            }
        }

    suspend fun renameBouquet(bouquetRef: String, newName: String): com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult =
        withContext(Dispatchers.IO) {
            // BouquetEditor.renameservice() uses setListName when the ref points at a bouquet.
            ApiClient.service.renameBouquet(bouquetRef, newName, bouquetMode(bouquetRef)).byteStream().use {
                com.enigma2.firetv.data.api.BouquetEditorXml.parseSimpleResult(it)
            }
        }

    suspend fun removeBouquet(bouquetRef: String): com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult =
        withContext(Dispatchers.IO) {
            ApiClient.service.removeBouquet(bouquetRef, bouquetMode(bouquetRef)).byteStream().use {
                com.enigma2.firetv.data.api.BouquetEditorXml.parseSimpleResult(it)
            }
        }

    suspend fun addServiceToBouquet(
        bouquetRef: String, service: Service
    ): com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult = withContext(Dispatchers.IO) {
        ApiClient.service.addServiceToBouquet(bouquetRef, service.ref, service.name).byteStream().use {
            com.enigma2.firetv.data.api.BouquetEditorXml.parseSimpleResult(it)
        }
    }

    suspend fun removeServiceFromBouquet(
        bouquetRef: String, serviceRef: String
    ): com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult = withContext(Dispatchers.IO) {
        ApiClient.service.removeServiceFromBouquet(bouquetRef, serviceRef).byteStream().use {
            com.enigma2.firetv.data.api.BouquetEditorXml.parseSimpleResult(it)
        }
    }

    suspend fun moveServiceInBouquet(
        bouquetRef: String, serviceRef: String, position: Int
    ): com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult = withContext(Dispatchers.IO) {
        ApiClient.service.moveServiceInBouquet(bouquetRef, serviceRef, position).byteStream().use {
            com.enigma2.firetv.data.api.BouquetEditorXml.parseSimpleResult(it)
        }
    }

    // ------------------------------------------------------------------
    // EPGImport (read-only viewer)
    //
    // The EPGImport plugin doesn't expose its own HTTP API. We use OpenWebif's
    // generic `/file` controller to enumerate and download the source XML files
    // that ship with the plugin (`/etc/epgimport/*.sources.xml`) and parse them
    // locally.
    // ------------------------------------------------------------------

    /** Lists the `*.sources.xml` files installed on the receiver. */
    suspend fun listEpgImportSourceFiles(): List<String> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.service.listFiles(EPGIMPORT_DIR, "*.sources.xml")
            if (!resp.result) emptyList() else (resp.files ?: emptyList()).sorted()
        } catch (e: Exception) {
            android.util.Log.e("Enigma2Repo", "listEpgImportSourceFiles failed", e)
            emptyList()
        }
    }

    /** Downloads and parses a single `.sources.xml` file. */
    suspend fun getEpgImportSourcesFile(
        path: String
    ): com.enigma2.firetv.data.model.EpgImportSourcesFile = withContext(Dispatchers.IO) {
        ApiClient.service.downloadFile(path).byteStream().use {
            com.enigma2.firetv.data.api.EpgImportXml.parseSourcesFile(
                path = path,
                displayName = path.substringAfterLast('/'),
                stream = it
            )
        }
    }

    companion object {
        /** Enigma2 `MODE_TV`. */
        const val MODE_TV = 0
        /** Enigma2 `MODE_RADIO`. */
        const val MODE_RADIO = 1

        private const val BOUQUETS_RADIO_ROOT =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"bouquets.radio\" ORDER BY bouquet"

        /** Standard install path for EPGImport `*.sources.xml` files. */
        private const val EPGIMPORT_DIR = "/etc/epgimport"

        /** Derives the Enigma2 mode (0=TV, 1=Radio) from a bouquet service reference. */
        fun bouquetMode(ref: String): Int =
            if (ref.contains(".radio") || ref.startsWith("1:7:2")) MODE_RADIO else MODE_TV
    }
}
