package com.enigma2.firetv.ui.settings.receiver

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.launch

/** Phase 1.5.2 — Audio & Volume. */
class AudioVolumeFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_audio_title

    private lateinit var seek: SeekBar
    private lateinit var label: TextView

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        addActionButton(R.string.rs_audio_btn_mute) {
            lifecycleScope.launch {
                val ok = repo.toggleMute()
                toastOkOrFail(ok)
                refresh()
            }
        }
        addActionButton(R.string.rs_audio_btn_minus5) { adjust(-5) }
        addActionButton(R.string.rs_audio_btn_plus5) { adjust(+5) }

        label = TextView(requireContext()).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        bodyView.addView(label)

        seek = SeekBar(requireContext()).apply { max = 100 }
        bodyView.addView(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                label.text = getString(R.string.rs_audio_level_fmt, value)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                lifecycleScope.launch { repo.setVolume(sb?.progress ?: 0) }
            }
        })

        refresh()
    }

    private fun adjust(delta: Int) = lifecycleScope.launch {
        val cur = repo.getVolume()?.current ?: seek.progress
        val next = (cur + delta).coerceIn(0, 100)
        val ok = repo.setVolume(next)
        toastOkOrFail(ok)
        refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        val v = repo.getVolume()
        if (v != null) {
            seek.progress = v.current
            label.text = getString(R.string.rs_audio_level_fmt, v.current)
            setStatus(
                if (v.muted) getString(R.string.rs_audio_status_muted)
                else getString(R.string.rs_audio_status_unmuted)
            )
        } else {
            setStatus(getString(R.string.rs_unavailable))
        }
    }
}
