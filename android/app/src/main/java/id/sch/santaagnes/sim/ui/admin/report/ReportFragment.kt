package id.sch.santaagnes.sim.ui.admin.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.FragmentReportBinding
import kotlinx.coroutines.launch

/**
 * Kerangka bersama layar laporan admin: header + tombol Ekspor Excel (F-LAP-11), total, daftar kartu
 * berpaginasi, serta state kosong dan error. Turunannya mengisi adapter dan widget filter.
 */
abstract class ReportFragment<R : Any, F : Any> : Fragment() {

    private var _binding: FragmentReportBinding? = null
    protected val binding get() = _binding!!

    protected abstract val viewModel: ReportViewModel<R, F>
    protected abstract val adapter: ListAdapter<R, *>

    /** Judul layar dan kunci jenis laporan untuk nama berkas ekspor (sama dengan segmen `/reports/:type`). */
    protected abstract val titleRes: Int
    protected abstract val reportType: String

    /** Teks total dengan satu argumen jumlah, mis. "12 perubahan". */
    protected abstract val totalRes: Int

    protected abstract fun setupFilters()

    protected abstract fun renderFilters(state: ReportUiState<R, F>)

    protected open fun releaseFilters() = Unit

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME)
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri.toString())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvTitle.setText(titleRes)

        val layoutManager = GridLayoutManager(requireContext(), resources.getInteger(R.integer.report_columns))
        binding.rvReport.layoutManager = layoutManager
        binding.rvReport.adapter = adapter
        binding.rvReport.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_THRESHOLD) {
                    viewModel.loadMore()
                }
            }
        })

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }
        binding.btnExport.setOnClickListener {
            exportLauncher.launch("laporan-$reportType-${DateTimeWib.today()}.xlsx")
        }
        setupFilters()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Muat ulang tiap layar tampil agar perubahan jadwal yang baru dibuat langsung ikut terlihat.
        viewModel.refresh()
    }

    private fun render(state: ReportUiState<R, F>) {
        binding.progress.isVisible = state.isLoading || state.isLoadingMore || state.isExporting
        binding.btnExport.isEnabled = !state.isExporting
        binding.errorBanner.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
        binding.tvEmpty.isVisible = state.isEmpty
        binding.tvTotal.isVisible = state.rows.isNotEmpty()
        binding.tvTotal.text = getString(totalRes, state.total)
        adapter.submitList(state.rows)
        renderFilters(state)
    }

    private fun onEvent(event: ReportEvent) {
        val message = when (event) {
            ReportEvent.ExportSaved -> getString(R.string.report_export_saved)
            ReportEvent.ExportWriteFailed -> getString(R.string.report_export_write_failed)
            is ReportEvent.ExportFailed -> event.message
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        binding.rvReport.adapter = null
        releaseFilters()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 3
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
