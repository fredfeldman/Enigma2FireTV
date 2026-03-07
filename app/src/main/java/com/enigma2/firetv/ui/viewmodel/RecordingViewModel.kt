package com.enigma2.firetv.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enigma2.firetv.data.model.Recording
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch

class RecordingViewModel : ViewModel() {

    private val repository = Enigma2Repository()

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
                val list = repository.getRecordings()
                // Sort newest first
                _recordings.value = list.sortedByDescending { it.startTimestamp }
            } catch (e: Exception) {
                _error.value = "Failed to load recordings: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onRecordingFocused(recording: Recording) {
        _focusedRecording.value = recording
    }

    fun clearFocus() {
        _focusedRecording.value = null
    }
}
