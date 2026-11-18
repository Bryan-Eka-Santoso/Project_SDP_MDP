package id.sch.santaagnes.sim.ui.admin.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import id.sch.santaagnes.sim.data.remote.ElectiveSurveyReportDto
import id.sch.santaagnes.sim.databinding.FragmentElectiveSurveyReportBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class ElectiveSurveyReportFragment : Fragment() {

    private var _binding: FragmentElectiveSurveyReportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ElectiveSurveyReportViewModel by viewModels()

    private var selectedStatus: String? = null
    private var renderedItems: List<ElectiveSurveyReportDto>? = null

    private val statusValues = listOf(
        null,
        "PUBLISHED",
        "CLOSED"
    )

    private val statusLabels = listOf(
        "Semua Status",
        "Diterbitkan",
        "Ditutup"
    )

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) {
            val resolver = requireContext().contentResolver

            viewModel.export {
                resolver.openOutputStream(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentElectiveSurveyReportBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        setupStatus()

        binding.btnFilter.setOnClickListener {
            terapkanFilter()
        }

        binding.btnReset.setOnClickListener {
            selectedStatus = null

            binding.etSurveyId.setText("")
            binding.etYearId.setText("")
            binding.etGrade.setText("")
            binding.etStatus.setText("Semua Status", false)

            viewModel.setFilter(null, null, null, null)
        }

        binding.btnRetry.setOnClickListener {
            viewModel.refresh()
        }

        binding.btnLoadMore.setOnClickListener {
            viewModel.loadMore()
        }

        binding.btnExport.setOnClickListener {
            val date = SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).format(Calendar.getInstance().time)

            exportLauncher.launch(
                "laporan-survei-mapel-$date.xlsx"
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.state.collect { state ->

                    binding.progress.isVisible =
                        state.loading || state.exporting

                    binding.btnExport.isEnabled =
                        !state.loading && !state.exporting

                    binding.tvError.isVisible =
                        state.error != null

                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null && !state.loading

                    binding.tvTotal.text =
                        state.total.toString()

                    binding.tvLoaded.text =
                        "Menampilkan ${state.items.size} " +
                                "dari ${state.total} data mata pelajaran"

                    binding.tvEmpty.isVisible =
                        !state.loading &&
                                state.error == null &&
                                state.items.isEmpty()

                    binding.btnLoadMore.isVisible =
                        !state.loading &&
                                state.error == null &&
                                state.page < state.totalPages

                    if (renderedItems != state.items) {
                        renderedItems = state.items
                        tampilkanLaporan(state.items)
                    }

                    state.message?.let { message ->
                        viewModel.clearMessage()

                        MaterialAlertDialogBuilder(
                            requireContext()
                        )
                            .setTitle("Berhasil")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }

        viewModel.refresh()
    }

    private fun setupStatus() {
        binding.etStatus.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                statusLabels
            )
        )

        binding.etStatus.setText("Semua Status", false)

        binding.etStatus.setOnItemClickListener { _, _, position, _ ->
            selectedStatus = statusValues[position]
        }
    }

    private fun terapkanFilter() {
        val surveyText = binding.etSurveyId.text
            ?.toString()?.trim().orEmpty()

        val yearText = binding.etYearId.text
            ?.toString()?.trim().orEmpty()

        val surveyId = surveyText.toIntOrNull()
        val yearId = yearText.toIntOrNull()

        if (
            surveyText.isNotEmpty() &&
            (surveyId == null || surveyId <= 0)
        ) {
            binding.etSurveyId.error = "ID survei tidak valid"
            return
        }

        if (
            yearText.isNotEmpty() &&
            (yearId == null || yearId <= 0)
        ) {
            binding.etYearId.error = "ID tahun ajaran tidak valid"
            return
        }

        val grade = binding.etGrade.text
            ?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }

        viewModel.setFilter(
            surveyId = surveyId,
            academicYearId = yearId,
            gradeLevel = grade,
            status = selectedStatus
        )
    }

    private fun tampilkanLaporan(
        items: List<ElectiveSurveyReportDto>
    ) {
        binding.listContainer.removeAllViews()

        items.forEach { item ->
            val card = MaterialCardView(requireContext()).apply {
                radius = dp(12).toFloat()
                cardElevation = dp(1).toFloat()

                setCardBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.white
                    )
                )

                layoutParams = LinearLayout.LayoutParams(
                    -1,
                    -2
                ).apply {
                    bottomMargin = dp(12)
                }
            }

            val content = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(16), dp(16),
                    dp(16), dp(16)
                )
            }

            fun addText(
                value: String,
                size: Float = 13f,
                bold: Boolean = false
            ) {
                val tv = TextView(requireContext()).apply {
                    text = value
                    textSize = size
                    setPadding(0, 0, 0, dp(7))

                    setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.agnesian_text
                        )
                    )

                    if (bold) {
                        setTypeface(
                            null,
                            android.graphics.Typeface.BOLD
                        )
                    }
                }

                content.addView(tv)
            }

            addText(item.subjectName, 16f, true)
            addText("Kode: ${item.subjectCode}")

            addText(
                "Jumlah Peminat: ${item.studentCount} siswa",
                14f,
                true
            )

            addText(
                "Persentase: ${item.percentage}%"
            )

            addText(
                "Total Responden Survei: ${item.respondentCount}"
            )

            addText("Survei: ${item.surveyTitle}")

            addText(
                "Tahun Ajaran: ${item.academicYearName}"
            )

            addText("Tingkat: ${item.gradeLevel}")

            addText(
                "Periode: ${item.openDate} - ${item.closeDate}"
            )

            addText(
                "Status: ${
                    if (item.status == "CLOSED") {
                        "Ditutup"
                    } else {
                        "Diterbitkan"
                    }
                }"
            )

            card.addView(content)
            binding.listContainer.addView(card)
        }
    }

    private fun dp(value: Int): Int {
        return (
                value * resources.displayMetrics.density
                ).toInt()
    }

    override fun onDestroyView() {
        renderedItems = null
        _binding = null
        super.onDestroyView()
    }
}
