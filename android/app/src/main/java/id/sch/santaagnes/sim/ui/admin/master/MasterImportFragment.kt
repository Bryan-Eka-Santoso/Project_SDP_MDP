package id.sch.santaagnes.sim.ui.admin.master

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
import id.sch.santaagnes.sim.databinding.FragmentMasterImportBinding
import id.sch.santaagnes.sim.ui.common.ImportBodyView
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MasterImportFragment : Fragment() {

    private var _binding: FragmentMasterImportBinding? = null
    private val binding get() = _binding!!
    private lateinit var body: ImportBodyView

    private val viewModel: MasterImportViewModel by viewModels()

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
        _binding = FragmentMasterImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val isTeacher = viewModel.importType == MasterImportType.TEACHER
        binding.tvTitle.setText(
            if (isTeacher) R.string.import_master_title_teacher else R.string.import_master_title_student
        )
        body = ImportBodyView(
            binding = binding.body,
            info = if (isTeacher) R.string.import_teacher_info else R.string.import_student_info,
            columns = if (isTeacher) R.array.import_teacher_columns else R.array.import_student_columns
        )

        val suggestedFileName = if (isTeacher) TEACHER_TEMPLATE_NAME else STUDENT_TEMPLATE_NAME
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        body.setOnTemplateClick { saveTemplate.launch(suggestedFileName) }
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

    private fun render(state: MasterImportUiState) {
        binding.progress.isVisible = state.isUploading
        val successText = state.result?.let { res ->
            when (state.type) {
                MasterImportType.TEACHER -> getString(R.string.import_master_teacher_success, res.imported)
                MasterImportType.STUDENT -> if (res.assignedToClass > 0) {
                    getString(R.string.import_master_student_success_with_class, res.imported, res.assignedToClass)
                } else {
                    getString(R.string.import_master_student_success, res.imported)
                }
            }
        }
        body.render(
            isUploading = state.isUploading,
            canImport = state.canImport,
            fileUri = state.fileUri,
            fileName = state.fileName,
            successText = successText,
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
        const val ANY_FILE = "application/octet-stream"
        const val STUDENT_TEMPLATE_NAME = "template-siswa.xlsx"
        const val TEACHER_TEMPLATE_NAME = "template-guru.xlsx"
    }
}
