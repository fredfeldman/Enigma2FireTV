package com.enigma2.firetv.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.model.NowNextEvent
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch

class ChannelViewModel : ViewModel() {

    private val repository = Enigma2Repository()

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
                _error.value = "Failed to load channels: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectBouquet(bouquet: Bouquet) {
        _selectedBouquet.value = bouquet
        loadChannels(bouquet.ref)
        loadNowNext(bouquet.ref)
    }

    private fun loadChannels(bouquetRef: String) {
        viewModelScope.launch {
            val services = repository.getChannels(bouquetRef)
            _channels.value = services
        }
    }

    fun loadNowNext(bouquetRef: String) {
        viewModelScope.launch {
            val nn = repository.getNowNext(bouquetRef)
            _nowNext.value = nn
        }
    }

    fun clearError() {
        _error.value = null
    }
}
