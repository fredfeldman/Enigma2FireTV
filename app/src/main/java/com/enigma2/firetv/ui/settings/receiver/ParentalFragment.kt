package com.enigma2.firetv.ui.settings.receiver

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import kotlinx.coroutines.launch

/**
 * Phase 1.5.5 — Parental control.
 *
 * App-side PIN gate (§1.4): if a parental PIN hash is set in
 * [ReceiverPreferences.parentalPinHash], the user must enter it before the
 * screen contents are revealed.
 */
class ParentalFragment : ReceiverSettingsBaseFragment() {

    override val screenTitleRes = R.string.rs_parental_title

    private lateinit var prefs: ReceiverPreferences
    private var unlocked = false

    override fun onScreenReady(view: View, savedInstanceState: Bundle?) {
        prefs = ReceiverPreferences(requireContext())
        if (prefs.parentalPinHash.isNotEmpty()) {
            promptAppPin()
        } else {
            unlocked = true
            buildUi()
        }
    }

    private fun promptAppPin() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rs_parental_app_pin_prompt_title)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(R.string.rs_action_ok) { _, _ ->
                if (prefs.verifyParentalPin(input.text.toString())) {
                    unlocked = true
                    buildUi()
                } else {
                    toastRes(R.string.rs_parental_pin_wrong)
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
            .setNegativeButton(R.string.rs_action_cancel) { _, _ ->
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .show()
    }

    private fun buildUi() {
        addActionButton(R.string.rs_parental_btn_change_app_pin) { changeAppPin() }
        addActionButton(R.string.rs_parental_btn_change_setup_pin) { changeSetupPin() }
        addActionButton(R.string.rs_action_refresh) { refresh() }
        refresh()
    }

    private fun refresh(): kotlinx.coroutines.Job = lifecycleScope.launch {
        if (!unlocked) return@launch
        bodyView.removeAllViews()
        val protSettings = repo.getProtectionSettings()
        val list = repo.getParentControlList()
        setStatus(getString(
            R.string.rs_parental_status_fmt,
            if (protSettings.first) "on" else "off",
            if (protSettings.second) "on" else "off"
        ))
        val header = TextView(requireContext()).apply {
            setText(R.string.rs_parental_list_header)
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        bodyView.addView(header)
        if (list.isEmpty()) {
            bodyView.addView(makeBodyText(getString(R.string.rs_parental_list_empty)))
            return@launch
        }
        list.forEach { svc ->
            val rowView = row()
            val name = TextView(requireContext())
            name.text = svc.name
            name.setTextColor(Color.WHITE)
            rowView.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val rm = Button(requireContext()).apply {
                setText(R.string.rs_action_remove)
                setBackgroundResource(R.drawable.bg_button)
                setTextColor(Color.WHITE)
                setOnClickListener {
                    lifecycleScope.launch {
                        val ok = repo.parentalProtect(svc.ref, add = false)
                        toastOkOrFail(ok)
                        if (ok) refresh()
                    }
                }
            }
            rowView.addView(rm)
            bodyView.addView(rowView)
        }
    }

    private fun changeAppPin() {
        val old = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.rs_parental_old_pin_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val new = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.rs_parental_new_pin_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val ll = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), 0)
            addView(old); addView(new)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rs_parental_btn_change_app_pin)
            .setView(ll)
            .setPositiveButton(R.string.rs_action_ok) { _, _ ->
                if (prefs.parentalPinHash.isNotEmpty() &&
                    !prefs.verifyParentalPin(old.text.toString())) {
                    toastRes(R.string.rs_parental_pin_wrong)
                    return@setPositiveButton
                }
                prefs.setParentalPin(new.text.toString().trim())
                toastRes(R.string.rs_action_ok)
            }
            .setNegativeButton(R.string.rs_action_cancel, null)
            .show()
    }

    private fun changeSetupPin() {
        val old = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.rs_parental_old_pin_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val new = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.rs_parental_new_pin_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        val ll = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), 0)
            addView(old); addView(new)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rs_parental_btn_change_setup_pin)
            .setView(ll)
            .setPositiveButton(R.string.rs_action_ok) { _, _ ->
                lifecycleScope.launch {
                    val ok = repo.changeSetupPin(
                        newPin = new.text.toString().trim(),
                        oldPin = old.text.toString().trim()
                    )
                    toastOkOrFail(ok)
                }
            }
            .setNegativeButton(R.string.rs_action_cancel, null)
            .show()
    }
}
