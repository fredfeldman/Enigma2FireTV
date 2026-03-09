package com.enigma2.firetv.ui.playlists

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.prefs.PlaylistPreferences
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.ui.player.PlayerActivity

/**
 * Shows the contents of a single [RecordingPlaylist].
 * The user can:
 *  - Tap an entry to play from that position (playlist continues sequentially).
 *  - Tap ▲/▼ to reorder entries.
 *  - Tap ✕ to remove an entry.
 *  - Tap "Play All" to play from the beginning.
 */
class PlaylistDetailFragment : Fragment() {

    private lateinit var playlistPrefs: PlaylistPreferences
    private lateinit var receiverPrefs: ReceiverPreferences
    private lateinit var adapter: PlaylistDetailAdapter
    private lateinit var rvEntries: RecyclerView
    private lateinit var tvTitle: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnPlayAll: Button
    private lateinit var btnAddVideos: Button

    private var playlistId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_playlist_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistId = arguments?.getString(ARG_PLAYLIST_ID) ?: ""
        playlistPrefs = PlaylistPreferences(requireContext())
        receiverPrefs = ReceiverPreferences(requireContext())

        tvTitle = view.findViewById(R.id.tv_playlist_detail_title)
        rvEntries = view.findViewById(R.id.rv_playlist_entries)
        tvEmpty = view.findViewById(R.id.tv_playlist_detail_empty)
        btnPlayAll = view.findViewById(R.id.btn_play_all)
        btnAddVideos = view.findViewById(R.id.btn_add_videos)

        adapter = PlaylistDetailAdapter(
            onPlay = { index -> playFrom(index) },
            onMoveUp = { index ->
                playlistPrefs.moveEntryUp(playlistId, index)
                refresh()
            },
            onMoveDown = { index ->
                playlistPrefs.moveEntryDown(playlistId, index)
                refresh()
            },
            onRemove = { index ->
                playlistPrefs.removeEntryFromPlaylist(playlistId, index)
                refresh()
            }
        )
        rvEntries.layoutManager = LinearLayoutManager(requireContext())
        rvEntries.adapter = adapter

        btnPlayAll.setOnClickListener { playFrom(0) }
        btnAddVideos.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_container, VideoFileBrowserFragment.newInstance(playlistId))
                .addToBackStack(null)
                .commit()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val pl = playlistPrefs.getPlaylist(playlistId)
        tvTitle.text = pl?.name ?: getString(R.string.playlist)
        val entries = pl?.entries ?: emptyList()
        adapter.setItems(entries)
        tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        btnPlayAll.isEnabled = entries.isNotEmpty()
    }

    private fun playFrom(startIndex: Int) {
        val pl = playlistPrefs.getPlaylist(playlistId) ?: return
        if (pl.entries.isEmpty()) return

        val urls = ArrayList<String>()
        val titles = ArrayList<String>()
        val durations = ArrayList<Int>()

        pl.entries.forEachIndexed { i, entry ->
            if (i >= startIndex) {
                urls.add(receiverPrefs.recordingStreamUrl(entry.filename))
                titles.add(entry.title)
                durations.add(entry.durationSec)
            }
        }

        if (urls.isEmpty()) return

        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, urls[0])
            putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, titles[0])
            putExtra(PlayerActivity.EXTRA_DURATION_SEC, durations[0])
            putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_URLS, urls)
            putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_TITLES, titles)
            putIntegerArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_DURATIONS, ArrayList(durations))
            putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, 0)
        }
        startActivity(intent)
    }

    companion object {
        private const val ARG_PLAYLIST_ID = "playlistId"

        fun newInstance(playlistId: String) = PlaylistDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_PLAYLIST_ID, playlistId) }
        }
    }
}
