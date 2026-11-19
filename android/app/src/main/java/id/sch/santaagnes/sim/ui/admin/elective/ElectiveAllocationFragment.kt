package id.sch.santaagnes.sim.ui.admin.elective

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.ElectivePreviewClassDto
import id.sch.santaagnes.sim.databinding.FragmentElectiveAllocationBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ElectiveAllocationFragment : Fragment() {
    private var _binding: FragmentElectiveAllocationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ElectiveAllocationViewModel by viewModels()
    private var renderedOptions: List<ElectiveOption> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentElectiveAllocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnElectiveBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnElectiveRetry.setOnClickListener { viewModel.loadPackages() }
        binding.btnElectivePreview.setOnClickListener {
            viewModel.setGrade(binding.inputElectiveGrade.text?.toString().orEmpty())
            viewModel.loadPreview()
        }
        binding.btnElectiveApply.setOnClickListener {
            val plan = viewModel.state.value.preview ?: return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Konfirmasi alokasi paket")
                .setMessage("Buat ${plan.totalClasses} kelas untuk ${plan.totalStudents} siswa paket ${plan.packageName} (${plan.gradeLevel})? Tindakan ini menyimpan kelas dan anggota ke database.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Jalankan alokasi") { _, _ -> viewModel.applyPreview() }
                .show()
        }
        binding.spinnerElectivePackage.setOnItemClickListener { _, _, index, _ ->
            viewModel.selectPackage(index)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(s: ElectiveAllocationState) {
        if (s.options != renderedOptions) {
            renderedOptions = s.options
            val labels = s.options.map { it.label }
            binding.spinnerElectivePackage.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
            )
            binding.spinnerElectivePackage.setText(s.selected?.label.orEmpty(), false)
        }
        binding.progressElective.isVisible = s.loading || s.applying
        binding.tvElectiveError.isVisible = s.error != null
        binding.tvElectiveError.text = s.error
        binding.btnElectiveRetry.isVisible = s.error != null && s.options.isEmpty()
        binding.tvElectiveEmpty.isVisible = !s.loading && s.options.isEmpty() && s.error == null
        binding.btnElectivePreview.isEnabled = !s.loading && !s.applying && s.selected != null
        binding.spinnerElectivePackage.isEnabled = !s.loading && !s.applying
        binding.inputElectiveGrade.isEnabled = !s.loading && !s.applying
        val plan = s.preview
        binding.cardElectiveResult.isVisible = plan != null
        binding.btnElectiveApply.isVisible = plan != null && plan.totalStudents > 0 && plan.totalClasses > 0
        binding.btnElectiveApply.isEnabled = !s.applying
        if (plan != null) {
            binding.tvElectiveSummary.text = "${plan.packageName} • ${plan.academicYearName}\nTingkat: ${plan.gradeLevel}  |  Kapasitas/kelas: ${plan.capacity}\n${plan.totalStudents} siswa → ${plan.totalClasses} kelas"
            binding.tvElectiveNoStudents.isVisible = plan.totalStudents == 0
            binding.electiveClassList.removeAllViews()
            plan.classes.forEach { renderClass(it) }
        }
        binding.cardElectiveApplied.isVisible = s.applied != null
        if (s.applied != null) {
            binding.tvElectiveApplied.text = "Kelas berhasil dibuat:\n" + s.applied.classes.joinToString("\n") { "${it.name} — ${it.studentCount} siswa" }
        }
        s.message?.let { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    private fun renderClass(item: ElectivePreviewClassDto) {
        val context = requireContext()
        val dp = resources.displayMetrics.density
        fun dip(n: Int) = (n * dp).toInt()
        val card = MaterialCardView(context).apply {
            radius = dip(12).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.WHITE)
            strokeWidth = dip(1)
            strokeColor = androidx.core.content.ContextCompat.getColor(context, R.color.agnesian_tile)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dip(10)
            }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(14), dip(12), dip(14), dip(12))
        }
        fun text(value: String, bold: Boolean = false): TextView = TextView(context).apply {
            this.text = value
            textSize = if (bold) 15f else 13f
            setTextColor(androidx.core.content.ContextCompat.getColor(context, if (bold) R.color.agnesian_text else R.color.agnesian_muted))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        content.addView(text("${item.name}  •  ${item.students.size}/${item.capacity} siswa", true))
        item.students.forEach { student ->
            val row = text("${student.studentName}  •  ${student.nis ?: "NIS tidak ada"}")
            row.setPadding(0, dip(7), 0, 0)
            content.addView(row)
        }
        card.addView(content)
        binding.electiveClassList.addView(card)
    }

    override fun onDestroyView() {
        renderedOptions = emptyList()
        _binding = null
        super.onDestroyView()
    }
}
