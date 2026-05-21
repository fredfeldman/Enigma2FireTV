package com.enigma2.firetv.ui.epg

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.model.Service
import com.enigma2.firetv.data.model.Timer
import com.enigma2.firetv.data.prefs.EpgReminder
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.prefs.RemindersStore
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.service.ReminderReceiver
import com.enigma2.firetv.ui.player.PlaybackRouter
import com.enigma2.firetv.ui.player.PlayerActivity
import com.enigma2.firetv.ui.epg.EpgSearchFragment
import com.enigma2.firetv.util.EpgExporter
import com.enigma2.firetv.BuildConfig
import com.enigma2.firetv.ui.epgassign.EpgAssignDialog
import com.enigma2.firetv.ui.viewmodel.ChannelViewModel
import com.enigma2.firetv.ui.viewmodel.EpgViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var btnEpgRefresh: TextView
    private lateinit var btnEpgExport: TextView
    private lateinit var tvCacheBanner: TextView

    private var selectedEvent: EpgEvent? = null

    /** v1.1.0: pending reminder waiting for POST_NOTIFICATIONS grant on Android 13+. */
    private var pendingReminder: EpgReminder? = null
    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pending = pendingReminder ?: return@registerForActivityResult
            pendingReminder = null
            commitReminder(pending)
            if (!granted && isAdded) {
                Toast.makeText(
                    requireContext(),
                    R.string.reminder_notif_permission_denied,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

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
        btnEpgRefresh = view.findViewById(R.id.btn_epg_refresh)
        btnEpgExport = view.findViewById(R.id.btn_epg_export)
        tvCacheBanner = view.findViewById(R.id.tv_cache_banner)

        btnRecord.setOnClickListener { selectedEvent?.let { confirmRecord(it) } }
        btnEpgSearch.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_container, EpgSearchFragment())
                .addToBackStack(null)
                .commit()
        }
        btnEpgRefresh.setOnClickListener { onRefreshClicked() }
        btnEpgExport.setOnClickListener { onExportClicked() }

        // Sync time ruler scroll with grid scroll
        epgHScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            rulerHScroll.scrollTo(scrollX, 0)
        }

        // Set window start on the ruler
        timeRuler.windowStartMs = epgGrid.windowStartMs

        // EPG grid callbacks
        epgGrid.onEventSelected = { event -> updateInfoBar(event) }
        epgGrid.onEventClicked = { _, service -> launchPlayer(service) }
        // v1.1.0: long-press opens a chooser with Record + Remind me (when future).
        epgGrid.onEventLongPressed = { event -> showEventActionsDialog(event) }

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

        // v1.1.0 cached-EPG banner
        epgViewModel.cacheBannerAgeMin.observe(viewLifecycleOwner) { ageMin ->
            if (ageMin >= 0) {
                tvCacheBanner.text = getString(R.string.epg_cache_banner, ageMin)
                tvCacheBanner.visibility = View.VISIBLE
            } else {
                tvCacheBanner.visibility = View.GONE
            }
        }
    }

    // ---------- v1.1.0 Phase 4: refresh + export ----------

    private fun onRefreshClicked() {
        val bouquetRef = arguments?.getString(ARG_BOUQUET_REF) ?: ""
        if (bouquetRef.isBlank()) {
            // Single-service mode: ask the receiver to refresh just this sref.
            val serviceRef = arguments?.getString(ARG_SERVICE_REF) ?: return
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { Enigma2Repository().refreshEpgForService(serviceRef) }
                if (isAdded) {
                    Toast.makeText(requireContext(), R.string.epg_refresh_requested, Toast.LENGTH_SHORT).show()
                    epgViewModel.loadEpgForService(serviceRef)
                }
            }
        } else {
            Toast.makeText(requireContext(), R.string.epg_refresh_requested, Toast.LENGTH_SHORT).show()
            epgViewModel.refreshEpg(bouquetRef)
        }
    }

    private fun onExportClicked() {
        val map = epgViewModel.epgMap.value.orEmpty()
        val services = channelViewModel.channels.value.orEmpty()
            .filter { map.containsKey(it.ref) && map[it.ref]?.isNotEmpty() == true }
        if (services.isEmpty()) {
            // Fall back to whatever single-service EPG we have on screen.
            val sref = arguments?.getString(ARG_SERVICE_REF).orEmpty()
            val sname = arguments?.getString(ARG_SERVICE_NAME).orEmpty()
            val single = epgViewModel.serviceEpg.value.orEmpty()
            if (sref.isBlank() || single.isEmpty()) {
                Toast.makeText(requireContext(), R.string.epg_export_no_channels, Toast.LENGTH_SHORT).show()
                return
            }
            showFormatPicker(sname.ifBlank { sref }, sref, single)
            return
        }
        val names = services.map { it.name.ifBlank { it.ref } }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.epg_export_dialog_title)
            .setItems(names) { _, which ->
                val svc = services[which]
                val events = map[svc.ref].orEmpty()
                if (events.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.epg_export_no_events, Toast.LENGTH_SHORT).show()
                } else {
                    showFormatPicker(svc.name.ifBlank { svc.ref }, svc.ref, events)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFormatPicker(
        channelName: String,
        channelRef: String,
        events: List<EpgEvent>
    ) {
        val formats = arrayOf(
            getString(R.string.epg_export_xmltv),
            getString(R.string.epg_export_json)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.epg_export_format_title)
            .setItems(formats) { _, which ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ctx = requireContext().applicationContext
                    val result = withContext(Dispatchers.IO) {
                        if (which == 0) EpgExporter.exportXmltv(ctx, channelName, channelRef, events)
                        else EpgExporter.exportJson(ctx, channelName, events)
                    }
                    if (!isAdded) return@launch
                    val msg = if (result.success)
                        getString(R.string.epg_export_ok, result.displayName)
                    else getString(R.string.epg_export_failed)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    // ---------- v1.1.0: long-press event options ----------

    private fun showEventActionsDialog(event: EpgEvent) {
        val isFuture = event.beginMs > System.currentTimeMillis()
        val store = RemindersStore(requireContext())
        val rid = store.newId(event.serviceRef, event.beginTimestamp)
        val alreadyReminded = store.all().any { it.id == rid }
        val actions = mutableListOf<String>()
        val handlers = mutableListOf<() -> Unit>()
        if (event.endMs > System.currentTimeMillis()) {
            actions += getString(R.string.action_record)
            handlers += { confirmRecord(event) }
        }
        if (isFuture) {
            if (alreadyReminded) {
                actions += getString(R.string.action_cancel_reminder)
                handlers += { cancelReminder(event, rid) }
            } else {
                actions += getString(R.string.action_remind_me)
                handlers += { addReminder(event, rid) }
            }
        }
        if (BuildConfig.ENABLE_EPG_ASSIGN) {
            actions += getString(R.string.epgassign_assign_channel)
            handlers += { EpgAssignDialog.show(requireContext(), viewLifecycleOwner, event.serviceRef, event.serviceName) }
        }
        if (actions.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.reminder_event_options)
            .setItems(actions.toTypedArray()) { _, which -> handlers[which].invoke() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addReminder(event: EpgEvent, rid: Int) {
        if (event.beginMs <= System.currentTimeMillis()) {
            Toast.makeText(requireContext(), R.string.reminder_past_event, Toast.LENGTH_SHORT).show()
            return
        }
        val reminder = EpgReminder(
            id = rid,
            title = event.title,
            channelName = event.serviceName,
            sref = event.serviceRef,
            startTimestampSec = event.beginTimestamp
        )
        // On Android 13+ POST_NOTIFICATIONS is runtime; without it the
        // alarm fires but the notification is silently dropped. Ask once,
        // schedule the reminder regardless of the user's choice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingReminder = reminder
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        commitReminder(reminder)
    }

    private fun commitReminder(reminder: EpgReminder) {
        RemindersStore(requireContext()).add(reminder)
        ReminderReceiver.schedule(requireContext(), reminder)
        if (!isAdded) return
        Toast.makeText(
            requireContext(),
            getString(R.string.reminder_added, reminder.title),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun cancelReminder(@Suppress("UNUSED_PARAMETER") event: EpgEvent, rid: Int) {
        RemindersStore(requireContext()).remove(rid)
        ReminderReceiver.cancel(requireContext(), rid)
        Toast.makeText(requireContext(), R.string.reminder_removed, Toast.LENGTH_SHORT).show()
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
            .setPositiveButton(getString(R.string.record_confirm_ok)) { _, _ -> checkConflictsAndSchedule(event) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * v1.1.0 conflict check: before scheduling, fetch existing timers and the
     * receiver's tuner count from `api/about`. If overlapping timers in the
     * proposed window meet or exceed the tuner count, warn the user once.
     */
    private fun checkConflictsAndSchedule(event: EpgEvent) {
        viewLifecycleOwner.lifecycleScope.launch {
            val repo = Enigma2Repository()
            val timers = runCatching { repo.getTimers() }.getOrDefault(emptyList())
            val tuners = runCatching {
                val raw = repo.getBoxInfo()?.get("tuners")
                when (raw) {
                    is Number -> raw.toInt()
                    is List<*> -> raw.size
                    is String -> raw.toIntOrNull() ?: 0
                    else -> 0
                }
            }.getOrDefault(0)
            val overlapping = timers.count { t ->
                t.disabled == 0 && t.beginMs < event.endMs && t.endMs > event.beginMs
            }
            if (!isAdded) return@launch
            if (tuners > 0 && overlapping >= tuners) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.record_conflict_title)
                    .setMessage(getString(R.string.record_conflict_message, overlapping, tuners))
                    .setPositiveButton(R.string.record_conflict_continue) { _, _ -> scheduleRecording(event) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                scheduleRecording(event)
            }
        }
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
        // Use PlaybackRouter so IPTV-type service refs (e.g. HDHomerun channels
        // added via M3U) have their real stream URL extracted before playback.
        PlaybackRouter.play(
            context = requireContext(),
            streamUrl = prefs.streamUrl(service.ref),
            channelName = service.name,
            serviceRef = service.ref
        )
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
