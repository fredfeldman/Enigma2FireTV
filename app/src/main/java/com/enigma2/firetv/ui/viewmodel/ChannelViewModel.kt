package com.enigma2.firetv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.model.IptvChannel
import com.enigma2.firetv.data.model.IptvSource
import com.enigma2.firetv.data.model.NowNextEvent
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.prefs.IptvPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.data.repository.IptvRepository
import com.enigma2.firetv.util.ApiErrors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val FAVORITES_REF = "__favorites__"
        /** Prefix for synthetic IPTV bouquet refs: "iptv:${sourceId}:${group}" */
        const val IPTV_BOUQUET_PREFIX = "iptv:"
        /** Prefix for IPTV channel service refs: "iptv_ch:${tvgIdOrUrl}" */
        const val IPTV_CH_PREFIX = "iptv_ch:"
    }

    private val repository = Enigma2Repository(app)

    /** In-memory lookup: IPTV service ref → IptvChannel (for playback URL). */
    val iptvChannelMap = mutableMapOf<String, IptvChannel>()

    // ---- Bouquets ----
    private val _bouquets = MutableLiveData<List<Bouquet>>()
    val bouquets: LiveData<List<Bouquet>> = _bouquets

    // ---- Channels in selected bouquet ----
    private val _channels = MutableLiveData<List<Service>>()
    val channels: LiveData<List<Service>> = _channels

    // ---- Selected bouquet ref ----
    private val _selectedBouquet = MutableLiveData<Bouquet?>()
    val selectedBouquet: LiveData<Bouquet?> = _selectedBouquet

    // ---- Now/Next data ----
    private val _nowNext = MutableLiveData<List<NowNextEvent>>()
    val nowNext: LiveData<List<NowNextEvent>> = _nowNext

    // ---- Recording-in-progress indicators ----
    private val _recordingServiceRefs = MutableLiveData<Set<String>>(emptySet())
    val recordingServiceRefs: LiveData<Set<String>> = _recordingServiceRefs

    // ---- Loading / error ----
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadBouquets() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            var enigmaBouquets: List<Bouquet> = emptyList()
            try {
                enigmaBouquets = repository.getBouquets()
            } catch (e: Exception) {
                _error.value = getApplication<Application>().getString(
                    R.string.vm_error_channels, ApiErrors.userMessage(getApplication(), e)
                )
            }
            val iptvBouquets = withContext(Dispatchers.IO) { buildIptvBouquets() }
            val allBouquets = enigmaBouquets + iptvBouquets
            _bouquets.value = allBouquets
            // Auto-select first bouquet
            if (allBouquets.isNotEmpty() && _selectedBouquet.value == null) {
                selectBouquet(allBouquets[0])
            }
            _isLoading.value = false
        }
    }

    /** Builds synthetic Bouquet entries for all cached IPTV sources. */
    private fun buildIptvBouquets(): List<Bouquet> {
        val iptvPrefs = IptvPreferences(getApplication())
        val iptvRepo = IptvRepository(getApplication())
        val result = mutableListOf<Bouquet>()
        for (source in iptvPrefs.getSources()) {
            val channels = iptvRepo.loadCachedChannels(source.id) ?: continue
            if (channels.isEmpty()) continue
            val groups = channels.map { it.group.ifBlank { "Uncategorized" } }.distinct()
            // "All Channels" entry for the source
            result.add(Bouquet(
                ref = "iptv:${source.id}:__all__",
                name = source.name,
                channels = null
            ))
            // One entry per group
            for (group in groups) {
                result.add(Bouquet(
                    ref = "iptv:${source.id}:$group",
                    name = "  $group",
                    channels = null
                ))
            }
        }
        return result
    }

    fun selectBouquet(bouquet: Bouquet) {
        _selectedBouquet.value = bouquet
        when {
            bouquet.ref.startsWith(IPTV_BOUQUET_PREFIX) -> loadIptvChannels(bouquet.ref)
            bouquet.ref != FAVORITES_REF -> {
                loadChannels(bouquet.ref)
                loadNowNext(bouquet.ref)
                loadRecordingTimers()
            }
        }
        // For FAVORITES_REF: ChannelsFragment observes selectedBouquet and calls showFavoriteChannels()
    }

    private fun loadChannels(bouquetRef: String) {
        viewModelScope.launch {
            try {
                _channels.value = repository.getChannels(bouquetRef)
            } catch (e: Exception) {
                _error.value = getApplication<Application>().getString(
                    R.string.vm_error_channels, ApiErrors.userMessage(getApplication(), e)
                )
                _channels.value = emptyList()
            }
        }
    }

    /** Called by ChannelsFragment to populate the favorites bouquet channel list. */
    fun showFavoriteChannels(services: List<Service>) {
        _channels.value = services
    }

    /** Loads channels for an IPTV bouquet from disk cache and sets _channels + _nowNext. */
    private fun loadIptvChannels(bouquetRef: String) {
        viewModelScope.launch {
            _channels.value = emptyList()
            _nowNext.value = emptyList()
            // Parse ref: "iptv:${sourceId}:${groupOrAll}"
            val afterPrefix = bouquetRef.removePrefix(IPTV_BOUQUET_PREFIX)
            val colonIdx = afterPrefix.indexOf(':')
            if (colonIdx < 0) return@launch
            val sourceId = afterPrefix.substring(0, colonIdx)
            val groupName = afterPrefix.substring(colonIdx + 1)

            val iptvRepo = IptvRepository(getApplication())
            val allChannels = withContext(Dispatchers.IO) {
                iptvRepo.loadCachedChannels(sourceId)
            } ?: return@launch

            val filtered = if (groupName == "__all__") allChannels
                           else allChannels.filter {
                               it.group.ifBlank { "Uncategorized" } == groupName
                           }

            // Build the in-memory ref → IptvChannel map
            val services = filtered.map { ch ->
                val ref = IPTV_CH_PREFIX + ch.tvgId.ifBlank { ch.streamUrl }
                iptvChannelMap[ref] = ch
                Service(ref = ref, name = ch.name, piconPath = ch.logoUrl.ifBlank { null })
            }
            _channels.value = services

            // Load EPG from disk cache (best-effort)
            val cachedEpg = withContext(Dispatchers.IO) {
                iptvRepo.loadCachedEpg(sourceId)
            }
            if (cachedEpg != null) {
                _nowNext.value = buildIptvNowNextEvents(cachedEpg)
            }
        }
    }

    /** Converts a cached IPTV EPG map into NowNextEvent list for the channel adapter. */
    private fun buildIptvNowNextEvents(
        epg: Map<String, List<com.enigma2.firetv.data.model.IptvEpgEvent>>
    ): List<NowNextEvent> {
        val now = System.currentTimeMillis()
        return epg.entries.mapNotNull { (tvgId, events) ->
            val nowEvt = events.firstOrNull { it.startMs <= now && it.endMs > now }
            val nextEvt = events.firstOrNull { it.startMs > now }
            if (nowEvt == null && nextEvt == null) return@mapNotNull null
            val serviceRef = IPTV_CH_PREFIX + tvgId
            NowNextEvent(
                nowEvent = nowEvt?.let {
                    EpgEvent(
                        id = 0,
                        serviceRef = serviceRef,
                        serviceName = "",
                        title = it.title,
                        shortDesc = it.description,
                        longDesc = null,
                        beginTimestamp = it.startMs / 1000,
                        durationSeconds = ((it.endMs - it.startMs) / 1000).toInt()
                    )
                },
                nextEvent = nextEvt?.let {
                    EpgEvent(
                        id = 0,
                        serviceRef = serviceRef,
                        serviceName = "",
                        title = it.title,
                        shortDesc = it.description,
                        longDesc = null,
                        beginTimestamp = it.startMs / 1000,
                        durationSeconds = ((it.endMs - it.startMs) / 1000).toInt()
                    )
                },
                serviceRef = serviceRef,
                serviceName = ""
            )
        }
    }

    /**
     * Fetches channels for a newly added M3U source, caches them, then rebuilds
     * the bouquet list to include the new source's groups.
     */
    fun refreshIptvSource(source: IptvSource) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val iptvRepo = IptvRepository(getApplication())
                val channels = withContext(Dispatchers.IO) {
                    iptvRepo.fetchChannels(source.m3uUrl)
                }
                withContext(Dispatchers.IO) {
                    iptvRepo.saveCachedChannels(source.id, channels)
                }
                // Rebuild full bouquet list preserving Enigma2 bouquets
                val currentBouquets = _bouquets.value ?: emptyList()
                val enigmaBouquets = currentBouquets.filter { !it.ref.startsWith(IPTV_BOUQUET_PREFIX) }
                val allIptvBouquets = withContext(Dispatchers.IO) { buildIptvBouquets() }
                _bouquets.value = enigmaBouquets + allIptvBouquets
            } catch (e: Exception) {
                _error.value = "Failed to load M3U: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadNowNext(bouquetRef: String) {
        viewModelScope.launch {
            try {
                _nowNext.value = repository.getNowNext(bouquetRef)
            } catch (_: Exception) {
                // Now/next is best-effort overlay; keep prior value silently.
            }
        }
    }

    fun loadRecordingTimers() {
        viewModelScope.launch {
            try {
                val timers = repository.getTimers()
                _recordingServiceRefs.value = timers
                    .filter { it.state == 2 && it.justPlay == 0 }
                    .map { it.serviceRef }
                    .toSet()
            } catch (_: Exception) {}
        }
    }

    fun clearError() {
        _error.value = null
    }

    /** Call before switching to a different device so stale data is not shown. */
    fun resetForNewDevice() {
        clearChannelCache()
    }

    /**
     * Clears all in-memory channel/bouquet state. Use after a server-side
     * bouquet edit so the next [loadBouquets] call refreshes from the box.
     */
    fun clearChannelCache() {
        _bouquets.value = emptyList()
        _channels.value = emptyList()
        _selectedBouquet.value = null
        _nowNext.value = emptyList()
        _error.value = null
        _channelsDirty.value = false
        iptvChannelMap.clear()
    }

    // ---- Channels-dirty flag ----
    // Set when something outside the main screen edited the bouquet/channel
    // list on the receiver. ChannelsFragment observes this in onResume and
    // forces a full reload when true.
    private val _channelsDirty = MutableLiveData(false)
    val channelsDirty: LiveData<Boolean> = _channelsDirty

    /** Mark the channel/bouquet data stale so the main screen reloads on resume. */
    fun markChannelsDirty() {
        _channelsDirty.value = true
    }

    /** Called by the main screen after it has acted on the dirty flag. */
    fun consumeChannelsDirty() {
        _channelsDirty.value = false
    }
}
