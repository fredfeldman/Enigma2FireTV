package com.enigma2.firetv.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enigma2.firetv.data.model.Recording
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch

enum class SortOrder { DATE_DESC, DATE_ASC, NAME, CHANNEL }

class RecordingViewModel : ViewModel() {

    private val repository = Enigma2Repository()

    private var rawRecordings: List<Recording> = emptyList()
    private var currentSort = SortOrder.DATE_DESC

    private val _recordings = MutableLiveData<List<Recording>>(emptyList())
    val recordings: LiveData<List<Recording>> = _recordings

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** The recording currently highlighted in the list (D-pad focus). */
    private val _focusedRecording = MutableLiveData<Recording?>()
    val focusedRecording: LiveData<Recording?> = _focusedRecording

    fun loadRecordings() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                rawRecordings = repository.getRecordings()
                applySort()
            } catch (e: Exception) {
                _error.value = "Failed to load recordings: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sortBy(order: SortOrder) {
        currentSort = order
        applySort()
    }

    private fun applySort() {
        _recordings.value = when (currentSort) {
            SortOrder.DATE_ASC  -> rawRecordings.sortedBy { it.startTimestamp }
            SortOrder.NAME      -> rawRecordings.sortedBy { it.displayTitle.lowercase() }
            SortOrder.CHANNEL   -> rawRecordings.sortedBy { it.channelName?.lowercase() ?: "" }
            SortOrder.DATE_DESC -> rawRecordings.sortedByDescending { it.startTimestamp }
        }
    }

    fun onRecordingFocused(recording: Recording) {
        _focusedRecording.value = recording
    }

    fun clearFocus() {
        _focusedRecording.value = null
    }
}
