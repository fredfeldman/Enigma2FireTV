package com.enigma2.firetv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.enigma2.firetv.data.model.Recording
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.util.ApiErrors
import kotlinx.coroutines.launch

enum class SortOrder { DATE_DESC, DATE_ASC, NAME, CHANNEL }

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = Enigma2Repository()
    private val prefs = ReceiverPreferences(app)

    private var rawRecordings: List<Recording> = emptyList()
    private var currentSort: SortOrder = runCatching {
        SortOrder.valueOf(prefs.recordingsSortOrder)
    }.getOrDefault(SortOrder.DATE_DESC)

    private val _recordings = MutableLiveData<List<Recording>>(emptyList())
    val recordings: LiveData<List<Recording>> = _recordings

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** The recording currently highlighted in the list (D-pad focus). */
    private val _focusedRecording = MutableLiveData<Recording?>()
    val focusedRecording: LiveData<Recording?> = _focusedRecording

    private val _selectionMode = MutableLiveData(false)
    val selectionMode: LiveData<Boolean> = _selectionMode

    private val _selectedFilenames = MutableLiveData<Set<String>>(emptySet())
    val selectedFilenames: LiveData<Set<String>> = _selectedFilenames

    fun loadRecordings() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                rawRecordings = repository.getRecordings()
                applySort()
            } catch (e: Exception) {
                _error.value = getApplication<Application>().getString(
                    com.enigma2.firetv.R.string.vm_error_recordings,
                    ApiErrors.userMessage(getApplication(), e)
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sortBy(order: SortOrder) {
        currentSort = order
        prefs.recordingsSortOrder = order.name
        applySort()
    }

    /** Currently active sort order (for UI to highlight the active option). */
    val sortOrder: SortOrder get() = currentSort

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

    /**
     * Deletes a recording on the receiver. [onResult] is invoked on the main thread
     * with `(success, message)` after the request completes.
     */
    fun deleteRecording(recording: Recording, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = repository.deleteRecording(recording)
                if (response.result) {
                    rawRecordings = rawRecordings.filter { it.filename != recording.filename }
                    applySort()
                    if (_focusedRecording.value?.filename == recording.filename) {
                        _focusedRecording.value = null
                    }
                }
                onResult(response.result, response.message)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    // ---- Multi-select ----

    fun enterSelectionMode(initial: Recording? = null) {
        _selectionMode.value = true
        _selectedFilenames.value = initial?.filename?.let { setOf(it) } ?: emptySet()
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedFilenames.value = emptySet()
    }

    fun toggleSelection(recording: Recording) {
        val name = recording.filename ?: return
        val current = _selectedFilenames.value ?: emptySet()
        _selectedFilenames.value =
            if (current.contains(name)) current - name else current + name
    }

    fun selectAll() {
        _selectedFilenames.value = rawRecordings.mapNotNull { it.filename }.toSet()
    }

    /**
     * Deletes every currently selected recording sequentially.
     * [onComplete] is invoked with (deletedCount, totalRequested).
     */
    fun deleteSelected(onComplete: (Int, Int) -> Unit) {
        val targets = (_selectedFilenames.value ?: emptySet())
        if (targets.isEmpty()) { onComplete(0, 0); return }
        val toDelete = rawRecordings.filter { it.filename in targets }
        viewModelScope.launch {
            var ok = 0
            for (rec in toDelete) {
                try {
                    if (repository.deleteRecording(rec).result) ok++
                } catch (_: Exception) { /* counted as failure */ }
            }
            // Refresh from server to get an authoritative list
            rawRecordings = try { repository.getRecordings() } catch (_: Exception) { rawRecordings }
            applySort()
            _selectionMode.value = false
            _selectedFilenames.value = emptySet()
            onComplete(ok, toDelete.size)
        }
    }
}
