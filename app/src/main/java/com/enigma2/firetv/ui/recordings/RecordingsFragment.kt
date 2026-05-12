package com.enigma2.firetv.ui.recordings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.PlaylistEntry
import com.enigma2.firetv.data.model.Recording
import com.enigma2.firetv.data.prefs.PlaylistPreferences
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.ui.player.PlayerActivity
import com.enigma2.firetv.ui.playlists.PlaylistManagerFragment
import com.enigma2.firetv.ui.viewmodel.RecordingViewModel
import com.enigma2.firetv.ui.viewmodel.SortOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recordings browser: left panel shows the list, right panel shows EPG-style
 * detail (description, channel, date, duration) for the focused recording.
 * Pressing OK on a recording launches [PlayerActivity] to play it.
 */
class RecordingsFragment : Fragment() {

    private val viewModel: RecordingViewModel by viewModels()
    private lateinit var prefs: ReceiverPreferences
    private lateinit var playlistPrefs: PlaylistPreferences

    // List-panel views
    private lateinit var rvRecordings: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnRefresh: TextView
    private lateinit var btnPlaylists: TextView
    private lateinit var btnSort: TextView

    // Selection-mode toolbar
    private lateinit var toolbarNormal: View
    private lateinit var toolbarSelection: View
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnSelectAll: TextView
    private lateinit var btnDeleteSelected: TextView
    private lateinit var btnCancelSelection: TextView

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { viewModel.exitSelectionMode() }
    }

    // Detail-panel views
    private lateinit var tvDetailHint: TextView
    private lateinit var tvDetailTitle: TextView
    private lateinit var tvDetailChannel: TextView
    private lateinit var tvDetailDatetime: TextView
    private lateinit var detailDivider: View
    private lateinit var svDescription: ScrollView
    private lateinit var tvDetailDescription: TextView
    private lateinit var tvPlayHint: TextView

    private lateinit var adapter: RecordingAdapter
    private val dateFmt = SimpleDateFormat("EEEE, dd MMM yyyy  HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_recordings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = ReceiverPreferences(requireContext())
        playlistPrefs = PlaylistPreferences(requireContext())

        bindViews(view)
        setupList()
        observeViewModel()

        btnRefresh.setOnClickListener { viewModel.loadRecordings() }
        btnSort.setOnClickListener { showSortDialog() }
        btnPlaylists.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_container, PlaylistManagerFragment())
                .addToBackStack(null)
                .commit()
        }

        btnSelectAll.setOnClickListener { viewModel.selectAll() }
        btnCancelSelection.setOnClickListener { viewModel.exitSelectionMode() }
        btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        if (viewModel.recordings.value.isNullOrEmpty()) {
            viewModel.loadRecordings()
        }
    }

    private fun bindViews(view: View) {
        rvRecordings     = view.findViewById(R.id.rv_recordings)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        tvError          = view.findViewById(R.id.tv_error)
        tvEmpty          = view.findViewById(R.id.tv_empty)
        btnRefresh       = view.findViewById(R.id.btn_refresh)
        btnPlaylists     = view.findViewById(R.id.btn_playlists)
        btnSort          = view.findViewById(R.id.btn_sort)

        toolbarNormal       = view.findViewById(R.id.toolbar_normal)
        toolbarSelection    = view.findViewById(R.id.toolbar_selection)
        tvSelectionCount    = view.findViewById(R.id.tv_selection_count)
        btnSelectAll        = view.findViewById(R.id.btn_select_all)
        btnDeleteSelected   = view.findViewById(R.id.btn_delete_selected)
        btnCancelSelection  = view.findViewById(R.id.btn_cancel_selection)

        tvDetailHint        = view.findViewById(R.id.tv_detail_hint)
        tvDetailTitle       = view.findViewById(R.id.tv_detail_title)
        tvDetailChannel     = view.findViewById(R.id.tv_detail_channel)
        tvDetailDatetime    = view.findViewById(R.id.tv_detail_datetime)
        detailDivider       = view.findViewById(R.id.detail_divider)
        svDescription       = view.findViewById(R.id.sv_description)
        tvDetailDescription = view.findViewById(R.id.tv_detail_description)
        tvPlayHint          = view.findViewById(R.id.tv_play_hint)
    }

    private fun setupList() {
        adapter = RecordingAdapter(
            onRecordingClick   = { recording -> playRecording(recording) },
            onRecordingFocused = { recording -> viewModel.onRecordingFocused(recording) },
            onRecordingLongClick = { recording -> showRecordingActionsDialog(recording) },
            onSelectionToggle = { recording -> viewModel.toggleSelection(recording) }
        )
        rvRecordings.layoutManager = LinearLayoutManager(requireContext())
        rvRecordings.adapter = adapter
    }

    private fun showRecordingActionsDialog(recording: Recording) {
        val actions = arrayOf(
            getString(R.string.action_play),
            getString(R.string.action_add_to_playlist),
            getString(R.string.action_select_multiple),
            getString(R.string.action_delete_recording)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.recording_action_title))
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> playRecording(recording)
                    1 -> showAddToPlaylistDialog(recording)
                    2 -> viewModel.enterSelectionMode(recording)
                    3 -> confirmDeleteRecording(recording)
                }
            }
            .show()
    }

    private fun confirmDeleteRecording(recording: Recording) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.recording_delete_title))
            .setMessage(getString(R.string.recording_delete_message, recording.displayTitle))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteRecording(recording) { ok, msg ->
                    if (!isAdded) return@deleteRecording
                    val text = if (ok) getString(R.string.recording_deleted_ok, recording.displayTitle)
                               else getString(R.string.recording_delete_failed, msg ?: "")
                    android.widget.Toast.makeText(requireContext(), text, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                tvError.text = getString(
                    R.string.error_with_retry_hint, error, getString(R.string.error_retry_hint)
                )
                tvError.visibility = View.VISIBLE
                tvError.setOnClickListener { viewModel.loadRecordings() }
            } else {
                tvError.visibility = View.GONE
            }
        }

        viewModel.recordings.observe(viewLifecycleOwner) { recordings ->
            adapter.submitList(recordings)
            tvEmpty.visibility = if (recordings.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.focusedRecording.observe(viewLifecycleOwner) { recording ->
            if (recording == null) showHintState() else showDetailState(recording)
        }

        viewModel.selectionMode.observe(viewLifecycleOwner) { enabled ->
            adapter.setSelectionMode(enabled)
            toolbarNormal.visibility = if (enabled) View.GONE else View.VISIBLE
            toolbarSelection.visibility = if (enabled) View.VISIBLE else View.GONE
            backCallback.isEnabled = enabled
            if (!enabled) updateSelectionUi(emptySet())
        }

        viewModel.selectedFilenames.observe(viewLifecycleOwner) { selected ->
            adapter.setSelectedFilenames(selected)
            updateSelectionUi(selected)
        }
    }

    private fun updateSelectionUi(selected: Set<String>) {
        tvSelectionCount.text = getString(R.string.selection_count, selected.size)
        btnDeleteSelected.isEnabled = selected.isNotEmpty()
        btnDeleteSelected.alpha = if (selected.isEmpty()) 0.4f else 1f
    }

    private fun confirmDeleteSelected() {
        val count = viewModel.selectedFilenames.value?.size ?: 0
        if (count == 0) return
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.recordings_delete_title, count))
            .setMessage(getString(R.string.recordings_delete_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteSelected { ok, total ->
                    if (!isAdded) return@deleteSelected
                    android.widget.Toast.makeText(
                        requireContext(),
                        getString(R.string.recordings_deleted_summary, ok, total),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showHintState() {
        tvDetailHint.visibility     = View.VISIBLE
        tvDetailTitle.visibility    = View.GONE
        tvDetailChannel.visibility  = View.GONE
        tvDetailDatetime.visibility = View.GONE
        detailDivider.visibility    = View.GONE
        svDescription.visibility    = View.GONE
        tvPlayHint.visibility       = View.GONE
    }

    private fun showDetailState(recording: Recording) {
        tvDetailHint.visibility     = View.GONE
        tvDetailTitle.visibility    = View.VISIBLE
        tvDetailChannel.visibility  = View.VISIBLE
        tvDetailDatetime.visibility = View.VISIBLE
        detailDivider.visibility    = View.VISIBLE
        tvPlayHint.visibility       = View.VISIBLE

        tvDetailTitle.text   = recording.displayTitle
        tvDetailChannel.text = recording.channelName.orEmpty()

        val dateStr = if (recording.startTimestamp > 0)
            dateFmt.format(Date(recording.startMs)) else ""
        val dur = recording.formatDuration()
        tvDetailDatetime.text = listOfNotNull(
            dateStr.takeIf { it.isNotBlank() },
            dur.takeIf { it.isNotBlank() }?.let { getString(R.string.recording_duration_label, it) }
        ).joinToString("   \u00b7   ")

        val synopsis = recording.synopsis
        if (synopsis.isNotBlank()) {
            tvDetailDescription.text = synopsis
            svDescription.visibility = View.VISIBLE
            // Scroll back to top whenever the selection changes
            svDescription.scrollTo(0, 0)
        } else {
            svDescription.visibility = View.GONE
        }
    }

    private fun showSortDialog() {
        val opts = arrayOf(
            getString(R.string.sort_date_newest),
            getString(R.string.sort_date_oldest),
            getString(R.string.sort_by_name),
            getString(R.string.sort_by_channel)
        )
        val orders = arrayOf(SortOrder.DATE_DESC, SortOrder.DATE_ASC, SortOrder.NAME, SortOrder.CHANNEL)
        val checked = orders.indexOf(viewModel.sortOrder).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sort_recordings))
            .setSingleChoiceItems(opts, checked) { dialog, which ->
                viewModel.sortBy(orders[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showAddToPlaylistDialog(recording: Recording) {
        val filename = recording.filename ?: return
        val playlists = playlistPrefs.playlists

        if (playlists.isEmpty()) {
            // No playlists yet — offer to create one
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.playlist_add_to))
                .setMessage(getString(R.string.playlist_none_yet))
                .setPositiveButton(getString(R.string.playlist_new_title)) { _, _ ->
                    showCreateAndAddDialog(recording)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        val names = (playlists.map { it.name } + getString(R.string.playlist_new_title)).toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.playlist_add_to))
            .setItems(names) { _, which ->
                if (which < playlists.size) {
                    val entry = PlaylistEntry(
                        filename = filename,
                        title = recording.displayTitle,
                        channel = recording.channelName ?: "",
                        durationSec = recording.durationInSeconds
                    )
                    playlistPrefs.addEntryToPlaylist(playlists[which].id, entry)
                    android.widget.Toast.makeText(
                        requireContext(),
                        getString(R.string.playlist_added, playlists[which].name),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showCreateAndAddDialog(recording)
                }
            }
            .show()
    }

    private fun showCreateAndAddDialog(recording: Recording) {
        val filename = recording.filename ?: return
        val et = EditText(requireContext()).apply {
            hint = getString(R.string.playlist_name_hint)
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.playlist_new_title))
            .setView(et)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotBlank()) {
                    val pl = playlistPrefs.createPlaylist(name)
                    val entry = PlaylistEntry(
                        filename = filename,
                        title = recording.displayTitle,
                        channel = recording.channelName ?: "",
                        durationSec = recording.durationInSeconds
                    )
                    playlistPrefs.addEntryToPlaylist(pl.id, entry)
                    android.widget.Toast.makeText(
                        requireContext(),
                        getString(R.string.playlist_added, name),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun playRecording(recording: Recording) {
        val streamUrl = prefs.recordingStreamUrl(recording.filename ?: return)
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, recording.displayTitle)
            // No EXTRA_SERVICE_REF — tells PlayerActivity to use pre-supplied EPG info
            putExtra(PlayerActivity.EXTRA_DESCRIPTION, recording.synopsis)
            putExtra(PlayerActivity.EXTRA_DURATION_SEC, recording.durationInSeconds)
        }
        startActivity(intent)
    }
}
