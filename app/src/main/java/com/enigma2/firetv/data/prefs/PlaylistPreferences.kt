package com.enigma2.firetv.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.enigma2.firetv.data.model.PlaylistEntry
import com.enigma2.firetv.data.model.RecordingPlaylist
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists [RecordingPlaylist]s as a JSON array in SharedPreferences.
 * Playlists are device-independent (shared across all configured receivers).
 */
class PlaylistPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    var playlists: List<RecordingPlaylist>
        get() {
            val json = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
            return try {
                val type = object : TypeToken<List<RecordingPlaylist>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        private set(value) = prefs.edit { putString(KEY_PLAYLISTS, gson.toJson(value)) }

    fun createPlaylist(name: String): RecordingPlaylist {
        val pl = RecordingPlaylist(name = name)
        playlists = playlists + pl
        return pl
    }

    fun renamePlaylist(id: String, newName: String) {
        playlists = playlists.map { if (it.id == id) it.copy(name = newName) else it }
    }

    fun deletePlaylist(id: String) {
        playlists = playlists.filter { it.id != id }
    }

    fun addEntryToPlaylist(playlistId: String, entry: PlaylistEntry) {
        playlists = playlists.map { pl ->
            if (pl.id == playlistId) pl.copy(entries = pl.entries + entry) else pl
        }
    }

    fun removeEntryFromPlaylist(playlistId: String, index: Int) {
        playlists = playlists.map { pl ->
            if (pl.id == playlistId) {
                val updated = pl.entries.toMutableList().also { it.removeAt(index) }
                pl.copy(entries = updated)
            } else pl
        }
    }

    fun moveEntryUp(playlistId: String, index: Int) {
        if (index <= 0) return
        playlists = playlists.map { pl ->
            if (pl.id == playlistId) {
                val list = pl.entries.toMutableList()
                val tmp = list[index - 1]; list[index - 1] = list[index]; list[index] = tmp
                pl.copy(entries = list)
            } else pl
        }
    }

    fun moveEntryDown(playlistId: String, index: Int) {
        val pl = playlists.find { it.id == playlistId } ?: return
        if (index >= pl.entries.size - 1) return
        playlists = playlists.map { p ->
            if (p.id == playlistId) {
                val list = p.entries.toMutableList()
                val tmp = list[index + 1]; list[index + 1] = list[index]; list[index] = tmp
                p.copy(entries = list)
            } else p
        }
    }

    fun getPlaylist(id: String): RecordingPlaylist? = playlists.firstOrNull { it.id == id }

    companion object {
        private const val PREFS_NAME = "recording_playlists"
        private const val KEY_PLAYLISTS = "playlists"
    }
}
