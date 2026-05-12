package com.enigma2.firetv.ui.epgimport

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
import com.enigma2.firetv.data.repository.Enigma2Repository
import com.enigma2.firetv.util.ApiErrors
import kotlinx.coroutines.launch

/**
 * Read-only viewer for EPGImport `*.sources.xml` files installed in
 * `/etc/epgimport/` on the receiver. Shows the list of files; tapping one
 * opens [EpgImportDetailFragment] to view its categories and sources.
 *
 * The EPGImport plugin itself doesn't expose an HTTP API — we rely on
 * OpenWebif's generic `/file` controller to enumerate and download the XML
 * source files.
 */
class EpgImportFragment : Fragment() {

    private lateinit var rv: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var empty: TextView
    private lateinit var btnRefresh: TextView

    private val repo = Enigma2Repository()
    private val adapter = EpgImportFilesAdapter { path -> openDetail(path) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_epgimport, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rv_epi_files)
        loading = view.findViewById(R.id.epi_loading)
        empty = view.findViewById(R.id.tv_epi_empty)
        btnRefresh = view.findViewById(R.id.btn_epi_refresh)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        btnRefresh.setOnClickListener { load() }
        load()
    }

    private fun load() {
        loading.visibility = View.VISIBLE
        empty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val files = try {
                repo.listEpgImportSourceFiles()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    ApiErrors.userMessage(requireContext(), e),
                    Toast.LENGTH_SHORT
                ).show()
                emptyList()
            }
            loading.visibility = View.GONE
            empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
            adapter.submit(files)
        }
    }

    private fun openDetail(path: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.epgimport_container, EpgImportDetailFragment.newInstance(path))
            .addToBackStack(null)
            .commit()
    }
}
