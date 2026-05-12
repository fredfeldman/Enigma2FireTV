package com.enigma2.firetv.ui.recordings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Recording
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for the recordings list.
 *
 * @param onRecordingClick      Invoked when OK / Enter is pressed on an item.
 * @param onRecordingFocused    Invoked whenever an item gains D-pad focus.
 * @param onRecordingLongClick  Invoked on long-press (for add-to-playlist).
 */
class RecordingAdapter(
    private val onRecordingClick: (Recording) -> Unit,
    private val onRecordingFocused: (Recording) -> Unit,
    private val onRecordingLongClick: ((Recording) -> Unit)? = null,
    private val onSelectionToggle: ((Recording) -> Unit)? = null
) : ListAdapter<Recording, RecordingAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val dateFmt = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())

    var selectionMode: Boolean = false
        private set
    private var selectedFilenames: Set<String> = emptySet()

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode == enabled) return
        selectionMode = enabled
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
    }

    fun setSelectedFilenames(set: Set<String>) {
        val previous = selectedFilenames
        if (previous == set) return
        selectedFilenames = set
        // Only refresh rows whose selection state actually flipped.
        val toggled = (previous - set) + (set - previous)
        if (toggled.isEmpty()) return
        for (i in 0 until itemCount) {
            val item = getItem(i)
            if (item.filename != null && item.filename in toggled) {
                notifyItemChanged(i, PAYLOAD_SELECTION)
            }
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCheck: TextView = view.findViewById(R.id.tv_rec_check)
        val tvTitle: TextView = view.findViewById(R.id.tv_rec_title)
        val tvChannel: TextView = view.findViewById(R.id.tv_rec_channel)
        val tvDatetime: TextView = view.findViewById(R.id.tv_rec_datetime)
        val tvDuration: TextView = view.findViewById(R.id.tv_rec_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION) && payloads.size == 1) {
            applySelectionVisuals(holder, getItem(position))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun applySelectionVisuals(holder: ViewHolder, recording: Recording) {
        val isSelected = selectedFilenames.contains(recording.filename)
        if (selectionMode) {
            holder.tvCheck.visibility = View.VISIBLE
            holder.tvCheck.text = if (isSelected) "\u2611" else "\u2610"
            holder.itemView.isActivated = isSelected
        } else {
            holder.tvCheck.visibility = View.GONE
            holder.itemView.isActivated = false
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recording = getItem(position)

        holder.tvTitle.text = recording.displayTitle

        holder.tvChannel.text = recording.channelName.orEmpty()

        holder.tvDatetime.text =
            if (recording.startTimestamp > 0) dateFmt.format(Date(recording.startMs)) else ""

        val dur = recording.formatDuration()
        val context = holder.itemView.context
        val sizeMb = recording.fileSizeBytes?.let {
            val mb = it / (1024 * 1024)
            if (mb >= 1024) context.getString(R.string.recording_size_gb, mb / 1024.0)
            else context.getString(R.string.recording_size_mb, mb)
        }
        holder.tvDuration.text = listOfNotNull(
            dur.takeIf { it.isNotBlank() }?.let { context.getString(R.string.recording_duration_label, it) },
            sizeMb
        ).joinToString("   ")

        // Compose a single TalkBack-friendly description of the whole row
        holder.itemView.contentDescription = context.getString(
            R.string.cd_recording_row,
            recording.displayTitle,
            holder.tvDatetime.text.toString().ifBlank { "—" },
            recording.channelName.orEmpty().ifBlank { "—" },
            holder.tvDuration.text.toString().ifBlank { "—" }
        )

        // Selection-mode visuals
        applySelectionVisuals(holder, recording)

        holder.itemView.setOnClickListener {
            if (selectionMode) onSelectionToggle?.invoke(recording)
            else onRecordingClick(recording)
        }
        holder.itemView.setOnLongClickListener {
            onRecordingLongClick?.invoke(recording)
            onRecordingLongClick != null
        }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) onRecordingFocused(recording)
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Recording>() {
            override fun areItemsTheSame(a: Recording, b: Recording) = a.filename == b.filename
            override fun areContentsTheSame(a: Recording, b: Recording) = a == b
        }
    }
}
