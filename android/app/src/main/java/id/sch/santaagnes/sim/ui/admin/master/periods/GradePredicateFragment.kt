package id.sch.santaagnes.sim.ui.admin.master.periods

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.DialogGradePredicateItemBinding
import id.sch.santaagnes.sim.databinding.FragmentGradePredicateBinding
import id.sch.santaagnes.sim.domain.model.GradePredicateItem
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianFormSheet
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GradePredicateFragment : Fragment() {

    private var _binding: FragmentGradePredicateBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GradePredicateViewModel by viewModels()

    private val adapter by lazy {
        GradePredicateAdapter(
            onEdit = { idx, item -> showItemSheet(idx, item) },
            onDelete = { idx -> viewModel.removeItem(idx) }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGradePredicateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvPredicates.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPredicates.adapter = adapter

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnAdd.setOnClickListener { showItemSheet(null, null) }
        binding.btnApplyDefault.setOnClickListener { viewModel.applyDefaultScale() }
        binding.btnSaveScale.setOnClickListener { viewModel.saveScale() }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.tvSubtitle.text = "Tahun Ajaran ${state.academicYearName.ifBlank { "-" }}"
                        binding.progressBar.isVisible = state.isLoading || state.isSaving
                        binding.tvEmptyState.isVisible = !state.isLoading && state.items.isEmpty()
                        binding.cardZeroWarning.isVisible =
                            !state.isLoading && state.items.isNotEmpty() && !state.hasZeroThreshold
                        binding.tvDirtyHint.isVisible = state.hasUnsavedChanges
                        binding.tvDirtyHint.text = "Ada perubahan yang belum disimpan ke server"
                        binding.btnSaveScale.isEnabled = state.canSave
                        binding.btnSaveScale.text = if (state.isSaving) {
                            "Menyimpan..."
                        } else {
                            getString(R.string.predicate_save_scale)
                        }
                        adapter.submitList(state.items)
                    }
                }

                launch {
                    viewModel.message.collect { msg ->
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showItemSheet(editingIndex: Int?, existing: GradePredicateItem?) {
        val d = DialogGradePredicateItemBinding.inflate(layoutInflater)
        if (existing != null) {
            d.etLabel.setText(existing.label)
            d.etMinScore.setText(GradePredicateViewModel.formatScore(existing.minScore))
            d.etDescription.setText(existing.description.orEmpty())
        }

        showAgnesianFormSheet(
            context = requireContext(),
            title = getString(
                if (existing == null) R.string.predicate_add_title else R.string.predicate_edit_title
            ),
            subtitle = "Contoh: predikat A dengan nilai minimum 90, dan wajib ada baris minimum 0",
            contentView = d.root,
            primaryLabel = if (existing == null) "Tambahkan" else "Perbarui"
        ) {
            val error = viewModel.upsertItem(
                editingIndex = editingIndex,
                rawLabel = d.etLabel.text?.toString().orEmpty(),
                rawMinScore = d.etMinScore.text?.toString().orEmpty(),
                rawDescription = d.etDescription.text?.toString().orEmpty()
            )
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
