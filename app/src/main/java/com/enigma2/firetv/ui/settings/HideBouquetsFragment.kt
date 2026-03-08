package com.enigma2.firetv.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.os.Bundle
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch

class HideBouquetsFragment : Fragment() {

    private val repository = Enigma2Repository()
    private lateinit var prefs: ReceiverPreferences
    private lateinit var adapter: BouquetToggleAdapter
    private lateinit var rvBouquets: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvEmpty: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_hide_bouquets, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = ReceiverPreferences(requireContext())

        rvBouquets = view.findViewById(R.id.rv_bouquets_toggle)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        tvEmpty = view.findViewById(R.id.tv_empty)

        adapter = BouquetToggleAdapter(prefs.hiddenBouquetRefs.toMutableSet()) { ref, isVisible ->
            val hidden = prefs.hiddenBouquetRefs.toMutableSet()
            if (isVisible) hidden.remove(ref) else hidden.add(ref)
            prefs.hiddenBouquetRefs = hidden
        }
        rvBouquets.layoutManager = LinearLayoutManager(requireContext())
        rvBouquets.adapter = adapter

        loadBouquets()
    }

    private fun loadBouquets() {
        loadingIndicator.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val bouquets = repository.getBouquets()
                loadingIndicator.visibility = View.GONE
                if (bouquets.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    adapter.submitList(bouquets)
                }
            } catch (e: Exception) {
                loadingIndicator.visibility = View.GONE
                tvEmpty.text = getString(R.string.no_bouquets_available)
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }
}

internal class BouquetToggleAdapter(
    private val hiddenRefs: MutableSet<String>,
    private val onToggle: (ref: String, isVisible: Boolean) -> Unit
) : RecyclerView.Adapter<BouquetToggleAdapter.ViewHolder>() {

    private val items = mutableListOf<Bouquet>()

    fun submitList(list: List<Bouquet>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_bouquet_toggle_name)
        val cbVisible: CheckBox = view.findViewById(R.id.cb_bouquet_visible)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bouquet_toggle, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bouquet = items[position]
        holder.tvName.text = bouquet.name
        // Detach listener before setting state to avoid recursive triggers
        holder.cbVisible.setOnCheckedChangeListener(null)
        holder.cbVisible.isChecked = bouquet.ref !in hiddenRefs
        holder.cbVisible.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) hiddenRefs.remove(bouquet.ref) else hiddenRefs.add(bouquet.ref)
            onToggle(bouquet.ref, isChecked)
        }
        // Clicking the whole row toggles the checkbox
        holder.itemView.setOnClickListener { holder.cbVisible.toggle() }
    }

    override fun getItemCount() = items.size
}
