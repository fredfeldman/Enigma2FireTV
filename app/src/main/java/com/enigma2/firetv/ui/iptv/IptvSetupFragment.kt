package com.enigma2.firetv.ui.iptv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.enigma2.firetv.R
import com.enigma2.firetv.data.prefs.IptvPreferences
import com.enigma2.firetv.data.repository.IptvRepository
import kotlinx.coroutines.launch

/**
 * First-run setup screen for the IPTV player.
 * Accepts an M3U URL (required) and an XMLTV / EPGSHARE01 EPG URL (optional).
 * On success the channels are cached and [IptvChannelsFragment] is shown.
 */
class IptvSetupFragment : Fragment() {

    private lateinit var etM3uUrl: EditText
    private lateinit var etEpgUrl: EditText
    private lateinit var btnLoad: TextView
    private lateinit var loading: ProgressBar
    private lateinit var tvError: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_iptv_setup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etM3uUrl = view.findViewById(R.id.et_m3u_url)
        etEpgUrl  = view.findViewById(R.id.et_epg_url)
        btnLoad   = view.findViewById(R.id.btn_iptv_load)
        loading   = view.findViewById(R.id.iptv_setup_loading)
        tvError   = view.findViewById(R.id.tv_iptv_setup_error)

        val prefs = IptvPreferences(requireContext())
        if (prefs.m3uUrl.isNotBlank()) etM3uUrl.setText(prefs.m3uUrl)
        etEpgUrl.setText(prefs.epgUrl.ifBlank { IptvPreferences.DEFAULT_EPG_URL })

        btnLoad.setOnClickListener { loadChannels(prefs) }
    }

    private fun loadChannels(prefs: IptvPreferences) {
        val m3uUrl = etM3uUrl.text.toString().trim()
        if (m3uUrl.isBlank()) {
            showError(getString(R.string.iptv_m3u_required))
            return
        }
        val epgUrl = etEpgUrl.text.toString().trim()
            .ifBlank { IptvPreferences.DEFAULT_EPG_URL }

        setLoadingState(true)

        lifecycleScope.launch {
            try {
                val repo = IptvRepository(requireContext())
                val channels = repo.fetchChannels(m3uUrl)
                if (channels.isEmpty()) {
                    showError(getString(R.string.iptv_empty))
                    return@launch
                }
                prefs.m3uUrl = m3uUrl
                prefs.epgUrl = epgUrl
                repo.saveCachedChannels(channels)

                parentFragmentManager.beginTransaction()
                    .replace(R.id.iptv_container, IptvChannelsFragment())
                    .commit()
            } catch (e: Exception) {
                showError("${getString(R.string.iptv_error)}: ${e.message}")
            }
        }
    }

    private fun setLoadingState(on: Boolean) {
        loading.visibility = if (on) View.VISIBLE else View.GONE
        btnLoad.isEnabled  = !on
        if (on) tvError.visibility = View.GONE
    }

    private fun showError(msg: String) {
        setLoadingState(false)
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }
}
