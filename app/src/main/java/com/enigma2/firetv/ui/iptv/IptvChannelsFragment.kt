package com.enigma2.firetv.ui.iptv

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.IptvChannel
import com.enigma2.firetv.data.model.IptvEpgEvent
import com.enigma2.firetv.data.prefs.IptvPreferences
import com.enigma2.firetv.data.repository.IptvRepository
import com.enigma2.firetv.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Two-panel IPTV channel browser.
 * Left panel: M3U groups. Right panel: channels in the selected group.
 * EPG now/next data is fetched from EPGSHARE01 (or a user-configured XMLTV URL)
 * in the background and applied to the channel list automatically.
 */
class IptvChannelsFragment : Fragment() {

    companion object {
        private const val GROUP_ALL = "All Channels"
        /** Re-use cached EPG for up to 6 hours before fetching again. */
        private const val EPG_CACHE_MAX_AGE_MS = 6L * 60 * 60 * 1000
    }

    private lateinit var rvGroups: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var tvGroupTitle: TextView
    private lateinit var btnRefresh: TextView
    private lateinit var btnSetup: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvEpgStatus: TextView

    private lateinit var groupAdapter: IptvGroupAdapter
    private lateinit var channelAdapter: IptvChannelAdapter
    private lateinit var prefs: IptvPreferences
    private lateinit var repo: IptvRepository

    private var allChannels: List<IptvChannel> = emptyList()
    private var groups: List<String> = emptyList()
    private var selectedGroup: String = GROUP_ALL
    private var epgMap: Map<String, List<IptvEpgEvent>> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_iptv_channels, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = IptvPreferences(requireContext())
        repo  = IptvRepository(requireContext())

        rvGroups        = view.findViewById(R.id.rv_iptv_groups)
        rvChannels      = view.findViewById(R.id.rv_iptv_channels)
        tvGroupTitle    = view.findViewById(R.id.tv_iptv_group_title)
        btnRefresh      = view.findViewById(R.id.btn_iptv_refresh)
        btnSetup        = view.findViewById(R.id.btn_iptv_setup)
        loadingIndicator = view.findViewById(R.id.iptv_loading)
        tvError         = view.findViewById(R.id.tv_iptv_error)
        tvEpgStatus     = view.findViewById(R.id.tv_iptv_epg_status)

        groupAdapter   = IptvGroupAdapter { group -> selectGroup(group) }
        channelAdapter = IptvChannelAdapter { channel -> playChannel(channel) }

        rvGroups.layoutManager = LinearLayoutManager(requireContext())
        rvGroups.adapter = groupAdapter

        rvChannels.layoutManager = LinearLayoutManager(requireContext())
        rvChannels.adapter = channelAdapter

        btnRefresh.setOnClickListener { refreshChannels() }
        btnSetup.setOnClickListener   { openSetup() }

        loadChannels()
    }

    // -------------------------------------------------------------------------
    // Data loading
    // -------------------------------------------------------------------------

    private fun loadChannels(forceRefresh: Boolean = false) {
        val cached = if (!forceRefresh) repo.loadCachedChannels() else null
        if (!cached.isNullOrEmpty()) {
            displayChannels(cached)
            loadEpgIfNeeded()
        } else {
            refreshChannels()
        }
    }

    private fun refreshChannels() {
        val m3uUrl = prefs.m3uUrl
        if (m3uUrl.isBlank()) { openSetup(); return }

        setLoading(true)
        tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val channels = repo.fetchChannels(m3uUrl)
                repo.saveCachedChannels(channels)
                withContext(Dispatchers.Main) { displayChannels(channels) }
                loadEpgIfNeeded()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    showError("${getString(R.string.iptv_error)}: ${e.message}")
                }
            }
        }
    }

    private fun displayChannels(channels: List<IptvChannel>) {
        allChannels = channels
        setLoading(false)

        val groupNames = mutableListOf(GROUP_ALL)
        groupNames += channels.map { it.group }.distinct().sorted()
        groups = groupNames

        groupAdapter.setGroups(groups, 0)
        selectGroup(GROUP_ALL)
    }

    private fun loadEpgIfNeeded() {
        if (repo.epgCacheAgeMs() < EPG_CACHE_MAX_AGE_MS) {
            val cached = repo.loadCachedEpg()
            if (cached != null) {
                epgMap = cached
                channelAdapter.setEpg(epgMap)
                return
            }
        }
        fetchEpg()
    }

    private fun fetchEpg() {
        val epgUrl = prefs.epgUrl
        if (epgUrl.isBlank()) return

        tvEpgStatus.text = getString(R.string.iptv_epg_loading)
        tvEpgStatus.visibility = View.VISIBLE

        val channelIds = allChannels.map { it.tvgId }.toSet()

        lifecycleScope.launch {
            try {
                val epg = repo.fetchEpg(epgUrl, channelIds)
                repo.saveCachedEpg(epg)
                withContext(Dispatchers.Main) {
                    epgMap = epg
                    channelAdapter.setEpg(epgMap)
                    tvEpgStatus.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvEpgStatus.text = getString(R.string.iptv_epg_failed)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private fun selectGroup(group: String) {
        selectedGroup = group
        val idx = groups.indexOf(group).coerceAtLeast(0)
        groupAdapter.setSelected(idx)
        tvGroupTitle.text = if (group == GROUP_ALL) getString(R.string.iptv_player) else group
        val filtered = if (group == GROUP_ALL) allChannels
                       else allChannels.filter { it.group == group }
        channelAdapter.setChannels(filtered)
    }

    private fun playChannel(channel: IptvChannel) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL,   channel.streamUrl)
            putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, channel.name)
            // No EXTRA_SERVICE_REF → no Enigma2 zap, works standalone
        }
        startActivity(intent)
    }

    private fun openSetup() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.iptv_container, IptvSetupFragment())
            .commit()
    }

    private fun setLoading(on: Boolean) {
        loadingIndicator.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }
}
