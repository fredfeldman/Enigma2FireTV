package com.enigma2.firetv.ui.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.PlaylistEntry
import com.enigma2.firetv.data.model.Recording
import com.enigma2.firetv.data.prefs.PlaylistPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch

/**
 * Browses video files in [VIDEO_DIR] on the receiver and lets the user
 * add them to a playlist by tapping.
 */
class VideoFileBrowserFragment : Fragment() {

    private lateinit var playlistPrefs: PlaylistPreferences
    private lateinit var tvTitle: TextView
    private lateinit var rvFiles: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var loadingIndicator: ProgressBar

    private var playlistId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_video_browser, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistId = arguments?.getString(ARG_PLAYLIST_ID) ?: ""
        playlistPrefs = PlaylistPreferences(requireContext())

        tvTitle = view.findViewById(R.id.tv_video_browser_title)
        rvFiles = view.findViewById(R.id.rv_video_files)
        tvEmpty = view.findViewById(R.id.tv_video_empty)
        loadingIndicator = view.findViewById(R.id.video_loading)

        val playlistName = playlistPrefs.getPlaylist(playlistId)?.name ?: ""
        tvTitle.text = if (playlistName.isNotBlank())
            getString(R.string.video_browser_title_for, playlistName)
        else
            getString(R.string.video_browser_title)

        rvFiles.layoutManager = LinearLayoutManager(requireContext())

        loadVideos()
    }

    private fun loadVideos() {
        loadingIndicator.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val videos = Enigma2Repository().getVideoFiles(VIDEO_DIR)
                loadingIndicator.visibility = View.GONE
                if (videos.isEmpty()) {
                    tvEmpty.text = getString(R.string.video_empty)
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    rvFiles.adapter = VideoFileAdapter(videos) { recording ->
                        val filename = recording.filename ?: return@VideoFileAdapter
                        val entry = PlaylistEntry(
                            filename = filename,
                            title = recording.displayTitle,
                            channel = recording.channelName ?: "",
                            durationSec = recording.durationInSeconds
                        )
                        playlistPrefs.addEntryToPlaylist(playlistId, entry)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.video_added_to_playlist, recording.displayTitle),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                loadingIndicator.visibility = View.GONE
                tvEmpty.text = getString(R.string.video_load_error)
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private const val ARG_PLAYLIST_ID = "playlistId"
        const val VIDEO_DIR = "/media/hdd/video"

        fun newInstance(playlistId: String) = VideoFileBrowserFragment().apply {
            arguments = Bundle().apply { putString(ARG_PLAYLIST_ID, playlistId) }
        }
    }

    // ── Inline adapter ────────────────────────────────────────────────────────

    private inner class VideoFileAdapter(
        private val items: List<Recording>,
        private val onAdd: (Recording) -> Unit
    ) : RecyclerView.Adapter<VideoFileAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_video_title)
            val tvInfo: TextView = view.findViewById(R.id.tv_video_info)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video_file, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val rec = items[position]
            holder.tvTitle.text = rec.displayTitle
            val fileStub = rec.filename?.substringAfterLast('/') ?: ""
            val duration = rec.formatDuration()
            holder.tvInfo.text = if (duration.isNotBlank()) "$fileStub  ·  $duration" else fileStub
            holder.itemView.setOnClickListener { onAdd(rec) }
        }
    }
}
