package com.enigma2.firetv.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.enigma2.firetv.BuildConfig
import com.enigma2.firetv.R
import com.enigma2.firetv.data.api.ApiClient
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.util.NetworkUtils
import kotlinx.coroutines.launch

/**
 * Settings screen allowing the user to change Enigma2 receiver connection details.
 * Uses AndroidX Preference library. Changes are applied immediately.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var prefs: ReceiverPreferences
    private val repo = Enigma2Repository()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        prefs = ReceiverPreferences(requireContext())

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
            summary = userSummary(prefs.username)
            setOnPreferenceChangeListener { _, newValue ->
                prefs.username = newValue as String
                summary = userSummary(prefs.username)
                reInitApi(prefs)
                true
            }
        }

        findPreference<EditTextPreference>("password")?.apply {
            text = prefs.password
            summary = passwordSummary(prefs.password)
            setOnPreferenceChangeListener { _, newValue ->
                prefs.password = newValue as String
                summary = passwordSummary(prefs.password)
                reInitApi(prefs)
                true
            }
        }

        findPreference<Preference>("test_connection")?.setOnPreferenceClickListener {
            runConnectionTest(it)
            true
        }

        findPreference<Preference>("manage_hidden_bouquets")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), HideBouquetsActivity::class.java))
            true
        }

        findPreference<Preference>("box_info")?.setOnPreferenceClickListener {
            showBoxInfo()
            true
        }

        findPreference<SwitchPreferenceCompat>("night_mode_switch")?.apply {
            isChecked = prefs.nightMode == AppCompatDelegate.MODE_NIGHT_YES
            setOnPreferenceChangeListener { _, newValue ->
                val mode = if (newValue as Boolean) AppCompatDelegate.MODE_NIGHT_YES
                           else AppCompatDelegate.MODE_NIGHT_NO
                prefs.nightMode = mode
                AppCompatDelegate.setDefaultNightMode(mode)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("auto_resume_channel")?.apply {
            isChecked = prefs.autoResumeEnabled
            setOnPreferenceChangeListener { _, newValue ->
                prefs.autoResumeEnabled = newValue as Boolean
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("zap_on_channel_change")?.apply {
            isChecked = prefs.zapOnChannelChange
            setOnPreferenceChangeListener { _, newValue ->
                prefs.zapOnChannelChange = newValue as Boolean
                true
            }
        }

        findPreference<Preference>("about_version")?.summary =
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    private fun userSummary(value: String): String =
        if (value.isBlank()) getString(R.string.pref_summary_username_none) else value

    private fun passwordSummary(value: String): String = getString(
        if (value.isBlank()) R.string.pref_summary_password_none
        else R.string.pref_summary_password_set
    )

    private fun runConnectionTest(pref: Preference) {
        if (!NetworkUtils.isOnline(requireContext())) {
            val msg = getString(R.string.connection_test_offline)
            pref.summary = msg
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            return
        }
        pref.summary = getString(R.string.connection_test_running)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bouquets = repo.getBouquets()
                val msg = getString(R.string.connection_test_ok, bouquets.size)
                pref.summary = msg
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val msg = getString(
                    R.string.connection_test_fail,
                    com.enigma2.firetv.util.ApiErrors.userMessage(requireContext(), e)
                )
                pref.summary = msg
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
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

    /**
     * Fetches `/api/about` and renders a tolerant key/value summary in a dialog.
     * Lists (tuners, hdd, ifaces) are flattened to one line per entry.
     */
    private fun showBoxInfo() {
        val ctx = requireContext()
        val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.box_info_title)
            .setMessage(R.string.box_info_loading)
            .setPositiveButton(R.string.box_info_close, null)
            .show()
        viewLifecycleOwner.lifecycleScope.launch {
            val info = repo.getBoxInfo()
            if (info == null || info.isEmpty()) {
                dialog.setMessage(getString(R.string.box_info_unavailable))
            } else {
                dialog.setMessage(formatBoxInfo(info))
            }
        }
    }

    private fun formatBoxInfo(info: Map<String, Any?>): CharSequence {
        // Show common fields first (when present), then anything else alphabetically.
        val priorityOrder = listOf(
            "brand", "model", "boxtype", "machinebuild", "chipset",
            "imagever", "imagedistro", "enigmaver", "webifver", "kernelver",
            "uptime", "fp_version", "friendlyimagedistro", "lanmac"
        )
        val sb = StringBuilder()
        val seen = mutableSetOf<String>()

        fun appendKv(k: String, v: Any?) {
            if (v == null) return
            when (v) {
                is List<*> -> {
                    if (v.isEmpty()) return
                    sb.append(prettyKey(k)).append(":\n")
                    v.forEachIndexed { i, item ->
                        sb.append("  ").append(i + 1).append(". ")
                            .append(stringifyItem(item)).append('\n')
                    }
                }
                is Map<*, *> -> {
                    sb.append(prettyKey(k)).append(": ").append(stringifyItem(v)).append('\n')
                }
                else -> {
                    val s = v.toString().trim()
                    if (s.isNotEmpty()) sb.append(prettyKey(k)).append(": ").append(s).append('\n')
                }
            }
        }

        for (k in priorityOrder) {
            if (info.containsKey(k)) {
                appendKv(k, info[k])
                seen.add(k)
            }
        }
        info.keys.sorted().forEach { k ->
            if (k !in seen) appendKv(k, info[k])
        }
        return sb.toString().trimEnd()
    }

    private fun prettyKey(k: String): String = k
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }

    private fun stringifyItem(item: Any?): String = when (item) {
        null -> ""
        is Map<*, *> -> item.entries
            .filter { it.value?.toString()?.isNotBlank() == true }
            .joinToString(", ") { "${it.key}=${it.value}" }
        is List<*> -> item.joinToString(", ") { stringifyItem(it) }
        else -> item.toString()
    }
}
