package com.enigma2.firetv.ui.settings.receiver

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Switch
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.launch

/**
 * Phase 1.5.8 — OpenWebif Web UI options.
 *
 * The receiver does not expose a list endpoint for these specific toggles —
 * we render a hard-coded checklist matching the OpenWebif keys, and post any
 * changes through `setWebConfig`. The current values are pre-populated from
 * `getAllSettings()` when the matching keys are present.
 */
class OpenWebifUiFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_webui_title

    private val keys = listOf(
        "session.timeout"  to R.string.rs_webui_session_timeout,
        "kioskmode"        to R.string.rs_webui_kiosk,
        "autorefresh"      to R.string.rs_webui_autorefresh,
        "hideadult"        to R.string.rs_webui_hide_adult,
        "showpicons"       to R.string.rs_webui_picons,
        "showname"         to R.string.rs_webui_show_name
    )
    private val switches = LinkedHashMap<String, Switch>()

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        keys.forEach { (k, label) ->
            val sw = Switch(requireContext()).apply {
                setText(label); setTextColor(Color.WHITE)
            }
            switches[k] = sw
            bodyView.addView(sw)
        }
        addActionButton(R.string.rs_action_save) { save() }
        addActionButton(R.string.rs_action_refresh) { refresh() }
        refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        val all = repo.getAllSettings()
        switches.forEach { (k, sw) ->
            sw.isChecked = all[k]?.equals("True", ignoreCase = true) == true
        }
        setStatus("")
    }

    private fun save(): kotlinx.coroutines.Job = lifecycleScope.launch {
        val params = switches.mapValues { if (it.value.isChecked) "True" else "False" }
        val ok = repo.setWebConfig(params)
        toastOkOrFail(ok)
    }
}
