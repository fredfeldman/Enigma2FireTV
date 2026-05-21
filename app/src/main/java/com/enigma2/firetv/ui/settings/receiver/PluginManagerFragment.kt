package com.enigma2.firetv.ui.settings.receiver

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Phase 1.5.12 — Plugin manager. */
class PluginManagerFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_plugins_title

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        addActionButton(R.string.rs_plugins_install) { promptInstall() }
        addActionButton(R.string.rs_action_refresh) { refresh() }
        refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        bodyView.removeAllViews()
        setStatus(getString(R.string.rs_storage_loading))
        val raw = repo.listPlugins() ?: ""
        setStatus("")
        val plugins = parsePlugins(raw)
        if (plugins.isEmpty()) {
            bodyView.addView(makeBodyText(
                if (raw.isBlank()) getString(R.string.rs_unavailable)
                else raw, mono = true))
            return@launch
        }
        plugins.forEach { pkg ->
            val rowView = row()
            val name = TextView(requireContext())
            name.text = pkg
            name.setTextColor(Color.WHITE)
            rowView.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val rm = Button(requireContext()).apply {
                setText(R.string.rs_action_remove)
                setBackgroundResource(R.drawable.bg_button)
                setTextColor(Color.WHITE)
                setOnClickListener { confirmRemove(pkg) }
            }
            rowView.addView(rm)
            bodyView.addView(rowView)
        }
    }

    private fun parsePlugins(raw: String): List<String> {
        return runCatching {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("plugins")
                ?: obj.optJSONArray("result")
                ?: return emptyList()
            (0 until arr.length()).mapNotNull {
                when (val v = arr.opt(it)) {
                    is String -> v
                    is JSONObject -> v.optString("name")
                        .ifBlank { v.optString("pkg") }
                        .ifBlank { v.optString("package") }
                    else -> null
                }?.takeIf { s -> s.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun promptInstall() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.rs_plugins_install_hint)
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rs_plugins_install)
            .setView(input)
            .setPositiveButton(R.string.rs_action_ok) { _, _ ->
                val pkg = input.text.toString().trim()
                if (pkg.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val ok = repo.installPlugin(pkg)
                    toastOkOrFail(ok)
                    if (ok) refresh()
                }
            }
            .setNegativeButton(R.string.rs_action_cancel, null)
            .show()
    }

    private fun confirmRemove(pkg: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rs_plugins_remove_title)
            .setMessage(getString(R.string.rs_plugins_remove_msg, pkg))
            .setPositiveButton(R.string.rs_action_remove) { _, _ ->
                lifecycleScope.launch {
                    val ok = repo.removePlugin(pkg)
                    toastOkOrFail(ok)
                    if (ok) refresh()
                }
            }
            .setNegativeButton(R.string.rs_action_cancel, null)
            .show()
    }
}
