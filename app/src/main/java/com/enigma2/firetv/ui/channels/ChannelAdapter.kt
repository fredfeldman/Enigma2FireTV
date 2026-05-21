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

    /** Cached per-Adapter so we don't read SharedPreferences on every bind. */
    private var cachedPrefs: ReceiverPreferences? = null
    private fun prefs(context: android.content.Context): ReceiverPreferences =
        cachedPrefs ?: ReceiverPreferences(context).also { cachedPrefs = it }

    companion object {
        /** Payload used by [updateNowNext] / [updateFavorites] / [updateRecordingRefs]
         *  so we can refresh decorations without rebinding picons. */
        private const val PAYLOAD_DECORATIONS = "decorations"
    }

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
        val prefs = prefs(holder.itemView.context)

        holder.tvNumber.text = (position + 1).toString()
        holder.tvName.text = service.name
        holder.ivPicon.contentDescription =
            holder.itemView.context.getString(R.string.cd_picon_for, service.name)

        // Picon load order:
        // 1. If piconPath is already a full URL (IPTV logo), load it directly.
        // 2. ref with trailing _ (e.g. 1_0_19_1_0_0_8c90fd2_0_0_0_.png)
        // 3. ref without trailing _ (e.g. 1_0_19_1_0_0_8c90fd2_0_0_0.png)
        // 4. channel name (e.g. 1 CANAL SUR HD.png)
        val piconUrl = when {
            service.piconPath?.startsWith("http") == true -> service.piconPath
            service.piconPath != null -> prefs.piconUrl(service.piconPath)
            else -> prefs.piconFallbackUrl(service.ref)
        }
        val piconUrlShort = if (service.ref.startsWith("iptv_ch:")) null
                            else prefs.piconFallbackUrlShort(service.ref)
        val piconUrlName = if (service.ref.startsWith("iptv_ch:")) null
                           else prefs.piconFallbackUrlByName(service.name)
        Glide.with(holder.ivPicon)
            .load(piconUrl)
            .placeholder(R.drawable.ic_channel_placeholder)
            .error(
                if (piconUrlShort != null) {
                    Glide.with(holder.ivPicon)
                        .load(piconUrlShort)
                        .error(
                            if (piconUrlName != null) {
                                Glide.with(holder.ivPicon)
                                    .load(piconUrlName)
                                    .placeholder(R.drawable.ic_channel_placeholder)
                                    .error(R.drawable.ic_channel_placeholder)
                            } else {
                                Glide.with(holder.ivPicon)
                                    .load(R.drawable.ic_channel_placeholder)
                            }
                        )
                } else {
                    Glide.with(holder.ivPicon)
                        .load(R.drawable.ic_channel_placeholder)
                }
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

        // TalkBack: read the whole row in one breath
        val nowText = holder.tvNowPlaying.text.toString()
        holder.itemView.contentDescription = if (nowText.isNotBlank()) {
            holder.itemView.context.getString(
                R.string.cd_channel_row, position + 1, service.name, nowText
            )
        } else {
            holder.itemView.context.getString(
                R.string.cd_channel_row_no_epg, position + 1, service.name
            )
        }
    }

    /**
     * Partial-rebind path used when only decorations (now/next, favorites,
     * recording badge) changed. Skips picon reload entirely.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty() || !payloads.contains(PAYLOAD_DECORATIONS)) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        val service = getItem(position)
        bindDecorations(holder, service, position)
    }

    private fun bindDecorations(holder: ViewHolder, service: Service, position: Int) {
        val nn = nowNextMap[service.ref]
        val nowEvt = nn?.nowEvent
        val nextEvt = nn?.nextEvent
        if (nowEvt != null) {
            val endTime = timeFmt.format(Date(nowEvt.endMs))
            val nowText = "${nowEvt.title}  \u25b8 $endTime"
            holder.tvNowPlaying.text = if (nextEvt != null) "$nowText  \u2502  ${nextEvt.title}" else nowText
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
        holder.btnFavorite.text = if (service.ref in favoriteRefs) "\u2605" else "\u2606"
        holder.tvRecBadge.visibility = if (service.ref in recordingRefs) View.VISIBLE else View.GONE

        val nowText = holder.tvNowPlaying.text.toString()
        holder.itemView.contentDescription = if (nowText.isNotBlank()) {
            holder.itemView.context.getString(R.string.cd_channel_row, position + 1, service.name, nowText)
        } else {
            holder.itemView.context.getString(R.string.cd_channel_row_no_epg, position + 1, service.name)
        }
    }

    fun updateNowNext(events: List<NowNextEvent>) {
        nowNextMap.clear()
        events.forEach { nowNextMap[it.serviceRef] = it }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_DECORATIONS)
    }

    fun updateFavorites(refs: Set<String>) {
        if (favoriteRefs == refs) return
        favoriteRefs = refs
        notifyItemRangeChanged(0, itemCount, PAYLOAD_DECORATIONS)
    }

    fun updateRecordingRefs(refs: Set<String>) {
        if (recordingRefs == refs) return
        recordingRefs = refs
        notifyItemRangeChanged(0, itemCount, PAYLOAD_DECORATIONS)
    }

    private class DiffCallback : DiffUtil.ItemCallback<Service>() {
        override fun areItemsTheSame(oldItem: Service, newItem: Service) = oldItem.ref == newItem.ref
        override fun areContentsTheSame(oldItem: Service, newItem: Service) = oldItem == newItem
    }
}
