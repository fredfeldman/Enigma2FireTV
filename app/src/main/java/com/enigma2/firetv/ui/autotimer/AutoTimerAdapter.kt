package com.enigma2.firetv.ui.autotimer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.AutoTimer

/**
 * RecyclerView adapter for the AutoTimer list.
 *
 * Each row shows the rule name, a description (match phrase + restricted services),
 * a coloured dot indicating enabled/disabled, plus inline Enable/Disable and Delete
 * buttons that route through the supplied callbacks.
 */
class AutoTimerAdapter(
    private val onToggle: (AutoTimer) -> Unit,
    private val onDelete: (AutoTimer) -> Unit,
    private val onEdit: (AutoTimer) -> Unit
) : ListAdapter<AutoTimer, AutoTimerAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_autotimer, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tv_at_name)
        private val subtitle: TextView = itemView.findViewById(R.id.tv_at_subtitle)
        private val dot: View = itemView.findViewById(R.id.v_enabled_dot)
        private val btnToggle: TextView = itemView.findViewById(R.id.btn_at_toggle)
        private val btnDelete: TextView = itemView.findViewById(R.id.btn_at_delete)

        fun bind(rule: AutoTimer) {
            name.text = rule.name.ifBlank { rule.match }
            subtitle.text = rule.describe()
            dot.setBackgroundColor(
                if (rule.enabled)
                    itemView.resources.getColor(android.R.color.holo_green_light, null)
                else
                    itemView.resources.getColor(android.R.color.darker_gray, null)
            )
            btnToggle.text = itemView.context.getString(
                if (rule.enabled) R.string.autotimer_disable else R.string.autotimer_enable
            )
            btnToggle.setOnClickListener { onToggle(rule) }
            btnDelete.setOnClickListener { onDelete(rule) }
            itemView.setOnClickListener { onEdit(rule) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AutoTimer>() {
            override fun areItemsTheSame(old: AutoTimer, new: AutoTimer): Boolean = old.id == new.id
            override fun areContentsTheSame(old: AutoTimer, new: AutoTimer): Boolean = old == new
        }
    }
}
