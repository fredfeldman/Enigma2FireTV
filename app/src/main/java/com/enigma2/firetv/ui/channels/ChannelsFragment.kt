package com.enigma2.firetv.ui.channels

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Bouquet
import com.enigma2.firetv.data.model.NowNextEvent
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.ui.epg.EpgFragment
import com.enigma2.firetv.ui.epg.EpgSearchFragment
import com.enigma2.firetv.ui.main.MainActivity
import com.enigma2.firetv.ui.player.PlayerActivity
import com.enigma2.firetv.ui.recordings.RecordingsFragment
import com.enigma2.firetv.ui.settings.SettingsActivity
import com.enigma2.firetv.ui.timers.TimersFragment
import com.enigma2.firetv.ui.viewmodel.ChannelViewModel
import com.enigma2.firetv.utils.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Two-panel layout: bouquet list on the left, channel list on the right.
 * Selecting a channel launches [PlayerActivity].
 */
class ChannelsFragment : Fragment() {

    private val viewModel: ChannelViewModel by activityViewModels()

    private lateinit var rvBouquets: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var tvBouquetTitle: TextView
    private lateinit var btnSwitchDevice: TextView
    private lateinit var btnEpg: TextView
    private lateinit var btnRecordings: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnTimers: TextView
    private lateinit var btnWol: TextView
    private lateinit var btnScreenshot: TextView
    private lateinit var tvNumberJump: TextView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var etChannelFilter: EditText

    private lateinit var bouquetAdapter: BouquetAdapter
    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var prefs: ReceiverPreferences
    private val repository = Enigma2Repository()
    private var fullChannelList: List<Service> = emptyList()

    // Channel number jump state
    private val numberJumpHandler = Handler(Looper.getMainLooper())
    private val numberJumpBuffer = StringBuilder()
    private val clearNumberJumpRunnable = Runnable {
        numberJumpBuffer.clear()
        tvNumberJump.visibility = View.GONE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_channels, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = ReceiverPreferences(requireContext())

        rvBouquets = view.findViewById(R.id.rv_bouquets)
        rvChannels = view.findViewById(R.id.rv_channels)
        tvBouquetTitle = view.findViewById(R.id.tv_bouquet_title)
        btnSwitchDevice = view.findViewById(R.id.btn_switch_device)
        btnEpg = view.findViewById(R.id.btn_epg)
        btnRecordings = view.findViewById(R.id.btn_recordings)
        btnSettings = view.findViewById(R.id.btn_settings)
        btnTimers = view.findViewById(R.id.btn_timers)
        btnWol = view.findViewById(R.id.btn_wol)
        btnScreenshot = view.findViewById(R.id.btn_screenshot)
        tvNumberJump = view.findViewById(R.id.tv_number_jump)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        tvError = view.findViewById(R.id.tv_error)
        etChannelFilter = view.findViewById(R.id.et_channel_filter)
        etChannelFilter.addTextChangedListener { applyChannelFilter() }

        setupBouquetList()
        setupChannelList()
        observeViewModel()

        btnSwitchDevice.setOnClickListener {
            (activity as? MainActivity)?.showDevicePicker()
        }
        btnEpg.setOnClickListener { openEpg() }
        btnRecordings.setOnClickListener { openRecordings() }
        btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        btnTimers.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_container, TimersFragment())
                .addToBackStack(null)
                .commit()
        }
        btnWol.setOnClickListener { sendWakeOnLan() }
        btnScreenshot.setOnClickListener { showScreenshot() }

        if (viewModel.bouquets.value.isNullOrEmpty()) {
            viewModel.loadBouquets()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply filter in case hidden bouquets changed while in Settings
        applyBouquetFilter(viewModel.bouquets.value ?: emptyList())
        // Sync star state in adapter
        channelAdapter.updateFavorites(prefs.favoriteServiceRefs)
    }

    private fun applyBouquetFilter(bouquets: List<Bouquet>) {
        val hidden = prefs.hiddenBouquetRefs
        val visible = bouquets.filter { it.ref !in hidden }.toMutableList()
        // Prepend synthetic Favorites bouquet if any favorites are saved
        if (prefs.favoriteServices.isNotEmpty()) {
            visible.add(0, Bouquet(
                ref = ChannelViewModel.FAVORITES_REF,
                name = getString(R.string.favorites_bouquet_label),
                channels = null
            ))
        }
        bouquetAdapter.submitList(visible)
        // If the currently selected bouquet was just hidden, select the first visible one
        val selected = viewModel.selectedBouquet.value
        if (selected != null && selected.ref != ChannelViewModel.FAVORITES_REF
                && selected.ref in hidden && visible.isNotEmpty()) {
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
            onChannelLongClick = { service -> showChannelInfo(service) },
            onFavoriteToggle = { service ->
                prefs.toggleFavorite(service)
                channelAdapter.updateFavorites(prefs.favoriteServiceRefs)
                // Refresh bouquet list so the pinned bouquet appears/disappears
                applyBouquetFilter(viewModel.bouquets.value ?: emptyList())
                // If currently viewing favorites, refresh the channel list too
                if (viewModel.selectedBouquet.value?.ref == ChannelViewModel.FAVORITES_REF) {
                    viewModel.showFavoriteChannels(prefs.favoriteServices)
                }
            }
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
            fullChannelList = channels
            applyChannelFilter()
        }

        viewModel.selectedBouquet.observe(viewLifecycleOwner) { bouquet ->
            tvBouquetTitle.text = bouquet?.name ?: ""
            if (bouquet != null) bouquetAdapter.setSelectedBouquet(bouquet.ref)
            if (bouquet?.ref == ChannelViewModel.FAVORITES_REF) {
                viewModel.showFavoriteChannels(prefs.favoriteServices)
            }
        }

        viewModel.nowNext.observe(viewLifecycleOwner) { nowNextList ->
            channelAdapter.updateNowNext(nowNextList)
        }

        viewModel.recordingServiceRefs.observe(viewLifecycleOwner) { refs ->
            channelAdapter.updateRecordingRefs(refs)
        }
    }

    private fun applyChannelFilter() {
        val query = etChannelFilter.text.toString().trim()
        val filtered = if (query.isEmpty()) fullChannelList
                       else fullChannelList.filter { it.name.contains(query, ignoreCase = true) }
        channelAdapter.submitList(filtered)
    }

    private fun playChannel(service: Service) {
        prefs.saveLastChannel(service.ref, service.name)
        // Fire-and-forget: also tune the receiver to this channel
        viewLifecycleOwner.lifecycleScope.launch {
            try { repository.zap(service.ref) } catch (_: Exception) {}
        }
        val streamUrl = prefs.streamUrl(service.ref)
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, service.name)
            putExtra(PlayerActivity.EXTRA_SERVICE_REF, service.ref)
        }
        startActivity(intent)
    }

    private fun showChannelInfo(service: Service) {
        val isFav = prefs.isFavorite(service.ref)
        val favOption = if (isFav) getString(R.string.favorite_remove) else getString(R.string.favorite_add)
        val options = arrayOf(getString(R.string.channel_menu_epg), favOption, getString(R.string.zap_receiver))
        AlertDialog.Builder(requireContext())
            .setTitle(service.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openChannelEpg(service)
                    1 -> {
                        prefs.toggleFavorite(service)
                        channelAdapter.updateFavorites(prefs.favoriteServiceRefs)
                        applyBouquetFilter(viewModel.bouquets.value ?: emptyList())
                        if (viewModel.selectedBouquet.value?.ref == ChannelViewModel.FAVORITES_REF) {
                            viewModel.showFavoriteChannels(prefs.favoriteServices)
                        }
                    }
                    2 -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                repository.zap(service.ref)
                                Toast.makeText(requireContext(), getString(R.string.zap_sent, service.name), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), getString(R.string.zap_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun openChannelEpg(service: Service) {
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

    // ── Wake-on-LAN ───────────────────────────────────────────────────────────

    private fun sendWakeOnLan() {
        val mac = prefs.activeDevice?.macAddress?.trim() ?: ""
        if (mac.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.wol_no_mac), Toast.LENGTH_LONG).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { WakeOnLan.send(mac) }
            Toast.makeText(
                requireContext(),
                if (ok) getString(R.string.wol_sent) else getString(R.string.wol_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ── Screenshot ────────────────────────────────────────────────────────────

    private fun showScreenshot() {
        Toast.makeText(requireContext(), getString(R.string.screenshot_loading), Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val body = repository.getScreenshot()
            if (body == null) {
                Toast.makeText(requireContext(), getString(R.string.screenshot_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val bytes = withContext(Dispatchers.IO) { body.bytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null) {
                Toast.makeText(requireContext(), getString(R.string.screenshot_failed), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val iv = ImageView(requireContext()).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
            }
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.screenshot_title))
                .setView(iv)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    // ── Channel number jump ───────────────────────────────────────────────────

    fun handleNumberKey(digit: Int) {
        numberJumpBuffer.append(digit.toString())
        tvNumberJump.text = numberJumpBuffer.toString()
        tvNumberJump.visibility = View.VISIBLE

        numberJumpHandler.removeCallbacks(clearNumberJumpRunnable)
        numberJumpHandler.postDelayed({
            val number = numberJumpBuffer.toString().toIntOrNull()
            numberJumpBuffer.clear()
            tvNumberJump.visibility = View.GONE
            if (number != null) jumpToChannelNumber(number)
        }, 1_500L)
    }

    private fun jumpToChannelNumber(number: Int) {
        val channels = viewModel.channels.value ?: return
        val idx = (number - 1).coerceIn(0, channels.size - 1)
        (rvChannels.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(idx, 0)
        rvChannels.post { rvChannels.findViewHolderForAdapterPosition(idx)?.itemView?.requestFocus() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        numberJumpHandler.removeCallbacksAndMessages(null)
    }
}