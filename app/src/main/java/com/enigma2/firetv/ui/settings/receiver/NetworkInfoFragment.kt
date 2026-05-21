package com.enigma2.firetv.ui.settings.receiver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.launch

/** Phase 1.5.13 — Network info (read-only dump + copy). */
class NetworkInfoFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_network_title

    private var raw: String = ""

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        addActionButton(R.string.rs_action_refresh) { refresh() }
        addActionButton(R.string.rs_network_copy) { copy() }
        refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        bodyView.removeAllViews()
        setStatus(getString(R.string.rs_storage_loading))
        raw = repo.getNetworkInfo() ?: ""
        setStatus("")
        bodyView.addView(makeBodyText(
            if (raw.isBlank()) getString(R.string.rs_unavailable) else raw,
            mono = true
        ))
    }

    private fun copy() {
        if (raw.isBlank()) { toastRes(R.string.rs_unavailable); return }
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Enigma2 network", raw))
        toastRes(R.string.rs_network_copied)
    }
}
