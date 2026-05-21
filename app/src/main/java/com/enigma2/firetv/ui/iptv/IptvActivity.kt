package com.enigma2.firetv.ui.iptv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.enigma2.firetv.R
import com.enigma2.firetv.data.prefs.IptvPreferences

/**
 * Standalone IPTV player activity.
 * Does not require a configured Enigma2 receiver.
 * Shows [IptvSetupFragment] on first launch (M3U URL not yet configured)
 * and [IptvChannelsFragment] thereafter.
 */
class IptvActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iptv)

        if (savedInstanceState == null) {
            val prefs = IptvPreferences(this)
            val fragment = if (prefs.m3uUrl.isBlank()) IptvSetupFragment()
                           else IptvChannelsFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.iptv_container, fragment)
                .commit()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
