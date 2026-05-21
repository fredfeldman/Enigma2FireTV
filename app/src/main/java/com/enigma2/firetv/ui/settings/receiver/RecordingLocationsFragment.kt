package com.enigma2.firetv.ui.settings.receiver

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.launch

/** Phase 1.5.3 — Recording locations. */
class RecordingLocationsFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_recloc_title

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        addActionButton(R.string.rs_recloc_btn_add) { promptAdd() }
        addActionButton(R.string.rs_action_refresh) { refresh() }
        refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        bodyView.removeAllViews()
        val data = repo.getRecordingLocations()
        if (data == null) {
            setStatus(getString(R.string.rs_unavailable))
            return@launch
        }
        setStatus(getString(R.string.rs_recloc_current_fmt, data.current ?: "-"))
        data.locations.forEach { loc ->
            val isCurrent = loc == data.current
            val rowView = row()
            val name = TextView(requireContext())
            name.text = (if (isCurrent) "★ " else "") + loc
            name.setTextColor(if (isCurrent) Color.YELLOW else Color.WHITE)
            name.textSize = 14f
            rowView.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val setBtn = Button(requireContext()).apply {
                setText(R.string.rs_recloc_btn_set_default)
                setBackgroundResource(R.drawable.bg_button)
                setTextColor(Color.WHITE)
                isEnabled = !isCurrent
                setOnClickListener {
                    lifecycleScope.launch {
                        val ok = repo.setCurrentLocation(loc)
                        toastOkOrFail(ok)
                        if (ok) refresh()
                    }
                }
            }
            rowView.addView(setBtn)
            val rmBtn = Button(requireContext()).apply {
                setText(R.string.rs_recloc_btn_remove)
                setBackgroundResource(R.drawable.bg_button)
                setTextColor(Color.WHITE)
                setOnClickListener { confirmRemove(loc) }
            }
            rowView.addView(rmBtn)
            rowView.gravity = Gravity.CENTER_VERTICAL
            bodyView.addView(rowView)
        }
    }

    private fun confirmRemove(loc: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rs_recloc_remove_title)
            .setMessage(getString(R.string.rs_recloc_remove_msg, loc))
            .setPositiveButton(R.string.rs_action_remove) { _, _ ->
                lifecycleScope.launch {
                    val ok = repo.removeLocation(loc)
                    toastOkOrFail(ok)
                    if (ok) refresh()
                }
            }
            .setNegativeButton(R.string.rs_action_cancel, null)
            .show()
    }

    private fun promptAdd() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.rs_recloc_add_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rs_recloc_add_title)
            .setView(input)
            .setPositiveButton(R.string.rs_action_add) { _, _ ->
                val path = input.text.toString().trim()
                if (path.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val ok = repo.addLocation(path)
                    toastOkOrFail(ok)
                    if (ok) refresh()
                }
            }
            .setNegativeButton(R.string.rs_action_cancel, null)
            .show()
    }
}
