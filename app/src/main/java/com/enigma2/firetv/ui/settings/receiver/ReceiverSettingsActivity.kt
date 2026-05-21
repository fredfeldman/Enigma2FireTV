package com.enigma2.firetv.ui.settings.receiver

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.enigma2.firetv.R

/**
 * v1.1.0 Phase 1 — host activity for the Receiver Settings hub.
 *
 * Sub-screens are added/replaced inside [R.id.receiver_settings_container].
 * No persistent state lives in the activity itself; each sub-fragment owns
 * its own coroutines and back-stack contribution.
 */
class ReceiverSettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receiver_settings)
        if (savedInstanceState == null) {
            navigateTo(ReceiverSettingsHubFragment(), addToBackStack = false)
        }
    }

    companion object {
        /** Helper used by every sub-screen to swap content + push back-stack. */
        fun navigate(activity: FragmentActivity, fragment: Fragment) {
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.receiver_settings_container, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun navigateTo(fragment: Fragment, addToBackStack: Boolean) {
        val tx = supportFragmentManager.beginTransaction()
            .replace(R.id.receiver_settings_container, fragment)
        if (addToBackStack) tx.addToBackStack(null)
        tx.commit()
    }
}
