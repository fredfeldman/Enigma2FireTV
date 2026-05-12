package com.enigma2.firetv.ui.timers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Timer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimerAdapter(
    private val onDeleteClick: (Timer) -> Unit,
    private val onToggleClick: ((Timer) -> Unit)? = null
) : ListAdapter<Timer, TimerAdapter.VH>(DIFF) {

    private val timeFmt = SimpleDateFormat("EEE dd MMM  HH:mm", Locale.getDefault())
    private val endTimeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_timer_name)
        val tvChannel: TextView = view.findViewById(R.id.tv_timer_channel)
        val tvTime: TextView = view.findViewById(R.id.tv_timer_time)
        val tvState: TextView = view.findViewById(R.id.tv_timer_state_badge)
        val btnDelete: TextView = view.findViewById(R.id.btn_delete_timer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_timer, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val timer = getItem(position)
        holder.tvName.text = timer.name
        holder.tvChannel.text = timer.serviceName ?: timer.serviceRef
        holder.tvTime.text = buildString {
            append(timeFmt.format(Date(timer.beginMs)))
            append(" \u2013 ")
            append(endTimeFmt.format(Date(timer.endMs)))
            val durationMin = ((timer.endTimestamp - timer.beginTimestamp) / 60).toInt()
            append("  ")
            append(holder.itemView.context.getString(R.string.timer_duration_minutes, durationMin))
        }
        holder.tvState.text = holder.itemView.context.getString(timer.stateLabelResId())
        holder.tvState.setBackgroundColor(
            when {
                timer.disabled == 1 -> 0xFF555555.toInt()
                timer.state == 2 -> holder.itemView.context.getColor(R.color.error)       // Recording
                timer.state == 3 -> 0xFF555555.toInt()                                     // Done
                timer.state == 4 -> holder.itemView.context.getColor(R.color.error)       // Failed
                else -> holder.itemView.context.getColor(R.color.accent)                   // Waiting/Preparing
            }
        )
        holder.btnDelete.setOnClickListener { onDeleteClick(timer) }
        if (onToggleClick != null) {
            holder.itemView.setOnClickListener { onToggleClick.invoke(timer) }
        }

        holder.itemView.contentDescription = holder.itemView.context.getString(
            R.string.cd_timer_row,
            timer.name.ifBlank { "—" },
            (timer.serviceName ?: timer.serviceRef).ifBlank { "—" },
            holder.tvTime.text.toString(),
            holder.tvState.text.toString()
        )
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Timer>() {
            override fun areItemsTheSame(a: Timer, b: Timer) =
                a.serviceRef == b.serviceRef &&
                a.beginTimestamp == b.beginTimestamp &&
                a.endTimestamp == b.endTimestamp
            override fun areContentsTheSame(a: Timer, b: Timer) = a == b
        }
    }
}
