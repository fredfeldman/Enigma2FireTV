package com.enigma2.firetv.ui.epg

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.ui.player.PlayerActivity
import com.enigma2.firetv.ui.viewmodel.ChannelViewModel
import com.enigma2.firetv.ui.viewmodel.EpgViewModel
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

    /** Simple single-column adapter that shows channel names aligned to EPG rows. */
    private inner class EpgChannelAdapter(private val services: List<Service>) :
        RecyclerView.Adapter<EpgChannelAdapter.VH>() {

        private val rowHeightPx: Int by lazy {
            requireContext().resources.getDimensionPixelSize(R.dimen.epg_row_height)
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tv: TextView = view as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, rowHeightPx
                )
                setPadding(16, 0, 16, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setTextColor(resources.getColor(R.color.text_primary, null))
                textSize = 12f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.tv.text = services[position].name
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

        // Sync time ruler scroll with grid scroll
        epgHScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            rulerHScroll.scrollTo(scrollX, 0)
        }

        // Set window start on the ruler
        timeRuler.windowStartMs = epgGrid.windowStartMs

        // EPG grid callbacks
        epgGrid.onEventSelected = { event -> updateInfoBar(event) }
        epgGrid.onEventClicked = { _, service -> launchPlayer(service) }

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

        // Sync vertical scroll between channel list and grid
        // (Both use internal scroll — wire them via touch/key events in the grid)
    }

    private fun updateInfoBar(event: EpgEvent?) {
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
