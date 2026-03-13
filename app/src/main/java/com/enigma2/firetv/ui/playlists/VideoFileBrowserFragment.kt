package com.enigma2.firetv.ui.playlists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
 * Browses video files on the receiver. Starts at [ROOT_DIR] and supports
 * navigating into sub-directories. Folder entries (filename ending with "/")
 * are rendered as directories; tapping them navigates in. The Up button
 * navigates to the parent directory.
 */
class VideoFileBrowserFragment : Fragment() {

    private lateinit var playlistPrefs: PlaylistPreferences
    private lateinit var tvTitle: TextView
    private lateinit var tvCurrentPath: TextView
    private lateinit var btnUp: ImageButton
    private lateinit var rvFiles: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var loadingIndicator: ProgressBar

    private var playlistId: String = ""
    private var currentPath: String = ROOT_DIR

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_video_browser, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistId = arguments?.getString(ARG_PLAYLIST_ID) ?: ""
        currentPath = arguments?.getString(ARG_INITIAL_PATH) ?: ROOT_DIR
        playlistPrefs = PlaylistPreferences(requireContext())

        tvTitle = view.findViewById(R.id.tv_video_browser_title)
        tvCurrentPath = view.findViewById(R.id.tv_current_path)
        btnUp = view.findViewById(R.id.btn_up)
        rvFiles = view.findViewById(R.id.rv_video_files)
        tvEmpty = view.findViewById(R.id.tv_video_empty)
        loadingIndicator = view.findViewById(R.id.video_loading)

        val playlistName = playlistPrefs.getPlaylist(playlistId)?.name ?: ""
        tvTitle.text = if (playlistName.isNotBlank())
            getString(R.string.video_browser_title_for, playlistName)
        else
            getString(R.string.video_browser_title)

        rvFiles.layoutManager = LinearLayoutManager(requireContext())

        btnUp.setOnClickListener { navigateUp() }

        loadDirectory(currentPath)
    }

    private fun navigateUp() {
        if (currentPath == ROOT_DIR) return
        val parent = currentPath.trimEnd('/').substringBeforeLast('/')
        loadDirectory(if (parent.isBlank()) "/" else parent)
    }

    private fun loadDirectory(path: String) {
        currentPath = path
        tvCurrentPath.text = path
        btnUp.isEnabled = path != ROOT_DIR
        btnUp.alpha = if (path != ROOT_DIR) 1f else 0.3f

        loadingIndicator.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val all = Enigma2Repository().getVideoFiles(path)
                loadingIndicator.visibility = View.GONE

                // Separate folders (filename ends with "/") from files
                val folders = all.filter { it.filename?.trimEnd('/')?.contains('.') == false &&
                    (it.fileSizeBytes == null || it.fileSizeBytes == 0L) &&
                    it.filename != null }
                val files = all.filter { it !in folders }

                if (all.isEmpty()) {
                    tvEmpty.text = getString(R.string.video_empty)
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    rvFiles.adapter = VideoFileAdapter(folders, files) { recording ->
                        val filename = recording.filename ?: return@VideoFileAdapter
                        // If the filename ends with "/" it's a folder — navigate into it
                        if (filename.endsWith("/") || (recording.fileSizeBytes == null || recording.fileSizeBytes == 0L)
                            && !filename.contains('.')) {
                            val dir = filename.trimEnd('/')
                            loadDirectory(dir)
                        } else {
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
        private const val ARG_INITIAL_PATH = "initialPath"
        const val ROOT_DIR = "/media/hdd"

        fun newInstance(playlistId: String) = VideoFileBrowserFragment().apply {
            arguments = Bundle().apply { putString(ARG_PLAYLIST_ID, playlistId) }
        }
    }

    // ── Inline adapter ────────────────────────────────────────────────────────

    private inner class VideoFileAdapter(
        private val folders: List<Recording>,
        private val files: List<Recording>,
        private val onTap: (Recording) -> Unit
    ) : RecyclerView.Adapter<VideoFileAdapter.VH>() {

        private val allItems = folders + files

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_video_title)
            val tvInfo: TextView = view.findViewById(R.id.tv_video_info)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video_file, parent, false))

        override fun getItemCount() = allItems.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val rec = allItems[position]
            val isFolder = rec in folders
            holder.tvTitle.text = if (isFolder) "\uD83D\uDCC1 ${rec.displayTitle}" else rec.displayTitle
            val fileStub = rec.filename?.substringAfterLast('/') ?: ""
            val duration = rec.formatDuration()
            holder.tvInfo.text = when {
                isFolder -> getString(R.string.video_folder_tap)
                duration.isNotBlank() -> "$fileStub  ·  $duration"
                else -> fileStub
            }
            holder.itemView.setOnClickListener { onTap(rec) }
        }
    }
}
