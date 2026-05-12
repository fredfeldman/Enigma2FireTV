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
 * Shows the categories and individual sources contained in a single
 * `*.sources.xml` file. Read-only.
 */
class EpgImportDetailFragment : Fragment() {

    private lateinit var rv: RecyclerView
    private lateinit var loading: ProgressBar
    private lateinit var empty: TextView
    private lateinit var title: TextView
    private lateinit var pathView: TextView
    private lateinit var btnBack: TextView

    private val repo = Enigma2Repository()
    private val adapter = EpgImportDetailAdapter()

    private lateinit var path: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = requireArguments().getString(ARG_PATH).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_epgimport_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rv_epi_detail)
        loading = view.findViewById(R.id.epi_detail_loading)
        empty = view.findViewById(R.id.tv_epi_detail_empty)
        title = view.findViewById(R.id.tv_epi_detail_title)
        pathView = view.findViewById(R.id.tv_epi_detail_path)
        btnBack = view.findViewById(R.id.btn_epi_back)

        title.text = path.substringAfterLast('/').removeSuffix(".sources.xml")
        pathView.text = path

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        load()
    }

    private fun load() {
        loading.visibility = View.VISIBLE
        empty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val rows: List<EpgImportDetailAdapter.Row> = try {
                val file = repo.getEpgImportSourcesFile(path)
                buildRows(file.categories)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    ApiErrors.userMessage(requireContext(), e),
                    Toast.LENGTH_SHORT
                ).show()
                emptyList()
            }
            loading.visibility = View.GONE
            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            adapter.submit(rows)
        }
    }

    private fun buildRows(
        categories: List<com.enigma2.firetv.data.model.EpgImportCategory>
    ): List<EpgImportDetailAdapter.Row> {
        val out = mutableListOf<EpgImportDetailAdapter.Row>()
        for (cat in categories) {
            // Skip an empty header for the "uncategorised" bucket so it just
            // flows naturally at the top.
            if (cat.name.isNotBlank()) {
                out += EpgImportDetailAdapter.Row.Header(cat.name)
            }
            cat.sources.forEach { out += EpgImportDetailAdapter.Row.Source(it) }
        }
        return out
    }

    companion object {
        private const val ARG_PATH = "path"

        fun newInstance(path: String): EpgImportDetailFragment =
            EpgImportDetailFragment().apply {
                arguments = Bundle().apply { putString(ARG_PATH, path) }
            }
    }
}
