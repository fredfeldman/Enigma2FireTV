package com.enigma2.firetv.ui.bouqueteditor

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.util.ApiErrors
import kotlinx.coroutines.launch

/**
 * Lists every bouquet on the receiver, showing a User/Provider badge and
 * a banner indicating whether server-side editing is available (BouquetEditor
 * plugin installed) or the app is in local-only fallback mode.
 *
 * Tap **Edit** on a row to open [BouquetEditFragment] for the channels inside.
 * Tap **Rename** or **Delete** to mutate the bouquet itself (server only;
 * disabled for provider bouquets and entirely in local mode).
 */
class BouquetEditorFragment : Fragment() {

    private lateinit var rv: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var empty: TextView
    private lateinit var banner: TextView
    private lateinit var btnNew: TextView
    private lateinit var btnRefresh: TextView

    private val repo by lazy { Enigma2Repository(requireContext()) }
    private val prefs by lazy { ReceiverPreferences(requireContext()) }

    private var capability: Enigma2Repository.BouquetEditorCapability =
        Enigma2Repository.BouquetEditorCapability.Missing
    private var userBouquetRefs: Set<String> = emptySet()
    private var allBouquets: List<Bouquet> = emptyList()

    private val adapter = BouquetEditorAdapter(
        onEdit = { openEditor(it) },
        onRename = { promptRename(it) },
        onDelete = { promptDelete(it) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bouquet_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rv = view.findViewById(R.id.rv_be_bouquets)
        loading = view.findViewById(R.id.be_loading)
        empty = view.findViewById(R.id.tv_be_empty)
        banner = view.findViewById(R.id.tv_be_banner)
        btnNew = view.findViewById(R.id.btn_be_new)
        btnRefresh = view.findViewById(R.id.btn_be_refresh)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        btnNew.setOnClickListener { promptNew() }
        btnRefresh.setOnClickListener { load() }

        load()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from BouquetEditFragment: refresh in case channels changed.
        if (allBouquets.isNotEmpty()) load()
    }

    private fun load() {
        loading.visibility = View.VISIBLE
        empty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            capability = repo.probeBouquetEditor()
            val all = repo.getBouquets()
            val userBouquets = if (capability == Enigma2Repository.BouquetEditorCapability.Available)
                repo.getUserBouquets() else emptyList()
            userBouquetRefs = userBouquets.map { it.ref }.toSet()
            allBouquets = all

            loading.visibility = View.GONE
            empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
            applyBanner()
            adapter.submit(all, userBouquetRefs, capability)
            btnNew.visibility =
                if (capability == Enigma2Repository.BouquetEditorCapability.Available) View.VISIBLE else View.GONE
        }
    }

    private fun applyBanner() {
        val isServer = capability == Enigma2Repository.BouquetEditorCapability.Available
        banner.visibility = View.VISIBLE
        banner.text = getString(
            if (isServer) R.string.bouquet_editor_banner_server
            else R.string.bouquet_editor_banner_local
        )
        banner.setBackgroundColor(
            if (isServer) 0xFF2E7D32.toInt() // green
            else 0xFFB07300.toInt() // amber
        )
    }

    private fun openEditor(bouquet: Bouquet) {
        // Decision 3: if entering server mode for a bouquet that already has a local
        // override, prompt before continuing. Default action is Discard.
        val hasOverride = prefs.getBouquetOverride(bouquet.ref) != null
        val isServer = capability == Enigma2Repository.BouquetEditorCapability.Available
        val isUserBouquet = bouquet.ref in userBouquetRefs
        if (isServer && isUserBouquet && hasOverride) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.bouquet_local_conflict_title)
                .setMessage(getString(R.string.bouquet_local_conflict_message, bouquet.name))
                .setPositiveButton(R.string.bouquet_local_conflict_discard) { _, _ ->
                    prefs.clearBouquetOverride(bouquet.ref)
                    pushEditor(bouquet)
                }
                .setNeutralButton(R.string.bouquet_local_conflict_keep) { _, _ ->
                    pushEditor(bouquet)
                }
                .setNegativeButton(R.string.bouquet_local_conflict_apply) { _, _ ->
                    applyLocalToServer(bouquet)
                }
                .show()
            return
        }
        pushEditor(bouquet)
    }

    private fun pushEditor(bouquet: Bouquet) {
        val isUserBouquet = bouquet.ref in userBouquetRefs
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.bouquet_editor_container,
                BouquetEditFragment.newInstance(
                    bouquetRef = bouquet.ref,
                    bouquetName = bouquet.name,
                    isUserBouquet = isUserBouquet,
                    serverMode = capability == Enigma2Repository.BouquetEditorCapability.Available
                )
            )
            .addToBackStack(null)
            .commit()
    }

    /**
     * Pushes any local override for [bouquet] up to the receiver, then clears it.
     * Reorders by calling moveservice for each entry; removes by removeservice.
     * After this completes the receiver is the source of truth again.
     */
    private fun applyLocalToServer(bouquet: Bouquet) {
        val override = prefs.getBouquetOverride(bouquet.ref) ?: run {
            pushEditor(bouquet); return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Removes first so positions for moves are stable.
                for (ref in override.removed) {
                    repo.removeServiceFromBouquet(bouquet.ref, ref)
                }
                override.order.forEachIndexed { idx, ref ->
                    repo.moveServiceInBouquet(bouquet.ref, ref, idx)
                }
                prefs.clearBouquetOverride(bouquet.ref)
                Toast.makeText(requireContext(),
                    getString(R.string.bouquet_edit_overrides_cleared, bouquet.name),
                    Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    getString(R.string.bouquet_editor_op_failed, ApiErrors.userMessage(requireContext(), e)),
                    Toast.LENGTH_LONG).show()
            }
            pushEditor(bouquet)
        }
    }

    private fun promptNew() {
        val view = layoutInflater.inflate(R.layout.dialog_new_bouquet, null)
        val etName = view.findViewById<EditText>(R.id.et_new_bouquet_name)
        val rg = view.findViewById<RadioGroup>(R.id.rg_new_bouquet_mode)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.bouquet_new_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val mode = if (rg.checkedRadioButtonId == R.id.rb_mode_radio)
                    Enigma2Repository.MODE_RADIO else Enigma2Repository.MODE_TV
                runOp { repo.addBouquet(name, mode) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptRename(bouquet: Bouquet) {
        val et = EditText(requireContext()).apply { setText(bouquet.name); setSingleLine() }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.bouquet_rename_title)
            .setView(et)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty() && name != bouquet.name)
                    runOp { repo.renameBouquet(bouquet.ref, name) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptDelete(bouquet: Bouquet) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.bouquet_delete_title)
            .setMessage(getString(R.string.bouquet_delete_message, bouquet.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                runOp {
                    val r = repo.removeBouquet(bouquet.ref)
                    // Keep prefs consistent: drop from hidden + clear local override.
                    prefs.hiddenBouquetRefs = prefs.hiddenBouquetRefs - bouquet.ref
                    prefs.clearBouquetOverride(bouquet.ref)
                    r
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Runs a server mutation, toasts the result, and refreshes the list. */
    private fun runOp(block: suspend () -> com.enigma2.firetv.data.api.AutoTimerXml.SimpleResult) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = block()
                Toast.makeText(requireContext(),
                    if (r.ok) getString(R.string.bouquet_editor_op_ok)
                    else getString(R.string.bouquet_editor_op_failed, r.message ?: ""),
                    Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    getString(R.string.bouquet_editor_op_failed, ApiErrors.userMessage(requireContext(), e)),
                    Toast.LENGTH_LONG).show()
            }
            // Mark main channels screen dirty so it refreshes next resume.
            (requireActivity().application as? Any)?.let { /* noop */ }
            BouquetEditorEvents.markDirty()
            load()
        }
    }
}
