package com.enigma2.firetv.ui.messages

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.api.RemoteReceiverApi
import com.enigma2.firetv.data.model.DeviceProfile
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch

/**
 * Send an on-screen message to one or more Enigma2 receivers via `api/message`.
 *
 * v1.2.0 Phase 6.4 — added multi-receiver "Send to" spinner when ≥2 profiles exist.
 */
object SendMessageDialog {

    private val repo = Enigma2Repository()

    fun show(context: Context, owner: LifecycleOwner) {
        val prefs = ReceiverPreferences(context)
        val devices = prefs.devices

        val pad = (context.resources.displayMetrics.density * 16).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val text = EditText(context).apply {
            hint = context.getString(R.string.send_message_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        val typeSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf(
                context.getString(R.string.msg_type_info),
                context.getString(R.string.msg_type_warning),
                context.getString(R.string.msg_type_question),
                context.getString(R.string.msg_type_error)
            ))
        }
        val timeout = EditText(context).apply {
            hint = context.getString(R.string.send_message_timeout)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("10")
        }

        // Phase 6.4: multi-receiver target spinner
        var targetSpinner: Spinner? = null
        if (devices.size >= 2) {
            val deviceNames = devices.map { it.name }
            // Pre-select active device
            val activeIdx = devices.indexOfFirst { it.id == prefs.activeDeviceId }.coerceAtLeast(0)
            targetSpinner = Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, deviceNames)
                setSelection(activeIdx)
            }
        }

        container.addView(text)
        container.addView(typeSpinner)
        container.addView(timeout)
        targetSpinner?.let { container.addView(it) }

        AlertDialog.Builder(context)
            .setTitle(R.string.send_message)
            .setView(container)
            .setPositiveButton(R.string.send_message_send) { _, _ ->
                val msg = text.text.toString().trim()
                if (msg.isEmpty()) return@setPositiveButton
                val typeCode = typeSpinner.selectedItemPosition + 1
                val to = timeout.text.toString().toIntOrNull() ?: 10
                val selectedDevice: DeviceProfile? =
                    targetSpinner?.let { devices.getOrNull(it.selectedItemPosition) }
                owner.lifecycleScope.launch {
                    val ok = if (selectedDevice == null ||
                                 selectedDevice.id == prefs.activeDeviceId) {
                        repo.sendMessage(msg, typeCode, to)
                    } else {
                        RemoteReceiverApi.message(selectedDevice, msg, typeCode, to)
                    }
                    Toast.makeText(context,
                        if (ok) R.string.send_message_sent else R.string.send_message_failed,
                        Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

