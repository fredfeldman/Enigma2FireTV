package com.enigma2.firetv.ui.settings.receiver

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R

/**
 * v1.1.0 Phase 1 — landing list for Receiver Settings.
 *
 * 13 entries per spec; each opens a dedicated sub-fragment. Capability gating
 * is left to the sub-fragments themselves (each endpoint already returns
 * `runCatching { … }.getOrDefault(…)` on 404, so users see a graceful empty
 * state instead of a crash).
 */
class ReceiverSettingsHubFragment : Fragment() {

    private data class Entry(val title: Int, val subtitle: Int, val open: () -> Fragment)

    private val entries: List<Entry> = listOf(
        Entry(R.string.rs_power_title,           R.string.rs_power_subtitle)           { PowerFragment() },
        Entry(R.string.rs_audio_title,           R.string.rs_audio_subtitle)           { AudioVolumeFragment() },
        Entry(R.string.rs_recloc_title,          R.string.rs_recloc_subtitle)          { RecordingLocationsFragment() },
        Entry(R.string.rs_tuner_title,           R.string.rs_tuner_subtitle)           { TunerSignalFragment() },
        Entry(R.string.rs_parental_title,        R.string.rs_parental_subtitle)        { ParentalFragment() },
        Entry(R.string.rs_wol_title,             R.string.rs_wol_subtitle)             { WolSetupFragment() },
        Entry(R.string.rs_transcoding_title,     R.string.rs_transcoding_subtitle)     { TranscodingFragment() },
        Entry(R.string.rs_webui_title,           R.string.rs_webui_subtitle)           { OpenWebifUiFragment() },
        Entry(R.string.rs_allsettings_title,     R.string.rs_allsettings_subtitle)     { AllSettingsFragment() },
        Entry(R.string.rs_storage_title,         R.string.rs_storage_subtitle)         { StorageMountsFragment() },
        Entry(R.string.rs_log_title,             R.string.rs_log_subtitle)             { SystemLogFragment() },
        Entry(R.string.rs_plugins_title,         R.string.rs_plugins_subtitle)         { PluginManagerFragment() },
        Entry(R.string.rs_network_title,         R.string.rs_network_subtitle)         { NetworkInfoFragment() }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_receiver_settings_hub, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rv_receiver_hub)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = HubAdapter(entries) { entry ->
            ReceiverSettingsActivity.navigate(requireActivity(), entry.open())
        }
    }

    private class HubAdapter(
        private val items: List<Entry>,
        private val onClick: (Entry) -> Unit
    ) : RecyclerView.Adapter<HubVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HubVH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_receiver_settings_hub, parent, false)
            return HubVH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: HubVH, position: Int) {
            val e = items[position]
            holder.title.setText(e.title)
            holder.subtitle.setText(e.subtitle)
            holder.itemView.setOnClickListener { onClick(e) }
        }
    }

    private class HubVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: android.widget.TextView = itemView.findViewById(R.id.tv_item_title)
        val subtitle: android.widget.TextView = itemView.findViewById(R.id.tv_item_subtitle)
    }
}
