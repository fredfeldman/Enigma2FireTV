package com.enigma2.firetv.ui.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R

class IptvGroupAdapter(
    private val onGroupClick: (String) -> Unit
) : RecyclerView.Adapter<IptvGroupAdapter.VH>() {

    private var groups: List<String> = emptyList()
    private var selectedIndex: Int = 0

    fun setGroups(list: List<String>, selectedIndex: Int = 0) {
        this.groups = list
        this.selectedIndex = selectedIndex
        notifyDataSetChanged()
    }

    fun setSelected(index: Int) {
        val old = selectedIndex
        selectedIndex = index
        notifyItemChanged(old)
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bouquet, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(groups[position], position == selectedIndex)
        holder.itemView.setOnClickListener { onGroupClick(groups[position]) }
    }

    override fun getItemCount() = groups.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv = view.findViewById<TextView>(R.id.tv_bouquet_name)
        fun bind(name: String, selected: Boolean) {
            tv.text = name
            tv.isSelected = selected
        }
    }
}
