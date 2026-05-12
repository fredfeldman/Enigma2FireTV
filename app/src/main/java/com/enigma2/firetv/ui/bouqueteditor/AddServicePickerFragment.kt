package com.enigma2.firetv.ui.bouqueteditor

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.util.ApiErrors
import kotlinx.coroutines.launch

/**
 * Flat, filterable list of every channel currently visible across all bouquets
 * on the receiver. Tapping one calls `addServiceToBouquet` and pops back to
 * [BouquetEditFragment]. Channels already present in the target bouquet are
 * skipped to avoid duplicates.
 */
class AddServicePickerFragment : Fragment() {

    private lateinit var rv: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var etFilter: EditText

    private val repo by lazy { Enigma2Repository() }

    private lateinit var bouquetRef: String
    private lateinit var existingRefs: HashSet<String>

    private val all = mutableListOf<Service>()
    private val shown = mutableListOf<Service>()

    private val adapter = PickerAdapter { pick(it) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_add_service_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bouquetRef = arguments?.getString(ARG_REF).orEmpty()
        existingRefs = HashSet(arguments?.getStringArrayList(ARG_EXISTING) ?: arrayListOf())

        rv = view.findViewById(R.id.rv_picker)
        loading = view.findViewById(R.id.picker_loading)
        etFilter = view.findViewById(R.id.et_picker_filter)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        load()
    }

    private fun load() {
        loading.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bouquets = repo.getBouquets()
                // Flatten + dedupe by ref. Skip channels already in target bouquet.
                val seen = HashSet<String>()
                val flat = mutableListOf<Service>()
                for (b in bouquets) {
                    for (s in b.channels.orEmpty()) {
                        if (s.ref in existingRefs) continue
                        if (seen.add(s.ref)) flat.add(s)
                    }
                }
                all.clear(); all.addAll(flat)
                applyFilter(etFilter.text.toString())
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    ApiErrors.userMessage(requireContext(), e), Toast.LENGTH_LONG).show()
            } finally {
                loading.visibility = View.GONE
            }
        }
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        shown.clear()
        if (q.isEmpty()) shown.addAll(all)
        else all.filterTo(shown) { it.name.lowercase().contains(q) }
        adapter.submit(shown)
    }

    private fun pick(service: Service) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = repo.addServiceToBouquet(bouquetRef, service)
                if (r.ok) {
                    BouquetEditorEvents.markDirty()
                    Toast.makeText(requireContext(),
                        R.string.bouquet_editor_op_ok, Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                } else {
                    Toast.makeText(requireContext(),
                        getString(R.string.bouquet_editor_op_failed, r.message ?: ""),
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    getString(R.string.bouquet_editor_op_failed,
                        ApiErrors.userMessage(requireContext(), e)),
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val ARG_REF = "ref"
        private const val ARG_EXISTING = "existing"

        fun newInstance(bouquetRef: String, existingRefs: ArrayList<String>): AddServicePickerFragment =
            AddServicePickerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_REF, bouquetRef)
                    putStringArrayList(ARG_EXISTING, existingRefs)
                }
            }
    }
}

private class PickerAdapter(
    private val onClick: (Service) -> Unit
) : RecyclerView.Adapter<PickerAdapter.VH>() {
    private val items = mutableListOf<Service>()

    fun submit(list: List<Service>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_picker_service, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.name.text = s.name
        holder.sub.text = s.ref
        holder.itemView.setOnClickListener { onClick(s) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: android.widget.TextView = view.findViewById(R.id.tv_pick_name)
        val sub: android.widget.TextView = view.findViewById(R.id.tv_pick_sub)
    }
}
