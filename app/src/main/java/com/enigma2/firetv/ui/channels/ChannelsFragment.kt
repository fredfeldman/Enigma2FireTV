package com.enigma2.firetv.ui.channels

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.model.NowNextEvent
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.ui.epg.EpgFragment
import com.enigma2.firetv.ui.main.MainActivity
import com.enigma2.firetv.ui.player.PlayerActivity
import com.enigma2.firetv.ui.recordings.RecordingsFragment
import com.enigma2.firetv.ui.settings.SettingsActivity
import com.enigma2.firetv.ui.viewmodel.ChannelViewModel

/**
 * Two-panel layout: bouquet list on the left, channel list on the right.
 * Selecting a channel launches [PlayerActivity].
 */
class ChannelsFragment : Fragment() {

    private val viewModel: ChannelViewModel by activityViewModels()

    private lateinit var rvBouquets: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var tvBouquetTitle: TextView
    private lateinit var btnEpg: TextView
    private lateinit var btnRecordings: TextView
    private lateinit var btnSettings: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvError: TextView

    private lateinit var bouquetAdapter: BouquetAdapter
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var prefs: ReceiverPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_channels, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = ReceiverPreferences(requireContext())

        rvBouquets = view.findViewById(R.id.rv_bouquets)
        rvChannels = view.findViewById(R.id.rv_channels)
        tvBouquetTitle = view.findViewById(R.id.tv_bouquet_title)
        btnEpg = view.findViewById(R.id.btn_epg)
        btnRecordings = view.findViewById(R.id.btn_recordings)
        btnSettings = view.findViewById(R.id.btn_settings)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        tvError = view.findViewById(R.id.tv_error)

        setupBouquetList()
        setupChannelList()
        observeViewModel()

        btnEpg.setOnClickListener { openEpg() }
        btnRecordings.setOnClickListener { openRecordings() }
        btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        if (viewModel.bouquets.value.isNullOrEmpty()) {
            viewModel.loadBouquets()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply filter in case hidden bouquets changed while in Settings
        applyBouquetFilter(viewModel.bouquets.value ?: emptyList())
    }

    private fun applyBouquetFilter(bouquets: List<Bouquet>) {
        val hidden = prefs.hiddenBouquetRefs
        val visible = bouquets.filter { it.ref !in hidden }
        bouquetAdapter.submitList(visible)
        // If the currently selected bouquet was just hidden, select the first visible one
        val selected = viewModel.selectedBouquet.value
        if (selected != null && selected.ref in hidden && visible.isNotEmpty()) {
            viewModel.selectBouquet(visible[0])
        }
    }

    private fun setupBouquetList() {
        bouquetAdapter = BouquetAdapter { bouquet ->
            viewModel.selectBouquet(bouquet)
        }
        rvBouquets.layoutManager = LinearLayoutManager(requireContext())
        rvBouquets.adapter = bouquetAdapter
    }

    private fun setupChannelList() {
        channelAdapter = ChannelAdapter(
            onChannelClick = { service, _ -> playChannel(service) },
            onChannelLongClick = { service -> showChannelInfo(service) }
        )
        rvChannels.layoutManager = LinearLayoutManager(requireContext())
        rvChannels.adapter = channelAdapter
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                tvError.text = error
                tvError.visibility = View.VISIBLE
            } else {
                tvError.visibility = View.GONE
            }
        }

        viewModel.bouquets.observe(viewLifecycleOwner) { bouquets ->
            applyBouquetFilter(bouquets)
        }

        viewModel.channels.observe(viewLifecycleOwner) { channels ->
            channelAdapter.submitList(channels)
        }

        viewModel.selectedBouquet.observe(viewLifecycleOwner) { bouquet ->
            tvBouquetTitle.text = bouquet?.name ?: ""
        }

        viewModel.nowNext.observe(viewLifecycleOwner) { nowNextList ->
            channelAdapter.updateNowNext(nowNextList)
        }
    }

    private fun playChannel(service: Service) {
        val streamUrl = prefs.streamUrl(service.ref)
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, service.name)
            putExtra(PlayerActivity.EXTRA_SERVICE_REF, service.ref)
        }
        startActivity(intent)
    }

    private fun showChannelInfo(service: Service) {
        // Navigate to single-channel EPG
        parentFragmentManager.beginTransaction()
            .replace(R.id.main_container, EpgFragment.newInstance(
                serviceRef = service.ref,
                serviceName = service.name,
                bouquetRef = viewModel.selectedBouquet.value?.ref ?: ""
            ))
            .addToBackStack(null)
            .commit()
    }

    private fun openRecordings() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.main_container, RecordingsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openEpg() {
        val bouquet = viewModel.selectedBouquet.value ?: return
        parentFragmentManager.beginTransaction()
            .replace(R.id.main_container, EpgFragment.newInstance(
                serviceRef = "",
                serviceName = bouquet.name,
                bouquetRef = bouquet.ref
            ))
            .addToBackStack(null)
            .commit()
    }
}
