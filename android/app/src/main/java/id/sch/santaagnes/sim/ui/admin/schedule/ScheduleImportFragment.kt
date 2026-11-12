package id.sch.santaagnes.sim.ui.admin.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentScheduleImportBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScheduleImportFragment : Fragment() {

    private var _binding: FragmentScheduleImportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleImportViewModel by viewModels()

    // Storage Access Framework: tanpa izin penyimpanan, hanya berkas yang dipilih pengguna yang terbaca.
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFilePicked(it.toString()) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnPick.setOnClickListener { pickFile.launch(arrayOf(XLSX, ANY_FILE)) }
        binding.btnImport.setOnClickListener { viewModel.import() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: ScheduleImportUiState) {
        binding.progress.isVisible = state.isUploading
        binding.btnImport.isEnabled = state.canImport
        binding.btnPick.isEnabled = !state.isUploading

        binding.tvFileName.isVisible = state.fileUri != null
        binding.tvFileName.text = state.fileName ?: getString(R.string.import_file_selected)

        binding.tvSuccess.isVisible = state.importedCount != null
        binding.tvSuccess.text = state.importedCount?.let { getString(R.string.import_success, it) }

        binding.tvError.isVisible = state.errorMessage != null
        binding.tvError.text = state.errorMessage
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        // Sebagian penyimpanan melaporkan .xlsx sebagai tipe umum; server tetap memeriksa isi berkas.
        const val ANY_FILE = "application/octet-stream"
    }
}
