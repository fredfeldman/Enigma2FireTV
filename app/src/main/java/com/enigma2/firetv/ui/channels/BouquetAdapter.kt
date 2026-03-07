package com.enigma2.firetv.ui.channels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Bouquet

class BouquetAdapter(
    private val onBouquetSelected: (Bouquet) -> Unit
) : ListAdapter<Bouquet, BouquetAdapter.ViewHolder>(DiffCallback()) {

    private var selectedPosition = 0

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_bouquet_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bouquet, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bouquet = getItem(position)
        holder.tvName.text = bouquet.name
        holder.tvName.isSelected = position == selectedPosition
        holder.itemView.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onBouquetSelected(bouquet)
        }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) holder.itemView.isSelected = true
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Bouquet>() {
        override fun areItemsTheSame(oldItem: Bouquet, newItem: Bouquet) = oldItem.ref == newItem.ref
        override fun areContentsTheSame(oldItem: Bouquet, newItem: Bouquet) = oldItem == newItem
    }
}
