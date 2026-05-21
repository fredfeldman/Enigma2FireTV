package com.enigma2.firetv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.prefs.EpgCacheStore
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.util.ApiErrors
import kotlinx.coroutines.launch

class EpgViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = Enigma2Repository()
    private val cache = EpgCacheStore(app)
    private val prefs = ReceiverPreferences(app)

    /** Full multi-service EPG for the current bouquet, keyed by serviceRef. */
    private val _epgMap = MutableLiveData<Map<String, List<EpgEvent>>>(emptyMap())
    val epgMap: LiveData<Map<String, List<EpgEvent>>> = _epgMap

    /** EPG for a single selected service. */
    private val _serviceEpg = MutableLiveData<List<EpgEvent>>()
    val serviceEpg: LiveData<List<EpgEvent>> = _serviceEpg

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * v1.1.0 cache-fallback signal. When the live fetch fails and we serve
     * the cached snapshot instead, this exposes the cache age (minutes) to
     * the UI so it can render the "Showing cached EPG (N min old)" banner.
     * Value is `-1` when fresh data is on screen.
     */
    private val _cacheBannerAgeMin = MutableLiveData<Long>(-1L)
    val cacheBannerAgeMin: LiveData<Long> = _cacheBannerAgeMin

    fun loadMultiEpg(bouquetRef: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val events = repository.getMultiEpg(bouquetRef)
                if (events.isEmpty()) {
                    // Empty live result — try cache as a recovery aid.
                    val cached = cache.load(prefs.activeDeviceId, bouquetRef)
                    if (!cached.isNullOrEmpty()) {
                        _epgMap.value = cached.groupBy { it.serviceRef }
                        _cacheBannerAgeMin.value =
                            cache.ageMinutes(prefs.activeDeviceId, bouquetRef)
                    } else {
                        _epgMap.value = emptyMap()
                        _cacheBannerAgeMin.value = -1L
                    }
                } else {
                    _epgMap.value = events.groupBy { it.serviceRef }
                    cache.save(prefs.activeDeviceId, bouquetRef, events)
                    _cacheBannerAgeMin.value = -1L
                }
            } catch (e: Exception) {
                val cached = cache.load(prefs.activeDeviceId, bouquetRef)
                if (!cached.isNullOrEmpty()) {
                    _epgMap.value = cached.groupBy { it.serviceRef }
                    _cacheBannerAgeMin.value =
                        cache.ageMinutes(prefs.activeDeviceId, bouquetRef)
                } else {
                    _error.value = getApplication<Application>().getString(
                        R.string.vm_error_epg, ApiErrors.userMessage(getApplication(), e)
                    )
                    _cacheBannerAgeMin.value = -1L
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * v1.1.0 manual EPG refresh: triggers the receiver's EPG scan (no-op if
     * the EPGRefresh plugin is missing) then reloads the bouquet.
     */
    fun refreshEpg(bouquetRef: String) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { repository.triggerEpgRefresh() }
            loadMultiEpg(bouquetRef)
        }
    }

    fun loadEpgForService(serviceRef: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val events = repository.getEpgForService(serviceRef)
                _serviceEpg.value = events.sortedBy { it.beginTimestamp }
            } catch (e: Exception) {
                _error.value = getApplication<Application>().getString(
                    R.string.vm_error_epg, ApiErrors.userMessage(getApplication(), e)
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getEventsForService(serviceRef: String): List<EpgEvent> {
        return _epgMap.value?.get(serviceRef) ?: emptyList()
    }
}
