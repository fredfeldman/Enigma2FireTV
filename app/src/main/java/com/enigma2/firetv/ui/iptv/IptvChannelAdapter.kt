package com.enigma2.firetv.ui.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.IptvChannel
import com.enigma2.firetv.data.model.IptvEpgEvent

class IptvChannelAdapter(
    private val onChannelClick: (IptvChannel) -> Unit
) : RecyclerView.Adapter<IptvChannelAdapter.VH>() {

    private var channels: List<IptvChannel> = emptyList()
    private var epg: Map<String, List<IptvEpgEvent>> = emptyMap()

    fun setChannels(list: List<IptvChannel>) {
        channels = list
        notifyDataSetChanged()
    }

    fun setEpg(epgMap: Map<String, List<IptvEpgEvent>>) {
        epg = epgMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(channels[position], position + 1, epg)
        holder.itemView.setOnClickListener { onChannelClick(channels[position]) }
    }

    override fun getItemCount() = channels.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvNumber   = view.findViewById<TextView>(R.id.tv_channel_number)
        private val ivPicon    = view.findViewById<ImageView>(R.id.iv_picon)
        private val tvName     = view.findViewById<TextView>(R.id.tv_channel_name)
        private val tvNow      = view.findViewById<TextView>(R.id.tv_now_playing)
        private val pbProgress = view.findViewById<ProgressBar>(R.id.pb_event_progress)
        private val tvRec      = view.findViewById<TextView>(R.id.tv_rec_badge)

        fun bind(channel: IptvChannel, number: Int, epg: Map<String, List<IptvEpgEvent>>) {
            tvNumber.text = number.toString()
            tvRec.visibility = View.GONE
            tvName.text = channel.name

            if (channel.logoUrl.isNotBlank()) {
                Glide.with(itemView.context)
                    .load(channel.logoUrl)
                    .error(R.drawable.ic_channel_placeholder)
                    .into(ivPicon)
            } else {
                ivPicon.setImageResource(R.drawable.ic_channel_placeholder)
            }

            val nowMs = System.currentTimeMillis()
            val currentEvent = epg[channel.tvgId]?.firstOrNull { e ->
                e.startMs <= nowMs && (e.endMs == 0L || e.endMs > nowMs)
            }

            if (currentEvent != null) {
                tvNow.text = currentEvent.title
                tvNow.visibility = View.VISIBLE
                val duration = currentEvent.endMs - currentEvent.startMs
                if (duration > 0) {
                    pbProgress.progress = ((nowMs - currentEvent.startMs).toFloat() / duration * 100)
                        .toInt().coerceIn(0, 100)
                    pbProgress.visibility = View.VISIBLE
                } else {
                    pbProgress.visibility = View.GONE
                }
            } else {
                tvNow.visibility = View.GONE
                pbProgress.visibility = View.GONE
            }
        }
    }
}
