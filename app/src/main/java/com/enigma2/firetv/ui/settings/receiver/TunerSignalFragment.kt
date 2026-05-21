package com.enigma2.firetv.ui.settings.receiver

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Phase 1.5.4 — Tuner / Signal (auto-refresh every 2s while visible). */
class TunerSignalFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_tuner_title

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        addActionButton(R.string.rs_action_refresh) { /* loop auto-refreshes */ }
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val sig = repo.getTunerSignal()
                if (sig == null) {
                    setStatus(getString(R.string.rs_unavailable))
                } else {
                    val sb = StringBuilder()
                    sig.tunerNumber?.let { sb.append("Tuner #").append(it) }
                    sig.tunerType?.let { sb.append("  ").append(it) }
                    sb.append('\n')
                    sig.snr?.let     { sb.append("SNR: ").append(it).append('\n') }
                    sig.ber?.let     { sb.append("BER: ").append(it).append('\n') }
                    sig.signal?.let  { sb.append("Signal: ").append(it).append('\n') }
                    setStatus(sb.toString().trimEnd())
                }
                delay(2000)
            }
        }
    }
}
