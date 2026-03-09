package com.enigma2.firetv.ui.playlists

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.prefs.PlaylistPreferences

/**
 * Lists all saved recording playlists.
 * - Tap a playlist to open [PlaylistDetailFragment].
 * - Long-press to rename or delete.
 * - "New Playlist" button creates a blank playlist.
 */
class PlaylistManagerFragment : Fragment() {

    private lateinit var playlistPrefs: PlaylistPreferences
    private lateinit var adapter: PlaylistAdapter
    private lateinit var rvPlaylists: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnNew: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_playlist_manager, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistPrefs = PlaylistPreferences(requireContext())

        rvPlaylists = view.findViewById(R.id.rv_playlists)
        tvEmpty = view.findViewById(R.id.tv_playlists_empty)
        btnNew = view.findViewById(R.id.btn_new_playlist)

        adapter = PlaylistAdapter(
            onClick = { pl ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.main_container, PlaylistDetailFragment.newInstance(pl.id))
                    .addToBackStack(null)
                    .commit()
            },
            onLongClick = { pl -> showPlaylistOptions(pl.id, pl.name) }
        )
        rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        rvPlaylists.adapter = adapter

        btnNew.setOnClickListener { showCreateDialog() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val list = playlistPrefs.playlists
        adapter.submitList(list.toList())
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showCreateDialog() {
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
                    playlistPrefs.createPlaylist(name)
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPlaylistOptions(id: String, name: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setItems(arrayOf(
                getString(R.string.rename),
                getString(R.string.delete)
            )) { _, which ->
                when (which) {
                    0 -> showRenameDialog(id, name)
                    1 -> confirmDelete(id, name)
                }
            }
            .show()
    }

    private fun showRenameDialog(id: String, currentName: String) {
        val et = EditText(requireContext()).apply {
            setText(currentName)
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.rename))
            .setView(et)
            .setPositiveButton(getString(R.string.rename)) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotBlank()) {
                    playlistPrefs.renamePlaylist(id, name)
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(id: String, name: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.playlist_delete_confirm_title))
            .setMessage(getString(R.string.playlist_delete_confirm_message, name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                playlistPrefs.deletePlaylist(id)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
