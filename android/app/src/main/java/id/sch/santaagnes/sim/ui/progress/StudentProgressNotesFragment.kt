package id.sch.santaagnes.sim.ui.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.databinding.FragmentStudentProgressNotesBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StudentProgressNotesFragment: Fragment() {
    private var _binding: FragmentStudentProgressNotesBinding? = null
    private val binding get() = _binding!!
    private val vm: StudentProgressNotesViewModel by viewModels()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStudentProgressNotesBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnNoteBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnNotesRetry.setOnClickListener { vm.refresh() }
        binding.btnNotesMore.setOnClickListener { vm.more() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    binding.progressNotes.isVisible = state.loading
                    binding.btnNotesRetry.isVisible = state.error != null
                    binding.btnNotesMore.isVisible = state.more
                    binding.tvNotesStatus.text = state.error ?: when {
                        state.loading && state.items.isEmpty() -> "Memuat catatan..."
                        state.items.isEmpty() -> "Belum ada catatan perkembangan dari guru."
                        else -> "${state.total} catatan perkembangan"
                    }
                    binding.listNotes.removeAllViews()
                    state.items.forEach { addProgressNoteCard(binding.listNotes, it, teacher = false) }
                }
            }
        }
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
