package com.enigma2.firetv.ui.epg

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.EpgEvent
import com.enigma2.firetv.data.prefs.ReceiverPreferences
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.ui.player.PlayerActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EPG search screen. Searches for events across all services matching the typed query.
 * Tapping a result launches [PlayerActivity] for that service.
 */
class EpgSearchFragment : Fragment() {

    private lateinit var etSearch: EditText
    private lateinit var btnSearch: TextView
    private lateinit var rvResults: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var prefs: ReceiverPreferences
    private val repository = Enigma2Repository()
    private val timeFmt = SimpleDateFormat("EEE dd MMM  HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_epg_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = ReceiverPreferences(requireContext())

        etSearch = view.findViewById(R.id.et_epg_search)
        btnSearch = view.findViewById(R.id.btn_epg_search_go)
        rvResults = view.findViewById(R.id.rv_epg_search_results)
        loadingIndicator = view.findViewById(R.id.epg_search_loading)
        tvEmpty = view.findViewById(R.id.tv_epg_search_empty)

        rvResults.layoutManager = LinearLayoutManager(requireContext())

        btnSearch.setOnClickListener { performSearch() }
        etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                performSearch()
                true
            } else false
        }

        // Request focus and show keyboard on open
        etSearch.requestFocus()
        etSearch.postDelayed({
            val imm = requireContext().getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun performSearch() {
        val query = etSearch.text?.toString()?.trim() ?: ""
        if (query.length < 2) {
            Toast.makeText(requireContext(), getString(R.string.search_too_short), Toast.LENGTH_SHORT).show()
            return
        }

        // Hide keyboard
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)

        loadingIndicator.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        rvResults.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val results = repository.searchEpg(query)
            loadingIndicator.visibility = View.GONE
            if (results.isEmpty()) {
                tvEmpty.text = getString(R.string.epg_search_no_results)
                tvEmpty.visibility = View.VISIBLE
            } else {
                rvResults.visibility = View.VISIBLE
                rvResults.adapter = SearchResultAdapter(results)
            }
        }
    }

    private inner class SearchResultAdapter(private val items: List<EpgEvent>) :
        RecyclerView.Adapter<SearchResultAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_search_result_title)
            val tvChannel: TextView = view.findViewById(R.id.tv_search_result_channel)
            val tvTime: TextView = view.findViewById(R.id.tv_search_result_time)
            val tvDesc: TextView = view.findViewById(R.id.tv_search_result_desc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_epg_search_result, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val event = items[position]
            holder.tvTitle.text = event.title
            holder.tvChannel.text = event.serviceName
            holder.tvTime.text = buildString {
                append(timeFmt.format(Date(event.beginMs)))
                append(" – ")
                append(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.endMs)))
            }
            holder.tvDesc.text = event.shortDesc?.takeIf { it.isNotBlank() }
                ?: event.longDesc?.takeIf { it.isNotBlank() }
                ?: ""

            holder.itemView.setOnClickListener {
                val nowMs = System.currentTimeMillis()
                if (event.beginMs <= nowMs && event.endMs > nowMs) {
                    // Currently airing – play live
                    val streamUrl = prefs.streamUrl(event.serviceRef)
                    startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_STREAM_URL, streamUrl)
                        putExtra(PlayerActivity.EXTRA_CHANNEL_NAME, event.serviceName)
                        putExtra(PlayerActivity.EXTRA_SERVICE_REF, event.serviceRef)
                    })
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.epg_search_not_live),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
