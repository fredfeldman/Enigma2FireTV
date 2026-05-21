package com.enigma2.firetv.ui.settings.receiver

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import kotlinx.coroutines.launch

/**
 * Phase 1.5.1 — Power & sleep.
 *
 * Power state codes (OpenWebif `/api/powerstate?newstate=N`):
 *  0 = toggle standby   5 = standby on
 *  1 = deep standby     2 = reboot
 *  3 = restart GUI      4 = wake up
 */
class PowerFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_power_title

    private lateinit var sleepMin: EditText
    private lateinit var sleepAction: Spinner
    private lateinit var sleepEnabled: Switch

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        addActionButton(R.string.rs_power_btn_standby)     { toggle(0) }
        addActionButton(R.string.rs_power_btn_wake)        { toggle(4) }
        addActionButton(R.string.rs_power_btn_deepstandby) { toggle(1) }
        addActionButton(R.string.rs_power_btn_reboot)      { toggle(2) }
        addActionButton(R.string.rs_power_btn_restart_gui) { toggle(3) }

        buildSleepTimerSection()
        refresh()
    }

    private fun toggle(code: Int) = lifecycleScope.launch {
        val ok = repo.setPowerState(code)
        toastOkOrFail(ok)
        if (ok) refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        val st = repo.getPowerState()
        val tm = repo.getSleepTimer()
        val sb = StringBuilder()
        sb.append(getString(R.string.rs_power_status_label))
            .append(' ')
            .append(if (st?.inStandby == true) getString(R.string.rs_power_state_standby)
                    else getString(R.string.rs_power_state_on))
        setStatus(sb.toString())
        if (tm != null) {
            sleepMin.setText(tm.minutes.toString())
            sleepAction.setSelection(if (tm.action.equals("shutdown", true)) 1 else 0)
            sleepEnabled.isChecked = tm.enabled
        }
    }

    private fun buildSleepTimerSection() {
        val ctx = requireContext()
        val header = TextView(ctx).apply {
            setText(R.string.rs_power_sleep_header)
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        bodyView.addView(header)

        sleepMin = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.rs_power_sleep_minutes_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        bodyView.addView(sleepMin, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        sleepAction = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx, android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.rs_power_sleep_action_standby),
                       getString(R.string.rs_power_sleep_action_shutdown))
            )
        }
        bodyView.addView(sleepAction)

        sleepEnabled = Switch(ctx).apply {
            setText(R.string.rs_power_sleep_enable)
            setTextColor(Color.WHITE)
        }
        bodyView.addView(sleepEnabled)

        val btnRow = row()
        val save = android.widget.Button(ctx).apply {
            setText(R.string.rs_power_sleep_save)
            setBackgroundResource(R.drawable.bg_button)
            setTextColor(Color.WHITE)
            setOnClickListener {
                val minutes = sleepMin.text.toString().toIntOrNull() ?: 0
                val action = if (sleepAction.selectedItemPosition == 1) "shutdown" else "standby"
                lifecycleScope.launch {
                    val ok = repo.setSleepTimer(minutes, action, sleepEnabled.isChecked)
                    toastOkOrFail(ok)
                }
            }
        }
        btnRow.addView(save)
        bodyView.addView(btnRow)
    }
}
