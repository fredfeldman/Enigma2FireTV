package com.enigma2.firetv.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.model.DeviceProfile
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.ui.main.MainActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * Setup/add/edit screen for an Enigma2 device.
 *
 * Modes:
 *  - Initial setup (no args): save the first device and go to ChannelsFragment.
 *  - Add new device (showCancel=true, no deviceId): save new device, switch to it.
 *  - Edit device (showCancel=true, deviceId set): update existing profile, pop back.
 */
class SetupFragment : Fragment() {

    private lateinit var etDeviceName: TextInputEditText
    private lateinit var etHost: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var cbHttps: CheckBox
    private lateinit var btnConnect: Button
    private lateinit var btnCancel: Button
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvStatus: TextView

    private lateinit var prefs: ReceiverPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_setup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = ReceiverPreferences(requireContext())

        etDeviceName = view.findViewById(R.id.et_device_name)
        etHost = view.findViewById(R.id.et_host)
        etPort = view.findViewById(R.id.et_port)
        etUsername = view.findViewById(R.id.et_username)
        etPassword = view.findViewById(R.id.et_password)
        cbHttps = view.findViewById(R.id.cb_https)
        btnConnect = view.findViewById(R.id.btn_connect)
        btnCancel = view.findViewById(R.id.btn_cancel)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        tvStatus = view.findViewById(R.id.tv_status)

        val deviceId = arguments?.getString(ARG_DEVICE_ID)
        val showCancel = arguments?.getBoolean(ARG_SHOW_CANCEL, false) ?: false

        if (deviceId != null) {
            // Edit mode: pre-fill from existing profile
            val device = prefs.devices.firstOrNull { it.id == deviceId }
            if (device != null) {
                etDeviceName.setText(device.name)
                etHost.setText(device.host)
                etPort.setText(device.port.toString())
                etUsername.setText(device.username)
                etPassword.setText(device.password)
                cbHttps.isChecked = device.useHttps
            }
        } else {
            etPort.setText("80")
        }

        btnCancel.visibility = if (showCancel) View.VISIBLE else View.GONE
        btnCancel.setOnClickListener { parentFragmentManager.popBackStack() }

        btnConnect.setOnClickListener { attemptConnect() }
    }

    private fun attemptConnect() {
        val deviceId = arguments?.getString(ARG_DEVICE_ID)
        val showCancel = arguments?.getBoolean(ARG_SHOW_CANCEL, false) ?: false

        val rawName = etDeviceName.text?.toString()?.trim() ?: ""
        val host = etHost.text?.toString()?.trim() ?: ""
        val portStr = etPort.text?.toString()?.trim() ?: "80"
        val username = etUsername.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        val useHttps = cbHttps.isChecked

        if (host.isBlank()) {
            showStatus(getString(R.string.hint_receiver_ip) + " is required", isError = true)
            return
        }

        val port = portStr.toIntOrNull() ?: 80
        val deviceName = rawName.ifBlank { host }

        setLoading(true)
        showStatus("", isError = false)

        ApiClient.initialize(host, port, useHttps, username, password)

        viewLifecycleOwner.lifecycleScope.launch {
            val repo = Enigma2Repository()
            val bouquets = try {
                repo.getBouquets()
            } catch (e: Exception) {
                android.util.Log.e("SetupFragment", "Connection failed", e)
                setLoading(false)
                val msg = e.message ?: e.javaClass.simpleName
                showStatus("Error: $msg", isError = true)
                return@launch
            }
            setLoading(false)

            if (bouquets.isNotEmpty()) {
                if (deviceId != null) {
                    // Edit existing device
                    val existing = prefs.devices.firstOrNull { it.id == deviceId }
                    if (existing != null) {
                        val updated = existing.copy(
                            name = deviceName, host = host, port = port,
                            useHttps = useHttps, username = username, password = password
                        )
                        prefs.updateDevice(updated)
                        // If we edited a non-active device, restore ApiClient to active device
                        if (prefs.activeDeviceId != deviceId) {
                            ApiClient.initialize(
                                prefs.host, prefs.port, prefs.useHttps,
                                prefs.username, prefs.password
                            )
                        }
                    }
                    parentFragmentManager.popBackStack()
                } else {
                    // Add new device
                    val profile = DeviceProfile(
                        name = deviceName, host = host, port = port,
                        useHttps = useHttps, username = username, password = password
                    )
                    if (showCancel) {
                        // From device picker: add, switch, navigate to channels
                        prefs.addDevice(profile)
                        (activity as? MainActivity)?.switchToDevice(profile.id)
                    } else {
                        // Initial setup
                        prefs.addDevice(profile)
                        (activity as? MainActivity)?.showChannels()
                    }
                }
            } else {
                showStatus(getString(R.string.error_connection), isError = true)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        btnConnect.isEnabled = !loading
    }

    private fun showStatus(message: String, isError: Boolean) {
        if (message.isEmpty()) {
            tvStatus.visibility = View.GONE
        } else {
            tvStatus.text = message
            tvStatus.setTextColor(
                if (isError) resources.getColor(R.color.error, null)
                else resources.getColor(R.color.accent, null)
            )
            tvStatus.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_SHOW_CANCEL = "showCancel"

        fun newInstance(deviceId: String? = null, showCancel: Boolean = false) =
            SetupFragment().apply {
                arguments = Bundle().apply {
                    deviceId?.let { putString(ARG_DEVICE_ID, it) }
                    putBoolean(ARG_SHOW_CANCEL, showCancel)
                }
            }
    }
}
