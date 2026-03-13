package com.enigma2.firetv.ui.timers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Timer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimerAdapter(
    private val onDeleteClick: (Timer) -> Unit
) : RecyclerView.Adapter<TimerAdapter.VH>() {

    private var items: List<Timer> = emptyList()
    private val timeFmt = SimpleDateFormat("EEE dd MMM  HH:mm", Locale.getDefault())

    fun submitList(list: List<Timer>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_timer_name)
        val tvChannel: TextView = view.findViewById(R.id.tv_timer_channel)
        val tvTime: TextView = view.findViewById(R.id.tv_timer_time)
        val tvState: TextView = view.findViewById(R.id.tv_timer_state_badge)
        val btnDelete: TextView = view.findViewById(R.id.btn_delete_timer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_timer, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val timer = items[position]
        holder.tvName.text = timer.name
        holder.tvChannel.text = timer.serviceName ?: timer.serviceRef
        holder.tvTime.text = buildString {
            append(timeFmt.format(Date(timer.beginMs)))
            append(" – ")
            append(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timer.endMs)))
            val durationMin = (timer.endTimestamp - timer.beginTimestamp) / 60
            append("  ($durationMin min)")
        }
        holder.tvState.text = timer.stateLabel()
        holder.tvState.setBackgroundColor(
            when (timer.state) {
                2 -> holder.itemView.context.getColor(R.color.error)       // Recording
                3 -> 0xFF555555.toInt()                                     // Done
                4 -> holder.itemView.context.getColor(R.color.error)       // Failed
                else -> holder.itemView.context.getColor(R.color.accent)   // Waiting/Preparing
            }
        )
        holder.btnDelete.setOnClickListener { onDeleteClick(timer) }
    }
}
