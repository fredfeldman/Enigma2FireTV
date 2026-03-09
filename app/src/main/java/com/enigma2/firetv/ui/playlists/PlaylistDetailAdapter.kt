package com.enigma2.firetv.ui.playlists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.PlaylistEntry

class PlaylistDetailAdapter(
    private val onPlay: (Int) -> Unit,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistDetailAdapter.ViewHolder>() {

    private val items = mutableListOf<PlaylistEntry>()

    fun setItems(list: List<PlaylistEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun getItems(): List<PlaylistEntry> = items.toList()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        val pos = holder.adapterPosition

        holder.tvIndex.text = "${pos + 1}."
        holder.tvTitle.text = entry.title
        holder.tvChannel.text = entry.channel

        holder.btnUp.visibility = if (pos > 0) View.VISIBLE else View.INVISIBLE
        holder.btnDown.visibility = if (pos < items.size - 1) View.VISIBLE else View.INVISIBLE

        holder.itemView.setOnClickListener { onPlay(pos) }
        holder.btnUp.setOnClickListener { onMoveUp(pos) }
        holder.btnDown.setOnClickListener { onMoveDown(pos) }
        holder.btnRemove.setOnClickListener { onRemove(pos) }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tv_entry_index)
        val tvTitle: TextView = view.findViewById(R.id.tv_entry_title)
        val tvChannel: TextView = view.findViewById(R.id.tv_entry_channel)
        val btnUp: TextView = view.findViewById(R.id.btn_entry_up)
        val btnDown: TextView = view.findViewById(R.id.btn_entry_down)
        val btnRemove: TextView = view.findViewById(R.id.btn_entry_remove)
    }
}
