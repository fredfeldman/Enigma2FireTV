package com.enigma2.firetv.ui.settings.receiver

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.launch

/** Phase 1.5.10 — Storage & SMART (read-only dump). */
class StorageMountsFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_storage_title

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        addActionButton(R.string.rs_action_refresh) { refresh() }
        refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        bodyView.removeAllViews()
        setStatus(getString(R.string.rs_storage_loading))
        val mount = repo.getMountInfo()
        val smart = repo.getSmartInfo()
        val sb = StringBuilder()
        sb.append(getString(R.string.rs_storage_mounts_header)).append("\n\n")
        sb.append(mount ?: getString(R.string.rs_unavailable))
        sb.append("\n\n").append(getString(R.string.rs_storage_smart_header)).append("\n\n")
        sb.append(smart ?: getString(R.string.rs_unavailable))
        setStatus("")
        bodyView.addView(makeBodyText(sb.toString(), mono = true))
    }
}
