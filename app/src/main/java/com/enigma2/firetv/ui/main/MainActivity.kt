package com.enigma2.firetv.ui.main

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.enigma2.firetv.R
import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.ui.channels.ChannelsFragment
import com.enigma2.firetv.ui.devices.DevicePickerFragment
import com.enigma2.firetv.ui.setup.SetupFragment
import com.enigma2.firetv.ui.viewmodel.ChannelViewModel

/**
 * Single-activity host that swaps between [SetupFragment], [ChannelsFragment],
 * and [DevicePickerFragment].
 */
class MainActivity : FragmentActivity() {

    private lateinit var prefs: ReceiverPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = ReceiverPreferences(this)

        if (savedInstanceState == null) {
            if (prefs.isConfigured) {
                ApiClient.initialize(
                    host = prefs.host,
                    port = prefs.port,
                    useHttps = prefs.useHttps,
                    username = prefs.username,
                    password = prefs.password
                )
                showChannels()
            } else {
                showSetup()
            }
        }
    }

    /** Show the setup/add/edit screen. For initial setup use defaults (no args). */
    fun showSetup(deviceId: String? = null, showCancel: Boolean = false) {
        val tx = supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, SetupFragment.newInstance(deviceId, showCancel))
        if (showCancel) tx.addToBackStack(null)
        tx.commit()
    }

    fun showChannels() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, ChannelsFragment())
            .commit()
    }

    fun showDevicePicker() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, DevicePickerFragment())
            .addToBackStack(null)
            .commit()
    }

    /**
     * Switches the active device, re-initializes the API client, resets cached
     * channel data, clears the back stack and shows a fresh [ChannelsFragment].
     */
    fun switchToDevice(deviceId: String) {
        prefs.setActiveDevice(deviceId)
        ApiClient.initialize(
            host = prefs.host,
            port = prefs.port,
            useHttps = prefs.useHttps,
            username = prefs.username,
            password = prefs.password
        )
        ViewModelProvider(this)[ChannelViewModel::class.java].resetForNewDevice()
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        showChannels()
    }
}
