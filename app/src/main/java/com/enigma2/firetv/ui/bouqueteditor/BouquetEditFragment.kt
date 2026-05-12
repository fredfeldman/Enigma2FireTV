package com.enigma2.firetv.ui.bouqueteditor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.util.ApiErrors
import kotlinx.coroutines.launch

/**
 * Edits the channels inside a single bouquet. Two operating modes:
 *
 *  * **Server mode** (BouquetEditor plugin available *and* this is a user
 *    bouquet): every reorder/add/remove triggers an HTTP call to the receiver.
 *    On failure we revert the in-memory list and surface a toast.
 *  * **Local mode** (plugin missing, *or* this is a provider bouquet, *or*
 *    this is a user bouquet that the user chose to keep as a local layer):
 *    edits are stored as a per-device [ReceiverPreferences.BouquetOverride]
 *    and applied transparently by [Enigma2Repository.getChannels].
 *
 * Local mode does not support **add channel** (you cannot add a channel to a
 * bouquet on disk on the box without the plugin) — the button is hidden.
 */
class BouquetEditFragment : Fragment() {

    private lateinit var rv: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var empty: TextView
    private lateinit var banner: TextView
    private lateinit var btnAdd: TextView
    private lateinit var tvTitle: TextView

    /** Bypasses the local override layer so server data is shown raw. */
    private val repo by lazy { Enigma2Repository() }
    /** Used only for reading/writing the local override map. */
    private val prefs by lazy { ReceiverPreferences(requireContext()) }

    private lateinit var bouquetRef: String
    private lateinit var bouquetName: String
    private var isUserBouquet: Boolean = false
    private var serverMode: Boolean = false

    /** Authoritative server-side list (used for revert + add-channel diffing). */
    private var serverList: List<Service> = emptyList()
    /** What the adapter is currently showing. */
    private val display = mutableListOf<Service>()

    private lateinit var adapter: BouquetEditChannelAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bouquet_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bouquetRef = arguments?.getString(ARG_REF).orEmpty()
        bouquetName = arguments?.getString(ARG_NAME).orEmpty()
        isUserBouquet = arguments?.getBoolean(ARG_IS_USER, false) ?: false
        serverMode = arguments?.getBoolean(ARG_SERVER_MODE, false) ?: false

        tvTitle = view.findViewById(R.id.tv_bedit_title)
        rv = view.findViewById(R.id.rv_bedit_channels)
        loading = view.findViewById(R.id.bedit_loading)
        empty = view.findViewById(R.id.tv_bedit_empty)
        banner = view.findViewById(R.id.tv_bedit_banner)
        btnAdd = view.findViewById(R.id.btn_bedit_add)

        tvTitle.text = bouquetName

        adapter = BouquetEditChannelAdapter(
            onMoveUp = { idx -> moveItem(idx, idx - 1) },
            onMoveDown = { idx -> moveItem(idx, idx + 1) },
            onRemove = { idx -> removeAt(idx) }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Drag-to-reorder via long-press, mirroring PlaylistDetailFragment.
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            private var dragFromTo: Pair<Int, Int>? = null

            override fun isLongPressDragEnabled(): Boolean = true

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val item = display.removeAt(from)
                display.add(to, item)
                adapter.notifyItemMoved(from, to)
                dragFromTo = (dragFromTo?.first ?: from) to to
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(rvLocal: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rvLocal, viewHolder)
                val drag = dragFromTo
                dragFromTo = null
                if (drag != null && drag.first != drag.second) {
                    persistMove(drag.first, drag.second)
                }
            }
        }).attachToRecyclerView(rv)

        applyBanner()
        btnAdd.visibility = if (serverMode && isUserBouquet) View.VISIBLE else View.GONE
        btnAdd.setOnClickListener { openPicker() }

        load()
    }

    override fun onResume() {
        super.onResume()
        // If the picker added a channel, it sets dirty; reload to pick it up.
        if (BouquetEditorEvents.consumeDirty()) {
            load()
        }
    }

    private fun applyBanner() {
        banner.visibility = View.VISIBLE
        if (serverMode && isUserBouquet) {
            banner.setText(R.string.bouquet_editor_banner_server)
            banner.setBackgroundColor(0xFF2E7D32.toInt())
        } else {
            banner.setText(R.string.bouquet_editor_banner_local)
            banner.setBackgroundColor(0xFFB07300.toInt())
        }
    }

    private fun load() {
        loading.visibility = View.VISIBLE
        empty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                serverList = repo.getChannels(bouquetRef)
            } catch (e: Exception) {
                serverList = emptyList()
                Toast.makeText(requireContext(),
                    ApiErrors.userMessage(requireContext(), e), Toast.LENGTH_LONG).show()
            }
            display.clear()
            if (serverMode && isUserBouquet) {
                display.addAll(serverList)
            } else {
                val ov = prefs.getBouquetOverride(bouquetRef)
                display.addAll(if (ov == null) serverList else repo.applyOverride(serverList, ov))
            }
            loading.visibility = View.GONE
            empty.visibility = if (display.isEmpty()) View.VISIBLE else View.GONE
            adapter.submit(display)
        }
    }

    // ── Edit operations ──────────────────────────────────────────────────

    private fun moveItem(from: Int, to: Int) {
        if (to < 0 || to >= display.size || from == to) return
        val item = display.removeAt(from)
        display.add(to, item)
        adapter.notifyItemMoved(from, to)
        persistMove(from, to)
    }

    private fun removeAt(idx: Int) {
        if (idx !in display.indices) return
        val item = display[idx]
        display.removeAt(idx)
        adapter.notifyItemRemoved(idx)
        if (display.isEmpty()) empty.visibility = View.VISIBLE
        if (serverMode && isUserBouquet) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val r = repo.removeServiceFromBouquet(bouquetRef, item.ref)
                    if (!r.ok) revertWithError(r.message)
                    else {
                        BouquetEditorEvents.markDirty()
                        Toast.makeText(requireContext(),
                            R.string.bouquet_editor_op_ok, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    revertWithError(ApiErrors.userMessage(requireContext(), e))
                }
            }
        } else {
            persistLocalOverride()
        }
    }

    /** Server: call moveservice for the moved item. Local: rewrite the override. */
    private fun persistMove(from: Int, to: Int) {
        if (serverMode && isUserBouquet) {
            val item = display[to]
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val r = repo.moveServiceInBouquet(bouquetRef, item.ref, to)
                    if (!r.ok) {
                        revertWithError(r.message)
                    } else {
                        BouquetEditorEvents.markDirty()
                    }
                } catch (e: Exception) {
                    revertWithError(ApiErrors.userMessage(requireContext(), e))
                }
            }
        } else {
            persistLocalOverride()
        }
    }

    private fun persistLocalOverride() {
        val displayedRefs = display.map { it.ref }
        val serverRefs = serverList.map { it.ref }.toSet()
        val removedRefs = serverRefs - displayedRefs.toSet()
        val ov = ReceiverPreferences.BouquetOverride(
            order = displayedRefs,
            removed = removedRefs.toList()
        )
        prefs.setBouquetOverride(bouquetRef, ov)
        BouquetEditorEvents.markDirty()
    }

    private fun revertWithError(msg: String?) {
        Toast.makeText(requireContext(),
            getString(R.string.bouquet_editor_op_failed, msg ?: ""),
            Toast.LENGTH_LONG).show()
        // Reload from server to get back to known state.
        load()
    }

    private fun openPicker() {
        val existingRefs = display.map { it.ref }.toHashSet()
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.bouquet_editor_container,
                AddServicePickerFragment.newInstance(bouquetRef, ArrayList(existingRefs))
            )
            .addToBackStack(null)
            .commit()
    }

    companion object {
        private const val ARG_REF = "ref"
        private const val ARG_NAME = "name"
        private const val ARG_IS_USER = "is_user"
        private const val ARG_SERVER_MODE = "server_mode"

        fun newInstance(
            bouquetRef: String,
            bouquetName: String,
            isUserBouquet: Boolean,
            serverMode: Boolean
        ): BouquetEditFragment = BouquetEditFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_REF, bouquetRef)
                putString(ARG_NAME, bouquetName)
                putBoolean(ARG_IS_USER, isUserBouquet)
                putBoolean(ARG_SERVER_MODE, serverMode)
            }
        }
    }
}
