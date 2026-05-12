package com.enigma2.firetv.ui.bouqueteditor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Service

/** Channel rows inside one bouquet, with up/down/remove buttons. */
class BouquetEditChannelAdapter(
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onRemove: (Int) -> Unit,
) : RecyclerView.Adapter<BouquetEditChannelAdapter.VH>() {

    private val items = mutableListOf<Service>()

    fun submit(list: List<Service>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bouquet_edit_channel, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.name.text = s.name
        holder.btnUp.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p > 0) onMoveUp(p)
        }
        holder.btnDown.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p in 0 until items.size - 1) onMoveDown(p)
        }
        holder.btnRemove.setOnClickListener {
            val p = holder.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onRemove(p)
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_bec_name)
        val btnUp: TextView = view.findViewById(R.id.btn_bec_up)
        val btnDown: TextView = view.findViewById(R.id.btn_bec_down)
        val btnRemove: TextView = view.findViewById(R.id.btn_bec_remove)
    }
}
