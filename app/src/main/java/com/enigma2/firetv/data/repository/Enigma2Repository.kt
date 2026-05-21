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
    // Receiver Settings (v1.1.0 port) — see SettingsXml for parsers.
    //
    // All helpers return null/empty on transport failure so callers can
    // disable the corresponding sub-screen on 404.
    // ------------------------------------------------------------------

    suspend fun probeCapabilities(): com.enigma2.firetv.data.model.settings.ReceiverCapabilities =
        withContext(Dispatchers.IO) {
            suspend fun ok(call: suspend () -> retrofit2.Response<okhttp3.ResponseBody>): Boolean = try {
                call().isSuccessful
            } catch (_: Exception) { false }
            com.enigma2.firetv.data.model.settings.ReceiverCapabilities(
                hasParental    = ok { ApiClient.service.getProtectionSettings() },
                hasTranscoding = ok { ApiClient.service.getTranscodingConfig() },
                hasConfigTree  = ok { ApiClient.service.getConfigSections() },
                hasWol         = ok { ApiClient.service.getWolSetup() }
            )
        }

    suspend fun getStatusInfo(): com.enigma2.firetv.data.model.settings.StatusInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.getStatusInfo().body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parseStatusInfo(it) }
            }.getOrNull()
        }

    suspend fun getPowerState(): com.enigma2.firetv.data.model.settings.PowerState? =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.getPowerState().body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parsePowerState(it) }
            }.getOrNull()
        }

    suspend fun setPowerState(newState: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.setPowerState(newState).isSuccessful }.getOrDefault(false)
    }

    suspend fun getSleepTimer(): com.enigma2.firetv.data.model.settings.SleepTimer? =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.getSleepTimer().body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parseSleepTimer(it) }
            }.getOrNull()
        }

    suspend fun setSleepTimer(minutes: Int, action: String, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.setSleepTimer(
                    time = minutes,
                    action = action,
                    enabled = if (enabled) "True" else "False"
                ).isSuccessful
            }.getOrDefault(false)
        }

    suspend fun getVolume(): com.enigma2.firetv.data.model.settings.VolumeInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.getVolume().body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parseVolume(it) }
            }.getOrNull()
        }

    suspend fun setVolume(level: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.setVolume("set${level.coerceIn(0, 100)}").isSuccessful }
            .getOrDefault(false)
    }

    suspend fun toggleMute(): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.setVolume("mute").isSuccessful }.getOrDefault(false)
    }

    suspend fun getAllSettings(): Map<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            ApiClient.service.getAllSettings().body()?.byteStream()
                ?.use { com.enigma2.firetv.data.api.SettingsXml.parseAllSettings(it) }
                ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    suspend fun getConfigSections(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            ApiClient.service.getConfigSections().body()?.byteStream()
                ?.use { com.enigma2.firetv.data.api.SettingsXml.parseConfigSections(it) }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun getConfigSection(name: String): com.enigma2.firetv.data.model.settings.ConfigSection =
        withContext(Dispatchers.IO) {
            val base = com.enigma2.firetv.data.api.ApiClient.baseUrl.trimEnd('/')
            val url = "$base/api/config/$name"
            runCatching {
                ApiClient.service.getConfigSection(url).body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parseConfigSection(name, it) }
                    ?: com.enigma2.firetv.data.model.settings.ConfigSection(name, emptyList())
            }.getOrDefault(com.enigma2.firetv.data.model.settings.ConfigSection(name, emptyList()))
        }

    suspend fun saveConfig(key: String, value: String): Pair<Boolean, String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.saveConfig(key, value).body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parseSaveAck(it) }
                    ?: (true to null)
            }.getOrDefault(false to "transport error")
        }

    suspend fun setWebConfig(params: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.setWebConfig(params).isSuccessful }.getOrDefault(false)
    }

    suspend fun getParentControlList(): List<com.enigma2.firetv.data.model.settings.ProtectedService> =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.getParentControlList().body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parseProtectedServices(it) }
                    ?: emptyList()
            }.getOrDefault(emptyList())
        }

    suspend fun getProtectionSettings(): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            ApiClient.service.getProtectionSettings().body()?.byteStream()
                ?.use { com.enigma2.firetv.data.api.SettingsXml.parseProtectionSettings(it) }
                ?: (false to false)
        }.getOrDefault(false to false)
    }

    suspend fun getRecordingLocations(): com.enigma2.firetv.data.model.settings.RecordingLocations? =
        withContext(Dispatchers.IO) {
            runCatching {
                val current = ApiClient.service.getCurrentLocation().body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parseCurrentLocation(it) }
                val list = ApiClient.service.getLocations().body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parseLocations(it) } ?: emptyList()
                com.enigma2.firetv.data.model.settings.RecordingLocations(current, list)
            }.getOrNull()
        }

    suspend fun setCurrentLocation(location: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.setCurrentLocation(location).isSuccessful }.getOrDefault(false)
    }

    suspend fun addLocation(dirname: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.addLocation(dirname).isSuccessful }.getOrDefault(false)
    }

    suspend fun removeLocation(dirname: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.removeLocation(dirname).isSuccessful }.getOrDefault(false)
    }

    suspend fun getTunerSignal(): com.enigma2.firetv.data.model.settings.TunerSignal? =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.getTunerSignal().body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parseTunerSignal(it) }
            }.getOrNull()
        }

    suspend fun getWolSetup(): com.enigma2.firetv.data.model.settings.WolSetup? =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.getWolSetup().body()?.byteStream()
                    ?.use { com.enigma2.firetv.data.api.SettingsXml.parseWolSetup(it) }
            }.getOrNull()
        }

    suspend fun setWolSetup(params: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.setWolSetup(params).isSuccessful }.getOrDefault(false)
    }

    suspend fun getTranscodingConfig(): Map<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            ApiClient.service.getTranscodingConfig().body()?.byteStream()
                ?.use { com.enigma2.firetv.data.api.SettingsXml.parseAllSettings(it) }
                ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    suspend fun setTranscodingConfig(params: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { ApiClient.service.setTranscodingConfig(params).isSuccessful }.getOrDefault(false)
        }

    // ---- v1.0.7: Remote control + messaging ----

    suspend fun sendRemoteCommand(commandCode: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.sendRemoteCommand(commandCode).isSuccessful }
            .getOrDefault(false)
    }

    suspend fun sendMessage(text: String, type: Int = 1, timeoutSeconds: Int = 10): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { ApiClient.service.sendMessage(text, type, timeoutSeconds).isSuccessful }
                .getOrDefault(false)
        }

    // ---- v1.0.8: Recording management ----

    suspend fun renameMovie(sRef: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.renameMovie(sRef, newName).isSuccessful }.getOrDefault(false)
    }

    suspend fun moveMovie(sRef: String, newDir: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.moveMovie(sRef, newDir).isSuccessful }.getOrDefault(false)
    }

    suspend fun updateMovieTags(sRef: String, add: List<String>?, remove: List<String>?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.movieTags(
                    sRef = sRef,
                    add = add?.joinToString(" ")?.takeIf { it.isNotBlank() },
                    del = remove?.joinToString(" ")?.takeIf { it.isNotBlank() }
                ).isSuccessful
            }.getOrDefault(false)
        }

    suspend fun getMovieTags(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = ApiClient.service.getTags().body()?.string().orEmpty()
            val arr = org.json.JSONObject(body).optJSONArray("tags")
                ?: return@runCatching emptyList<String>()
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    // ---- v1.1.0: Parental write / system / plugins / network ----

    suspend fun parentalProtect(sRef: String, add: Boolean, type: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.parentalProtect(
                    sRef = sRef,
                    action = if (add) "add" else "remove",
                    type = type
                ).isSuccessful
            }.getOrDefault(false)
        }

    suspend fun changeSetupPin(newPin: String, oldPin: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.changeSetupPin(newPin, oldPin).isSuccessful }.getOrDefault(false)
    }

    suspend fun getMountInfo(): String? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.getMountInfo().body()?.string() }.getOrNull()
    }

    suspend fun getSmartInfo(): String? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.getSmartInfo().body()?.string() }.getOrNull()
    }

    suspend fun getReceiverLog(): String? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.getReceiverLog().body()?.string() }.getOrNull()
    }

    suspend fun listPlugins(): String? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.listPlugins().body()?.string() }.getOrNull()
    }

    suspend fun installPlugin(pkg: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.installPlugin(pkg).isSuccessful }.getOrDefault(false)
    }

    suspend fun removePlugin(pkg: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.removePlugin(pkg).isSuccessful }.getOrDefault(false)
    }

    suspend fun getNetworkInfo(): String? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.getNetworkInfo().body()?.string() }.getOrNull()
    }

    // ---- v1.1.1: EPG refresh ----

    suspend fun refreshEpgForService(sRef: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.refreshEpgForService(sRef).isSuccessful }.getOrDefault(false)
    }

    suspend fun triggerEpgRefresh(): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.triggerEpgRefresh().isSuccessful }.getOrDefault(false)
    }

    // ---- v1.2.0 Phase 5: IPTV ref detection (constants live in companion object below) ----

    // ---- v1.2.0 Phase 7: EPG Assign (gated by BuildConfig.ENABLE_EPG_ASSIGN) ----

    /** Returns true when the companion epgassign plugin is reachable. */
    suspend fun epgAssignPing(): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.epgAssignPing().isSuccessful }.getOrDefault(false)
    }

    /** Returns raw JSON string from epgassign/sources, or null on failure. */
    suspend fun epgAssignSources(): String? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.epgAssignSources().body()?.string() }.getOrNull()
    }

    /** Returns raw JSON string from epgassign/source?name=…, or null on failure. */
    suspend fun epgAssignSource(name: String): String? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.epgAssignSource(name).body()?.string() }.getOrNull()
    }

    /** Returns raw JSON string from epgassign/mappings, or null on failure. */
    suspend fun epgAssignMappings(): String? = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.epgAssignMappings().body()?.string() }.getOrNull()
    }

    /** Assigns a channel-id+source to a service reference. Returns true on success. */
    suspend fun epgAssign(sRef: String, channelId: String, source: String, name: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service.epgAssign(sRef, channelId, source, name).isSuccessful
            }.getOrDefault(false)
        }

    /** Removes any epgassign mapping for [sRef]. Returns true on success. */
    suspend fun epgAssignUnassign(sRef: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.epgAssignUnassign(sRef).isSuccessful }.getOrDefault(false)
    }

    /** Triggers an EPGImport run. Returns true on success. */
    suspend fun epgAssignImport(): Boolean = withContext(Dispatchers.IO) {
        runCatching { ApiClient.service.epgAssignImport().isSuccessful }.getOrDefault(false)
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

        /** Service-reference type codes that represent IPTV / HTTP streams. */
        val IPTV_REF_TYPES = setOf("4097", "5001", "5002", "5003", "8193")

        /**
         * For an IPTV-type service reference, extracts and URL-decodes the stream URL
         * from the 11th colon-separated segment. Returns null for non-IPTV refs.
         */
        fun extractIptvUrl(sRef: String): String? {
            val parts = sRef.split(":")
            if (parts.size < 11) return null
            if (parts[0] !in IPTV_REF_TYPES) return null
            return try {
                java.net.URLDecoder.decode(parts[10], "UTF-8")
            } catch (_: Exception) { null }
        }

        fun isIptvRef(sRef: String): Boolean = extractIptvUrl(sRef) != null

        /** Derives the Enigma2 mode (0=TV, 1=Radio) from a bouquet service reference. */
        fun bouquetMode(ref: String): Int =
            if (ref.contains(".radio") || ref.startsWith("1:7:2")) MODE_RADIO else MODE_TV
    }
}
