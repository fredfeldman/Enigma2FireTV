package com.enigma2.firetv.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.enigma2.firetv.R
import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.prefs.ReceiverPreferences

/**
 * Settings screen allowing the user to change Enigma2 receiver connection details.
 * Uses AndroidX Preference library. Changes are applied immediately.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val prefs = ReceiverPreferences(requireContext())

        // Sync current values into preference UI
        findPreference<EditTextPreference>("host")?.apply {
            text = prefs.host
            setOnPreferenceChangeListener { _, newValue ->
                prefs.host = newValue as String
                reInitApi(prefs)
                true
            }
        }

        findPreference<EditTextPreference>("port")?.apply {
            text = prefs.port.toString()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.port = (newValue as String).toIntOrNull() ?: 80
                reInitApi(prefs)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("use_https")?.apply {
            isChecked = prefs.useHttps
            setOnPreferenceChangeListener { _, newValue ->
                prefs.useHttps = newValue as Boolean
                reInitApi(prefs)
                true
            }
        }

        findPreference<EditTextPreference>("username")?.apply {
            text = prefs.username
            setOnPreferenceChangeListener { _, newValue ->
                prefs.username = newValue as String
                reInitApi(prefs)
                true
            }
        }

        findPreference<EditTextPreference>("password")?.apply {
            text = prefs.password
            setOnPreferenceChangeListener { _, newValue ->
                prefs.password = newValue as String
                reInitApi(prefs)
                true
            }
        }

        findPreference<Preference>("manage_hidden_bouquets")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), HideBouquetsActivity::class.java))
            true
        }
    }

    private fun reInitApi(prefs: ReceiverPreferences) {
        ApiClient.initialize(
            host = prefs.host,
            port = prefs.port,
            useHttps = prefs.useHttps,
            username = prefs.username,
            password = prefs.password
        )
    }
}
