package com.enigma2.firetv.ui.settings.receiver

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.launch

/** Phase 1.5.6 — Wake-on-LAN setup. */
class WolSetupFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_wol_title

    private lateinit var swEnabled: Switch
    private lateinit var swStandby: Switch
    private lateinit var etLocation: EditText

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        swEnabled = Switch(ctx).apply { setText(R.string.rs_wol_enable);    setTextColor(Color.WHITE) }
        swStandby = Switch(ctx).apply { setText(R.string.rs_wol_standby);   setTextColor(Color.WHITE) }
        val locLbl = TextView(ctx).apply { setText(R.string.rs_wol_location_lbl); setTextColor(Color.WHITE); textSize = 14f }
        etLocation = EditText(ctx).apply {
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); hint = "/media/hdd"
        }
        bodyView.addView(swEnabled)
        bodyView.addView(swStandby)
        bodyView.addView(locLbl)
        bodyView.addView(etLocation)

        addActionButton(R.string.rs_action_save) { save() }
        addActionButton(R.string.rs_action_refresh) { refresh() }
        refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        val w = repo.getWolSetup()
        if (w == null) { setStatus(getString(R.string.rs_unavailable)); return@launch }
        swEnabled.isChecked = w.enabled
        swStandby.isChecked = w.wolStandby
        etLocation.setText(w.location ?: "")
        setStatus("")
    }

    private fun save(): kotlinx.coroutines.Job = lifecycleScope.launch {
        val params = mapOf(
            "wol"        to if (swEnabled.isChecked) "True" else "False",
            "wolstandby" to if (swStandby.isChecked) "True" else "False",
            "location"   to etLocation.text.toString()
        )
        val ok = repo.setWolSetup(params)
        toastOkOrFail(ok)
    }
}
