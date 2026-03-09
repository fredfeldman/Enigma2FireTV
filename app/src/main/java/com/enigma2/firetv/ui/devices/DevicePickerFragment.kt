package com.enigma2.firetv.ui.devices

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.model.DeviceProfile
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.ui.main.MainActivity
import com.enigma2.firetv.ui.setup.SetupFragment

/**
 * Displays all configured Enigma2 devices. The user can:
 *  - Tap a device to switch to it and go to the channel list.
 *  - Long-press a device to edit or delete it.
 *  - Tap "Add Device" to configure a new device.
 */
class DevicePickerFragment : Fragment() {

    private lateinit var rvDevices: RecyclerView
    private lateinit var btnAddDevice: Button
    private lateinit var tvTitle: TextView
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var prefs: ReceiverPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_device_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = ReceiverPreferences(requireContext())

        tvTitle = view.findViewById(R.id.tv_picker_title)
        rvDevices = view.findViewById(R.id.rv_devices)
        btnAddDevice = view.findViewById(R.id.btn_add_device)

        deviceAdapter = DeviceAdapter(
            onDeviceClick = { device ->
                (activity as? MainActivity)?.switchToDevice(device.id)
            },
            onDeviceLongClick = { device ->
                showDeviceOptions(device)
            }
        )
        rvDevices.layoutManager = LinearLayoutManager(requireContext())
        rvDevices.adapter = deviceAdapter

        btnAddDevice.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_container, SetupFragment.newInstance(showCancel = true))
                .addToBackStack(null)
                .commit()
        }

        refreshDevices()
    }

    override fun onResume() {
        super.onResume()
        refreshDevices()
    }

    private fun refreshDevices() {
        deviceAdapter.submitList(prefs.devices.toList())
        deviceAdapter.setActiveId(prefs.activeDeviceId)
    }

    private fun showDeviceOptions(device: DeviceProfile) {
        AlertDialog.Builder(requireContext())
            .setTitle(device.name)
            .setItems(arrayOf(
                getString(R.string.device_option_edit),
                getString(R.string.device_option_delete)
            )) { _, which ->
                when (which) {
                    0 -> editDevice(device)
                    1 -> confirmDeleteDevice(device)
                }
            }
            .show()
    }

    private fun editDevice(device: DeviceProfile) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.main_container, SetupFragment.newInstance(deviceId = device.id, showCancel = true))
            .addToBackStack(null)
            .commit()
    }

    private fun confirmDeleteDevice(device: DeviceProfile) {
        if (prefs.devices.size <= 1) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.device_delete_last_title))
                .setMessage(getString(R.string.device_delete_last_message))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.device_delete_confirm_title))
            .setMessage(getString(R.string.device_delete_confirm_message, device.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                prefs.removeDevice(device.id)
                // If deleted device was active, new active is already set by removeDevice;
                // re-init API client to the new active device.
                val active = prefs.activeDevice
                if (active != null) {
                    ApiClient.initialize(prefs.host, prefs.port, prefs.useHttps, prefs.username, prefs.password)
                }
                refreshDevices()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
