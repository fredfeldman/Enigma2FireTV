package com.enigma2.firetv.ui.autotimer

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.AutoTimer
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.util.ApiErrors
import kotlinx.coroutines.launch

/**
 * Manage AutoTimer rules on the receiver: list, enable/disable, delete, add/edit,
 * and trigger an immediate "scan EPG and create timers now" pass.
 *
 * Requires the AutoTimer plugin to be installed on the Enigma2 box. If the plugin
 * isn't installed the list will simply come back empty (the underlying HTTP 404
 * is swallowed by the repository).
 */
class AutoTimerFragment : Fragment() {

    private lateinit var rv: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var empty: TextView
    private lateinit var btnAdd: TextView
    private lateinit var btnScan: TextView
    private lateinit var btnRefresh: TextView

    private val repository = Enigma2Repository()
    private val adapter = AutoTimerAdapter(
        onToggle = { confirmToggle(it) },
        onDelete = { confirmDelete(it) },
        onEdit = { showEditor(it) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_autotimer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rv_autotimers)
        loading = view.findViewById(R.id.at_loading)
        empty = view.findViewById(R.id.tv_at_empty)
        btnAdd = view.findViewById(R.id.btn_at_add)
        btnScan = view.findViewById(R.id.btn_at_scan)
        btnRefresh = view.findViewById(R.id.btn_at_refresh)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        btnAdd.setOnClickListener { showEditor(null) }
        btnScan.setOnClickListener { runScan() }
        btnRefresh.setOnClickListener { load() }

        load()
    }

    private fun load() {
        loading.visibility = View.VISIBLE
        empty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val rules = repository.getAutoTimers()
            loading.visibility = View.GONE
            empty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(rules)
        }
    }

    private fun confirmToggle(rule: AutoTimer) {
        val updated = rule.copy(enabled = !rule.enabled)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = repository.saveAutoTimer(updated)
                if (!r.ok) toast(getString(R.string.autotimer_save_failed, r.message ?: ""))
            } catch (e: Exception) {
                toast(getString(R.string.autotimer_save_failed, ApiErrors.userMessage(requireContext(), e)))
            }
            load()
        }
    }

    private fun confirmDelete(rule: AutoTimer) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.autotimer_delete_title)
            .setMessage(getString(R.string.autotimer_delete_message, rule.name))
            .setPositiveButton(R.string.delete) { _, _ -> doDelete(rule) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doDelete(rule: AutoTimer) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = repository.removeAutoTimer(rule.id)
                if (!r.ok) toast(getString(R.string.autotimer_save_failed, r.message ?: ""))
            } catch (e: Exception) {
                toast(getString(R.string.autotimer_save_failed, ApiErrors.userMessage(requireContext(), e)))
            }
            load()
        }
    }

    private fun runScan() {
        toast(getString(R.string.autotimer_scan_started))
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = repository.parseAutoTimers()
                toast(if (r.ok) getString(R.string.autotimer_scan_done)
                      else getString(R.string.autotimer_save_failed, r.message ?: ""))
            } catch (e: Exception) {
                toast(getString(R.string.autotimer_save_failed, ApiErrors.userMessage(requireContext(), e)))
            }
        }
    }

    private fun showEditor(existing: AutoTimer?) {
        val view = layoutInflater.inflate(R.layout.dialog_autotimer_edit, null)
        val etName = view.findViewById<EditText>(R.id.et_at_name)
        val etMatch = view.findViewById<EditText>(R.id.et_at_match)
        val cbEnabled = view.findViewById<CheckBox>(R.id.cb_at_enabled)
        val etAfter = view.findViewById<EditText>(R.id.et_at_after)
        val etBefore = view.findViewById<EditText>(R.id.et_at_before)

        existing?.let {
            etName.setText(it.name)
            etMatch.setText(it.match)
            cbEnabled.isChecked = it.enabled
            etAfter.setText(it.after.orEmpty())
            etBefore.setText(it.before.orEmpty())
        }

        val titleRes = if (existing == null) R.string.autotimer_add_title else R.string.autotimer_edit_title
        AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val match = etMatch.text.toString().trim()
                if (match.isEmpty()) {
                    toast(getString(R.string.autotimer_match_required))
                    return@setPositiveButton
                }
                val name = etName.text.toString().trim().ifEmpty { match }
                val rule = (existing ?: AutoTimer(name = name, match = match)).copy(
                    name = name,
                    match = match,
                    enabled = cbEnabled.isChecked,
                    after = etAfter.text.toString().trim().ifEmpty { null },
                    before = etBefore.text.toString().trim().ifEmpty { null }
                )
                save(rule)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun save(rule: AutoTimer) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val r = repository.saveAutoTimer(rule)
                if (!r.ok) toast(getString(R.string.autotimer_save_failed, r.message ?: ""))
                else toast(getString(R.string.autotimer_saved))
            } catch (e: Exception) {
                toast(getString(R.string.autotimer_save_failed, ApiErrors.userMessage(requireContext(), e)))
            }
            load()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
