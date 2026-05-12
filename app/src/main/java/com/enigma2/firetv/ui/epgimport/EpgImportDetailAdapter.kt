package com.enigma2.firetv.ui.epgimport

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.EpgImportSource

/**
 * Heterogeneous adapter that renders interleaved category headers and source
 * rows for one `.sources.xml` file.
 */
class EpgImportDetailAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Header(val name: String) : Row()
        data class Source(val source: EpgImportSource) : Row()
    }

    private val items = mutableListOf<Row>()

    fun submit(rows: List<Row>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.Source -> TYPE_SOURCE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_epgimport_category, parent, false))
        } else {
            SourceVH(inflater.inflate(R.layout.item_epgimport_source, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = items[position]) {
            is Row.Header -> (holder as HeaderVH).bind(row.name)
            is Row.Source -> (holder as SourceVH).bind(row.source)
        }
    }

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val tv = v as TextView
        fun bind(name: String) { tv.text = name }
    }

    class SourceVH(v: View) : RecyclerView.ViewHolder(v) {
        private val desc: TextView = v.findViewById(R.id.tv_epi_src_desc)
        private val meta: TextView = v.findViewById(R.id.tv_epi_src_meta)
        private val urls: TextView = v.findViewById(R.id.tv_epi_src_urls)

        fun bind(s: EpgImportSource) {
            val ctx = itemView.context
            desc.text = s.description.ifBlank { ctx.getString(R.string.epgimport_no_description) }
            meta.text = buildString {
                append(ctx.getString(R.string.epgimport_type_label, s.type.ifBlank { "?" }))
                if (!s.channels.isNullOrBlank()) {
                    append("   ")
                    append(ctx.getString(R.string.epgimport_channels_label, s.channels))
                }
                append("   ")
                append(ctx.getString(R.string.epgimport_mirror_count, s.urls.size))
            }
            urls.text = s.urls.joinToString("\n")
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SOURCE = 1
    }
}
