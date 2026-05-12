package com.enigma2.firetv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.model.NowNextEvent
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.util.ApiErrors
import kotlinx.coroutines.launch

class ChannelViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val FAVORITES_REF = "__favorites__"
    }

    private val repository = Enigma2Repository(app)

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
            try {
                val list = repository.getBouquets()
                _bouquets.value = list
                // Auto-select first bouquet
                if (list.isNotEmpty() && _selectedBouquet.value == null) {
                    selectBouquet(list[0])
                }
            } catch (e: Exception) {
                _error.value = getApplication<Application>().getString(
                    R.string.vm_error_channels, ApiErrors.userMessage(getApplication(), e)
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectBouquet(bouquet: Bouquet) {
        _selectedBouquet.value = bouquet
        if (bouquet.ref != FAVORITES_REF) {
            loadChannels(bouquet.ref)
            loadNowNext(bouquet.ref)
            loadRecordingTimers()
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
