package com.enigma2.firetv.ui.playlists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.RecordingPlaylist

class PlaylistAdapter(
    private val onClick: (RecordingPlaylist) -> Unit,
    private val onLongClick: (RecordingPlaylist) -> Unit
) : ListAdapter<RecordingPlaylist, PlaylistAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_playlist_name)
        val tvCount: TextView = view.findViewById(R.id.tv_playlist_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pl = getItem(position)
        holder.tvName.text = pl.name
        val n = pl.entries.size
        holder.tvCount.text = holder.itemView.context.resources
            .getQuantityString(R.plurals.playlist_entry_count, n, n)
        holder.itemView.setOnClickListener { onClick(pl) }
        holder.itemView.setOnLongClickListener { onLongClick(pl); true }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<RecordingPlaylist>() {
            override fun areItemsTheSame(a: RecordingPlaylist, b: RecordingPlaylist) = a.id == b.id
            override fun areContentsTheSame(a: RecordingPlaylist, b: RecordingPlaylist) = a == b
        }
    }
}
