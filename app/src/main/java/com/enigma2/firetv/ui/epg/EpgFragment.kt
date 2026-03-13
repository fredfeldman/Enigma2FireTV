package com.enigma2.firetv.ui.epg

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.model.Timer
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.ui.player.PlayerActivity
import com.enigma2.firetv.ui.epg.EpgSearchFragment
import com.enigma2.firetv.ui.viewmodel.ChannelViewModel
import com.enigma2.firetv.ui.viewmodel.EpgViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen multi-channel EPG guide.
 *
 * Displays a scrollable grid of events for all services in a bouquet.
 * Pressing OK on a highlighted event opens [PlayerActivity] for that channel.
 * Back button returns to the channel list.
 */
class EpgFragment : Fragment() {

    private val epgViewModel: EpgViewModel by viewModels()
    private val channelViewModel: ChannelViewModel by activityViewModels()

    private lateinit var timeRuler: EpgTimeRulerView
    private lateinit var epgGrid: EpgGridView
    private lateinit var epgHScroll: HorizontalScrollView
    private lateinit var rulerHScroll: HorizontalScrollView
    private lateinit var rvEpgChannels: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvSelectedTitle: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var tvSelectedDesc: TextView
    private lateinit var eventInfoBar: LinearLayout
    private lateinit var btnRecord: Button
    private lateinit var btnEpgSearch: TextView

    private var selectedEvent: EpgEvent? = null

    /** Pinned channel column adapter: shows picon + channel name aligned to EPG rows. */
    private inner class EpgChannelAdapter(private val services: List<Service>) :
        RecyclerView.Adapter<EpgChannelAdapter.VH>() {

        private val rowHeightPx: Int by lazy {
            requireContext().resources.getDimensionPixelSize(R.dimen.epg_row_height)
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivPicon: android.widget.ImageView = view.findViewById(R.id.iv_epg_picon)
            val tvName: TextView = view.findViewById(R.id.tv_epg_channel_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_epg_channel, parent, false)
            v.layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, rowHeightPx
            )
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val service = services[position]
            holder.tvName.text = service.name
            val piconUrl = if (service.piconPath != null) prefs.piconUrl(service.piconPath)
                           else prefs.piconFallbackUrl(service.ref)
            val piconUrlShort = prefs.piconFallbackUrlShort(service.ref)
            val piconUrlName = prefs.piconFallbackUrlByName(service.name)
            Glide.with(holder.ivPicon)
                .load(piconUrl)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(
                    Glide.with(holder.ivPicon)
                        .load(piconUrlShort)
                        .error(
                            Glide.with(holder.ivPicon)
                                .load(piconUrlName)
                                .placeholder(R.drawable.ic_channel_placeholder)
                                .error(R.drawable.ic_channel_placeholder)
                        )
                )
                .into(holder.ivPicon)
        }

        override fun getItemCount() = services.size
    }

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private lateinit var prefs: ReceiverPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bouquetRef = arguments?.getString(ARG_BOUQUET_REF) ?: ""
        if (bouquetRef.isNotBlank()) {
            epgViewModel.loadMultiEpg(bouquetRef)
        } else {
            val serviceRef = arguments?.getString(ARG_SERVICE_REF) ?: ""
            if (serviceRef.isNotBlank()) {
                epgViewModel.loadEpgForService(serviceRef)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_epg, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = ReceiverPreferences(requireContext())

        timeRuler = view.findViewById(R.id.time_ruler)
        epgGrid = view.findViewById(R.id.epg_grid)
        epgHScroll = view.findViewById(R.id.epg_hscroll)
        rulerHScroll = view.findViewById(R.id.ruler_hscroll)
        rvEpgChannels = view.findViewById(R.id.rv_epg_channels)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        tvSelectedTitle = view.findViewById(R.id.tv_selected_title)
        tvSelectedTime = view.findViewById(R.id.tv_selected_time)
        tvSelectedDesc = view.findViewById(R.id.tv_selected_desc)
        eventInfoBar = view.findViewById(R.id.event_info_bar)
        btnRecord = view.findViewById(R.id.btn_record)
        btnEpgSearch = view.findViewById(R.id.btn_epg_search)

        btnRecord.setOnClickListener { selectedEvent?.let { confirmRecord(it) } }
        btnEpgSearch.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_container, EpgSearchFragment())
                .addToBackStack(null)
                .commit()
        }

        // Sync time ruler scroll with grid scroll
        epgHScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            rulerHScroll.scrollTo(scrollX, 0)
        }

        // Set window start on the ruler
        timeRuler.windowStartMs = epgGrid.windowStartMs

        // EPG grid callbacks
        epgGrid.onEventSelected = { event -> updateInfoBar(event) }
        epgGrid.onEventClicked = { _, service -> launchPlayer(service) }
        epgGrid.onEventLongPressed = { event -> confirmRecord(event) }

        observeViewModel()
    }

    private fun observeViewModel() {
        epgViewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        }

        epgViewModel.epgMap.observe(viewLifecycleOwner) { epgMap ->
            val services = channelViewModel.channels.value ?: emptyList()
            populateEpg(services, epgMap)
        }

        // Single-service mode
        epgViewModel.serviceEpg.observe(viewLifecycleOwner) { events ->
            val serviceRef = arguments?.getString(ARG_SERVICE_REF) ?: ""
            val serviceName = arguments?.getString(ARG_SERVICE_NAME) ?: ""
            if (serviceRef.isNotBlank() && events.isNotEmpty()) {
                val service = Service(ref = serviceRef, name = serviceName)
                populateEpg(listOf(service), mapOf(serviceRef to events))
            }
        }
    }

    private fun populateEpg(services: List<Service>, epgMap: Map<String, List<EpgEvent>>) {
        // Channel name column
        rvEpgChannels.layoutManager = LinearLayoutManager(requireContext())
        rvEpgChannels.adapter = EpgChannelAdapter(services)

        // Grid
        epgGrid.setData(services, epgMap)
        updateInfoBar(epgGrid.getSelectedEvent())

        // Fetch timers and highlight recorded/scheduled events in red
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val timers = Enigma2Repository().getTimers()
                epgGrid.setTimers(timers)
            } catch (_: Exception) {}
        }

        // Sync vertical scroll between channel list and grid
        // (Both use internal scroll — wire them via touch/key events in the grid)
    }

    private fun updateInfoBar(event: EpgEvent?) {
        selectedEvent = event
        if (event == null) {
            eventInfoBar.visibility = View.GONE
            return
        }
        eventInfoBar.visibility = View.VISIBLE
        tvSelectedTitle.text = event.title
        tvSelectedTime.text = buildString {
            append(timeFmt.format(Date(event.beginMs)))
            append(" – ")
            append(timeFmt.format(Date(event.endMs)))
            append("  (${event.durationSeconds / 60} min)")
        }
        tvSelectedDesc.text = event.shortDesc?.takeIf { it.isNotBlank() }
            ?: event.longDesc?.takeIf { it.isNotBlank() }
            ?: ""
        // Show the Record button only for events that haven't ended yet
        btnRecord.visibility = if (event.endMs > System.currentTimeMillis()) View.VISIBLE else View.GONE
    }

    private fun confirmRecord(event: EpgEvent) {
        val timeStr = buildString {
            append(timeFmt.format(Date(event.beginMs)))
            append(" – ")
            append(timeFmt.format(Date(event.endMs)))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.record_confirm_title))
            .setMessage(getString(R.string.record_confirm_message, event.title, timeStr))
            .setPositiveButton(getString(R.string.record_confirm_ok)) { _, _ -> scheduleRecording(event) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun scheduleRecording(event: EpgEvent) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = Enigma2Repository().addTimer(event)
                val msg = if (result.result)
                    getString(R.string.record_scheduled_ok, event.title)
                else
                    getString(R.string.record_scheduled_fail, result.message ?: "")
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.record_scheduled_fail, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun launchPlayer(service: Service) {
        val streamUrl = prefs.streamUrl(service.ref)
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
            putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, service.name)
            putExtra(PlayerActivity.EXTRA_SERVICE_REF, service.ref)
        }
        startActivity(intent)
    }

    companion object {
        private const val ARG_BOUQUET_REF = "bouquet_ref"
        private const val ARG_SERVICE_REF = "service_ref"
        private const val ARG_SERVICE_NAME = "service_name"

        fun newInstance(
            serviceRef: String,
            serviceName: String,
            bouquetRef: String
        ): EpgFragment = EpgFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SERVICE_REF, serviceRef)
                putString(ARG_SERVICE_NAME, serviceName)
                putString(ARG_BOUQUET_REF, bouquetRef)
            }
        }
    }
}
