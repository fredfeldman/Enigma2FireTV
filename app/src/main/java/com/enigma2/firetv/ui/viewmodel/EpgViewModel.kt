package com.enigma2.firetv.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch

class EpgViewModel : ViewModel() {

    private val repository = Enigma2Repository()

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

    fun loadMultiEpg(bouquetRef: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val events = repository.getMultiEpg(bouquetRef)
                // Group by service reference
                _epgMap.value = events.groupBy { it.serviceRef }
            } catch (e: Exception) {
                _error.value = "Failed to load EPG: ${e.message}"
            } finally {
                _isLoading.value = false
            }
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
                _error.value = "Failed to load EPG: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getEventsForService(serviceRef: String): List<EpgEvent> {
        return _epgMap.value?.get(serviceRef) ?: emptyList()
    }
}
