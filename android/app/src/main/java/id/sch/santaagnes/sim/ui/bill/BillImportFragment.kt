package id.sch.santaagnes.sim.ui.bill

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
import id.sch.santaagnes.sim.databinding.FragmentBillImportBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BillImportFragment : Fragment() {

    private var _binding: FragmentBillImportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillImportViewModel by viewModels()

    // Storage Access Framework: tanpa izin penyimpanan, hanya berkas yang dipilih pengguna yang terbaca.
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFilePicked(it.toString()) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillImportBinding.inflate(inflater, container, false)
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

    private fun render(state: BillImportUiState) {
        binding.progress.isVisible = state.isUploading
        binding.btnImport.isEnabled = state.canImport
        binding.btnPick.isEnabled = !state.isUploading

        binding.tvFileName.isVisible = state.fileUri != null
        binding.tvFileName.text = state.fileName ?: getString(R.string.import_file_selected)

        val result = state.result
        binding.tvSuccess.isVisible = result != null
        binding.tvSuccess.text = result?.let {
            getString(R.string.bill_import_success, it.created, it.updated, it.unchanged, it.notifiedStudents)
        }

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
