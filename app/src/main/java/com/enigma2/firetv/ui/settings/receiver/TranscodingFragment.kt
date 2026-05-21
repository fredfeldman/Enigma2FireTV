package com.enigma2.firetv.ui.settings.receiver

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.launch

/** Phase 1.5.7 — Transcoding (dynamic key/value editor). */
class TranscodingFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_transcoding_title

    private val editors = LinkedHashMap<String, EditText>()

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        addActionButton(R.string.rs_action_save) { save() }
        addActionButton(R.string.rs_action_refresh) { refresh() }
        refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        bodyView.removeAllViews()
        editors.clear()
        val cfg = repo.getTranscodingConfig()
        if (cfg.isEmpty()) {
            setStatus(getString(R.string.rs_unavailable))
            return@launch
        }
        setStatus("")
        cfg.forEach { (k, v) ->
            val label = TextView(requireContext()).apply {
                text = k; setTextColor(Color.WHITE); textSize = 13f
            }
            bodyView.addView(label)
            val ed = EditText(requireContext()).apply {
                setText(v); setTextColor(Color.WHITE)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            bodyView.addView(ed, lp)
            editors[k] = ed
        }
    }

    private fun save(): kotlinx.coroutines.Job = lifecycleScope.launch {
        val params = editors.mapValues { it.value.text.toString() }
        val ok = repo.setTranscodingConfig(params)
        toastOkOrFail(ok)
    }
}
