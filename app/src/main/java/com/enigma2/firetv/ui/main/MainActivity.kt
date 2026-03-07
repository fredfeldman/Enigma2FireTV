package com.enigma2.firetv.ui.main

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.enigma2.firetv.R
import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.ui.channels.ChannelsFragment
import com.enigma2.firetv.ui.setup.SetupFragment

/**
 * Single-activity host that swaps between [SetupFragment] and [ChannelsFragment].
 */
class MainActivity : FragmentActivity() {

    private lateinit var prefs: ReceiverPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = ReceiverPreferences(this)

        if (savedInstanceState == null) {
            if (prefs.isConfigured) {
                // Re-initialize API client with stored settings
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

    fun showSetup() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, SetupFragment())
            .commit()
    }

    fun showChannels() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_container, ChannelsFragment())
            .commit()
    }
}
