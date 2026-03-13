package com.enigma2.firetv.ui.channels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.NowNextEvent
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChannelAdapter(
    private val onChannelClick: (Service, Int) -> Unit,
    private val onChannelLongClick: (Service) -> Unit,
    private val onFavoriteToggle: ((Service) -> Unit)? = null
) : ListAdapter<Service, ChannelAdapter.ViewHolder>(DiffCallback()) {

    private val nowNextMap = mutableMapOf<String, NowNextEvent>()
    private var favoriteRefs: Set<String> = emptySet()
    private var recordingRefs: Set<String> = emptySet()
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tv_channel_number)
        val ivPicon: ImageView = view.findViewById(R.id.iv_picon)
        val tvName: TextView = view.findViewById(R.id.tv_channel_name)
        val tvNowPlaying: TextView = view.findViewById(R.id.tv_now_playing)
        val pbProgress: ProgressBar = view.findViewById(R.id.pb_event_progress)
        val btnFavorite: TextView = view.findViewById(R.id.btn_favorite)
        val tvRecBadge: TextView = view.findViewById(R.id.tv_rec_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val service = getItem(position)
        val prefs = ReceiverPreferences(holder.itemView.context)

        holder.tvNumber.text = (position + 1).toString()
        holder.tvName.text = service.name

        // Picon load order:
        // 1. ref with trailing _ (e.g. 1_0_19_1_0_0_8c90fd2_0_0_0_.png)
        // 2. ref without trailing _ (e.g. 1_0_19_1_0_0_8c90fd2_0_0_0.png)
        // 3. channel name (e.g. 1 CANAL SUR HD.png)
        val piconUrl = if (service.piconPath != null) prefs.piconUrl(service.piconPath)
                       else prefs.piconFallbackUrl(service.ref)
        val piconUrlShort = prefs.piconFallbackUrlShort(service.ref)
        val piconUrlName = prefs.piconFallbackUrlByName(service.name)
        Glide.with(holder.ivPicon)
            .load(piconUrl)
            .placeholder(R.drawable.ic_channel_placeholder)
            .error(
                Glide.with(holder.ivPicon)
                    .load(piconUrlShort)
                    .error(
                        Glide.with(holder.ivPicon)
                            .load(piconUrlName)
                            .placeholder(R.drawable.ic_channel_placeholder)
                            .error(R.drawable.ic_channel_placeholder)
                    )
            )
            .into(holder.ivPicon)

        // Show now/next info
        val nn = nowNextMap[service.ref]
        val nowEvt = nn?.nowEvent
        val nextEvt = nn?.nextEvent
        if (nowEvt != null) {
            val endTime = timeFmt.format(Date(nowEvt.endMs))
            val nowText = "${nowEvt.title}  ▸ $endTime"
            holder.tvNowPlaying.text = if (nextEvt != null) "$nowText  │  ${nextEvt.title}" else nowText

            // Calculate progress
            val currentTime = System.currentTimeMillis()
            val total = nowEvt.endMs - nowEvt.beginMs
            val elapsed = currentTime - nowEvt.beginMs
            val progress = if (total > 0) ((elapsed.toFloat() / total) * 100).toInt().coerceIn(0, 100) else 0
            holder.pbProgress.progress = progress
            holder.pbProgress.visibility = View.VISIBLE
        } else if (nextEvt != null) {
            holder.tvNowPlaying.text = "Next: ${nextEvt.title}"
            holder.pbProgress.visibility = View.INVISIBLE
        } else {
            holder.tvNowPlaying.text = ""
            holder.pbProgress.visibility = View.INVISIBLE
        }

        holder.itemView.setOnClickListener { onChannelClick(service, position) }
        holder.itemView.setOnLongClickListener {
            onChannelLongClick(service)
            true
        }

        // Favorite star indicator (visual only)
        holder.btnFavorite.text = if (service.ref in favoriteRefs) "★" else "☆"

        // Recording-in-progress badge
        holder.tvRecBadge.visibility = if (service.ref in recordingRefs) View.VISIBLE else View.GONE
    }

    fun updateNowNext(events: List<NowNextEvent>) {
        nowNextMap.clear()
        events.forEach { nowNextMap[it.serviceRef] = it }
        notifyDataSetChanged()
    }

    fun updateFavorites(refs: Set<String>) {
        favoriteRefs = refs
        notifyDataSetChanged()
    }

    fun updateRecordingRefs(refs: Set<String>) {
        recordingRefs = refs
        notifyDataSetChanged()
    }

    private class DiffCallback : DiffUtil.ItemCallback<Service>() {
        override fun areItemsTheSame(oldItem: Service, newItem: Service) = oldItem.ref == newItem.ref
        override fun areContentsTheSame(oldItem: Service, newItem: Service) = oldItem == newItem
    }
}
