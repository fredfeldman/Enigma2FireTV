package com.enigma2.firetv.ui.bouqueteditor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.repository.Enigma2Repository

/**
 * Bouquet list for the editor. Edit is always allowed (channel-level edits
 * fall back to local override); Rename/Delete are receiver-only and only
 * apply to user bouquets that the BouquetEditor plugin reports.
 */
class BouquetEditorAdapter(
    private val onEdit: (Bouquet) -> Unit,
    private val onRename: (Bouquet) -> Unit,
    private val onDelete: (Bouquet) -> Unit,
) : RecyclerView.Adapter<BouquetEditorAdapter.VH>() {

    private val items = mutableListOf<Bouquet>()
    private var userRefs: Set<String> = emptySet()
    private var capability: Enigma2Repository.BouquetEditorCapability =
        Enigma2Repository.BouquetEditorCapability.Missing

    fun submit(
        bouquets: List<Bouquet>,
        userBouquetRefs: Set<String>,
        capability: Enigma2Repository.BouquetEditorCapability
    ) {
        items.clear()
        items.addAll(bouquets)
        this.userRefs = userBouquetRefs
        this.capability = capability
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bouquet_editor_row, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = items[position]
        val isServer = capability == Enigma2Repository.BouquetEditorCapability.Available
        val isUser = isServer && b.ref in userRefs

        holder.name.text = b.name
        val count = b.channels?.size ?: 0
        val ctx = holder.itemView.context
        holder.subtitle.text = ctx.getString(
            if (isUser) R.string.bouquet_editor_subtitle_user
            else R.string.bouquet_editor_subtitle_provider,
            count
        )

        // Edit always available (channel-level edits work in local mode too).
        holder.btnEdit.setOnClickListener { onEdit(b) }

        // Rename/Delete: server only, user bouquets only.
        val canMutate = isServer && isUser
        holder.btnRename.alpha = if (canMutate) 1f else 0.4f
        holder.btnDelete.alpha = if (canMutate) 1f else 0.4f
        holder.btnRename.isEnabled = canMutate
        holder.btnDelete.isEnabled = canMutate
        holder.btnRename.setOnClickListener { onRename(b) }
        holder.btnDelete.setOnClickListener { onDelete(b) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_be_name)
        val subtitle: TextView = view.findViewById(R.id.tv_be_subtitle)
        val btnEdit: TextView = view.findViewById(R.id.btn_be_edit)
        val btnRename: TextView = view.findViewById(R.id.btn_be_rename)
        val btnDelete: TextView = view.findViewById(R.id.btn_be_delete)
    }
}
