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
 * @param onRecordingClick   Invoked when OK / Enter is pressed on an item.
 * @param onRecordingFocused Invoked whenever an item gains D-pad focus.
 */
class RecordingAdapter(
    private val onRecordingClick: (Recording) -> Unit,
    private val onRecordingFocused: (Recording) -> Unit
) : ListAdapter<Recording, RecordingAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val dateFmt = SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault())

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recording = getItem(position)

        holder.tvTitle.text = recording.displayTitle

        holder.tvChannel.text = recording.channelName.orEmpty()

        holder.tvDatetime.text =
            if (recording.startTimestamp > 0) dateFmt.format(Date(recording.startMs)) else ""

        val dur = recording.formatDuration()
        val sizeMb = recording.fileSizeBytes?.let {
            val mb = it / (1024 * 1024)
            if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "$mb MB"
        }
        holder.tvDuration.text = listOfNotNull(
            dur.takeIf { it.isNotBlank() }?.let { "Duration: $it" },
            sizeMb
        ).joinToString("   ")

        holder.itemView.setOnClickListener { onRecordingClick(recording) }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) onRecordingFocused(recording)
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Recording>() {
            override fun areItemsTheSame(a: Recording, b: Recording) = a.filename == b.filename
            override fun areContentsTheSame(a: Recording, b: Recording) = a == b
        }
    }
}
