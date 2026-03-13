package com.enigma2.firetv.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.enigma2.firetv.R
import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.ui.channels.ChannelsFragment
import com.enigma2.firetv.ui.devices.DevicePickerFragment
import com.enigma2.firetv.ui.player.PlayerActivity
import com.enigma2.firetv.ui.setup.SetupFragment
import com.enigma2.firetv.ui.viewmodel.ChannelViewModel
import com.enigma2.firetv.worker.TimerPollingWorker
import java.util.concurrent.TimeUnit

/**
 * Single-activity host that swaps between [SetupFragment], [ChannelsFragment],
 * and [DevicePickerFragment].
 */
class MainActivity : FragmentActivity() {

    private lateinit var prefs: ReceiverPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(ReceiverPreferences(this).nightMode)
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

                // Auto-resume last channel on cold launch
                val lastRef = prefs.lastChannelRef
                if (prefs.autoResumeEnabled && lastRef.isNotBlank()) {
                    startActivity(Intent(this, PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_STREAM_URL, prefs.streamUrl(lastRef))
                        putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, prefs.lastChannelName)
                        putExtra(PlayerActivity.EXTRA_SERVICE_REF, lastRef)
                    })
                }

                // Start background timer polling for recording notifications
                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    TimerPollingWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<TimerPollingWorker>(15, TimeUnit.MINUTES).build()
                )
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val digit = keyCode - KeyEvent.KEYCODE_0
        if (digit in 0..9) {
            val cf = supportFragmentManager.findFragmentById(R.id.main_container)
            if (cf is ChannelsFragment) {
                cf.handleNumberKey(digit)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
