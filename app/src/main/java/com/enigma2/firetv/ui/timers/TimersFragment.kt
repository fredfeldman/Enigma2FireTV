package com.enigma2.firetv.ui.timers

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.enigma2.firetv.R
import com.enigma2.firetv.data.model.Timer
import com.enigma2.firetv.data.repository.Enigma2Repository
import kotlinx.coroutines.launch

/**
 * Shows all timers (scheduled recordings) on the receiver.
 * Long-press (or tap ✕) to delete a timer.
 */
class TimersFragment : Fragment() {

    private lateinit var rvTimers: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var btnRefresh: TextView
    private val adapter = TimerAdapter { timer -> confirmDelete(timer) }
    private val repository = Enigma2Repository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_timers, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvTimers = view.findViewById(R.id.rv_timers)
        loadingIndicator = view.findViewById(R.id.timers_loading)
        tvEmpty = view.findViewById(R.id.tv_timers_empty)
        btnRefresh = view.findViewById(R.id.btn_refresh_timers)

        rvTimers.layoutManager = LinearLayoutManager(requireContext())
        rvTimers.adapter = adapter

        btnRefresh.setOnClickListener { loadTimers() }

        loadTimers()
    }

    private fun loadTimers() {
        loadingIndicator.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val timers = repository.getTimers()
            loadingIndicator.visibility = View.GONE
            if (timers.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
            } else {
                // Sort: running first, then by begin time
                val sorted = timers.sortedWith(compareBy({ it.state != 2 }, { it.beginTimestamp }))
                adapter.submitList(sorted)
            }
        }
    }

    private fun confirmDelete(timer: Timer) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.timer_delete_title))
            .setMessage(getString(R.string.timer_delete_message, timer.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteTimer(timer) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteTimer(timer: Timer) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.deleteTimer(timer)
                if (result.result) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.timer_deleted_ok, timer.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadTimers()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.timer_deleted_fail, result.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.timer_deleted_fail, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
