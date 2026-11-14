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
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentScheduleImportBinding
import id.sch.santaagnes.sim.ui.common.ImportBodyView
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScheduleImportFragment : Fragment() {

    private var _binding: FragmentScheduleImportBinding? = null
    private val binding get() = _binding!!
    private lateinit var body: ImportBodyView

    private val viewModel: ScheduleImportViewModel by viewModels()

    // Storage Access Framework: tanpa izin penyimpanan, hanya berkas yang dipilih pengguna yang terbaca.
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFilePicked(it.toString()) }
    }

    private val saveTemplate = registerForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri ->
        uri?.let { viewModel.onTemplateDestinationPicked(it.toString()) }
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
        body = ImportBodyView(binding.body, R.string.import_info, R.array.import_schedule_columns)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        body.setOnTemplateClick { saveTemplate.launch(TEMPLATE_NAME) }
        body.setOnPickClick { pickFile.launch(arrayOf(XLSX, ANY_FILE)) }
        body.setOnImportClick { viewModel.import() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.templateSaved.collect { saved ->
                        Snackbar.make(
                            binding.root,
                            if (saved) R.string.import_template_saved else R.string.import_template_failed,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun render(state: ScheduleImportUiState) {
        binding.progress.isVisible = state.isUploading
        body.render(
            isUploading = state.isUploading,
            canImport = state.canImport,
            fileUri = state.fileUri,
            fileName = state.fileName,
            successText = state.importedCount?.let { getString(R.string.import_success, it) },
            errorMessage = state.errorMessage,
            importErrors = state.importErrors,
            errorRowCount = state.errorRowCount
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        // Sebagian penyimpanan melaporkan .xlsx sebagai tipe umum; server tetap memeriksa isi berkas.
        const val ANY_FILE = "application/octet-stream"
        const val TEMPLATE_NAME = "template-jadwal.xlsx"
    }
}
