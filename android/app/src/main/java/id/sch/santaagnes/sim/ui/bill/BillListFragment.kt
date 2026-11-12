package id.sch.santaagnes.sim.ui.bill

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentBillListBinding
import id.sch.santaagnes.sim.domain.model.BillFilter
import id.sch.santaagnes.sim.domain.model.BillScopeOption
import kotlinx.coroutines.launch

/** Daftar tagihan (F-BYR-02): siswa, wali murid (per anak), wali kelas (per kelas binaan), dan admin. */
@AndroidEntryPoint
class BillListFragment : Fragment() {

    private var _binding: FragmentBillListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillListViewModel by viewModels()
    private val adapter = BillAdapter()

    private val filters = BillFilter.entries
    private var shownOptions: List<BillScopeOption>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvBills.layoutManager = layoutManager
        binding.rvBills.adapter = adapter
        binding.rvBills.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_THRESHOLD) {
                    viewModel.loadMore()
                }
            }
        })

        buildFilterChips()
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnImport.setOnClickListener { findNavController().navigate(R.id.action_bills_to_import) }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }
        binding.inputSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(binding.inputSearch.text.toString())
                true
            } else {
                false
            }
        }
        // Tombol hapus pada kolom cari mengembalikan daftar lengkap tanpa menunggu tombol cari.
        binding.layoutSearch.setEndIconOnClickListener {
            binding.inputSearch.text?.clear()
            viewModel.search("")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Muat ulang tiap layar tampil agar hasil impor rekap baru langsung terlihat.
        viewModel.refresh()
    }

    private fun buildFilterChips() {
        filters.forEach { filter ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                setText(
                    when (filter) {
                        BillFilter.ALL -> R.string.bill_filter_all
                        BillFilter.OVERDUE -> R.string.bill_filter_overdue
                        BillFilter.PAID -> R.string.bill_filter_paid
                    }
                )
                isCheckable = true
                setOnClickListener { viewModel.selectFilter(filter) }
            }
            binding.chipFilter.addView(chip)
        }
    }

    private fun render(state: BillListUiState) {
        binding.progress.isVisible = state.isLoading || state.isLoadingMore
        binding.btnImport.isVisible = state.canImport
        binding.layoutSearch.isVisible = state.showStudent
        binding.errorBanner.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
        binding.tvEmpty.isVisible = state.isEmpty || state.hasNoAccess
        binding.tvEmpty.setText(if (state.hasNoAccess) R.string.bill_no_access else R.string.bill_empty)
        (binding.chipFilter.getChildAt(filters.indexOf(state.filter)) as? Chip)?.isChecked = true
        renderScopeChips(state)
        adapter.submitList(state.bills)
    }

    private fun renderScopeChips(state: BillListUiState) {
        binding.scrollScope.isVisible = state.options.size > 1
        if (state.options != shownOptions) {
            shownOptions = state.options
            binding.chipScope.removeAllViews()
            state.options.forEachIndexed { index, option ->
                val chip = Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = option.name
                    isCheckable = true
                    setOnClickListener { viewModel.selectOption(index) }
                }
                binding.chipScope.addView(chip)
            }
        }
        (binding.chipScope.getChildAt(state.selectedOption) as? Chip)?.isChecked = true
    }

    override fun onDestroyView() {
        binding.rvBills.adapter = null
        shownOptions = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 3
    }
}
