package com.enigma2.firetv.ui.settings.receiver

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.settings.ConfigItem
import com.enigma2.firetv.data.model.settings.ConfigItemType
import kotlinx.coroutines.launch

/**
 * Phase 1.5.9 — All settings (advanced).
 *
 * Step 1: lists every section returned by `/api/configsections`.
 * Step 2 (after a section is tapped): renders each [ConfigItem] using a widget
 * appropriate to its [ConfigItemType] and writes changes back via
 * `saveConfig(key, value)`.
 */
class AllSettingsFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_allsettings_title

    private var currentSection: String? = null

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        addActionButton(R.string.rs_action_refresh) {
            if (currentSection == null) loadSections() else loadSection(currentSection!!)
        }
        loadSections()
    }

    private fun loadSections(): kotlinx.coroutines.Job = lifecycleScope.launch {
        currentSection = null
        bodyView.removeAllViews()
        setStatus(getString(R.string.rs_allsettings_pick_section))
        val sections = repo.getConfigSections()
        if (sections.isEmpty()) {
            setStatus(getString(R.string.rs_unavailable))
            return@launch
        }
        sections.forEach { name ->
            val btn = Button(requireContext()).apply {
                text = name
                setBackgroundResource(R.drawable.bg_button)
                setTextColor(Color.WHITE)
                setOnClickListener { loadSection(name) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 8
            bodyView.addView(btn, lp)
        }
    }

    private fun loadSection(name: String): kotlinx.coroutines.Job = lifecycleScope.launch {
        currentSection = name
        bodyView.removeAllViews()
        setStatus(name)
        val sec = repo.getConfigSection(name)
        if (sec.items.isEmpty()) {
            bodyView.addView(makeBodyText(getString(R.string.rs_allsettings_empty)))
            return@launch
        }
        sec.items.forEach { renderItem(it) }
    }

    private fun renderItem(item: ConfigItem) {
        val ctx = requireContext()
        val label = TextView(ctx).apply {
            text = item.description.ifBlank { item.path }
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        bodyView.addView(label)

        when (item.type) {
            ConfigItemType.Bool -> {
                val sw = Switch(ctx).apply {
                    setTextColor(Color.WHITE)
                    isChecked = item.value.equals("True", ignoreCase = true) || item.value == "1"
                    setOnCheckedChangeListener { _, isChecked ->
                        commit(item.path, if (isChecked) "True" else "False")
                    }
                }
                bodyView.addView(sw)
            }
            ConfigItemType.Choice -> {
                val sp = Spinner(ctx)
                val labels = item.choices.map { it.second }
                sp.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, labels)
                val curIdx = item.choices.indexOfFirst { it.first == item.value }
                if (curIdx >= 0) sp.setSelection(curIdx)
                sp.onItemSelectedListener = object :
                    android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        val newVal = item.choices.getOrNull(pos)?.first ?: return
                        if (newVal != item.value) commit(item.path, newVal)
                    }
                    override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
                }
                bodyView.addView(sp)
            }
            ConfigItemType.Password -> {
                renderEdit(item) { it.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
            }
            ConfigItemType.Int, ConfigItemType.Float, ConfigItemType.Slider -> {
                renderEdit(item) { it.inputType = InputType.TYPE_CLASS_NUMBER }
            }
            else -> renderEdit(item) {}
        }
    }

    private fun renderEdit(item: ConfigItem, init: (EditText) -> Unit) {
        val ed = EditText(requireContext()).apply {
            setText(item.value); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            init(this)
        }
        bodyView.addView(ed)
        val btn = Button(requireContext()).apply {
            setText(R.string.rs_action_save)
            setBackgroundResource(R.drawable.bg_button)
            setTextColor(Color.WHITE)
            setOnClickListener { commit(item.path, ed.text.toString()) }
        }
        bodyView.addView(btn)
    }

    private fun commit(key: String, value: String) = lifecycleScope.launch {
        val (ok, msg) = repo.saveConfig(key, value)
        if (ok) toastRes(R.string.rs_action_ok)
        else AlertDialog.Builder(requireContext())
            .setTitle(R.string.rs_action_failed)
            .setMessage(msg ?: "")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
