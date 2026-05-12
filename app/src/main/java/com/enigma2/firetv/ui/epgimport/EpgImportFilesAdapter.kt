package com.enigma2.firetv.ui.epgimport

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R

/**
 * Lists `*.sources.xml` filenames. Tap → open detail view.
 */
class EpgImportFilesAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<EpgImportFilesAdapter.VH>() {

    private val items = mutableListOf<String>()

    fun submit(paths: List<String>) {
        items.clear()
        items.addAll(paths)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_epgimport_file, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val path = items[position]
        holder.name.text = path.substringAfterLast('/').removeSuffix(".sources.xml")
        holder.path.text = path
        holder.itemView.setOnClickListener { onClick(path) }
    }

    class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tv_epi_file_name)
        val path: TextView = v.findViewById(R.id.tv_epi_file_path)
    }
}
