package id.sch.santaagnes.sim.ui.admin.survei

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.*
import id.sch.santaagnes.sim.databinding.FragmentSurveiMinatBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class SurveiMinatFragment : Fragment() {

    private var _binding: FragmentSurveiMinatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SurveiMinatViewModel by viewModels()

    private var rendered: List<SurveiMinatDto>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurveiMinatBinding.inflate(
            inflater,
            container,
            false
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

        binding.btnNew.setOnClickListener {
            showCreateDialog()
        }

        binding.btnRetry.setOnClickListener {
            viewModel.refresh()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.state.collect { state ->

                    binding.progress.isVisible = state.loading

                    binding.btnNew.isEnabled = !state.loading

                    binding.tvError.isVisible =
                        state.error != null

                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null

                    binding.tvTotal.text =
                        state.surveys.size.toString()

                    binding.tvEmpty.isVisible =
                        !state.loading &&
                                state.error == null &&
                                state.surveys.isEmpty()

                    if (rendered != state.surveys) {
                        rendered = state.surveys
                        renderList(state.surveys)
                    }

                    state.recap?.let {
                        showRecap(it)
                        viewModel.clearRecap()
                    }

                    state.message?.let {
                        MaterialAlertDialogBuilder(
                            requireContext()
                        )
                            .setMessage(it)
                            .setPositiveButton("OK", null)
                            .show()

                        viewModel.clearMessage()
                    }
                }
            }
        }

        viewModel.refresh()
    }

    private fun renderList(
        surveys: List<SurveiMinatDto>
    ) {
        binding.listContainer.removeAllViews()

        surveys.forEach { item ->

            val card = MaterialCardView(requireContext()).apply {
                radius = dp(12).toFloat()
                cardElevation = dp(1).toFloat()

                setCardBackgroundColor(
                    resources.getColor(
                        R.color.white,
                        requireContext().theme
                    )
                )

                val params = LinearLayout.LayoutParams(
                    -1,
                    -2
                )

                params.bottomMargin = dp(12)
                layoutParams = params
            }

            val content = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(16),
                    dp(14),
                    dp(16),
                    dp(14)
                )
            }

            fun addText(
                value: String,
                size: Float = 13f
            ) {
                val textView = TextView(requireContext()).apply {
                    text = value
                    textSize = size

                    setTextColor(
                        resources.getColor(
                            R.color.agnesian_text,
                            requireContext().theme
                        )
                    )
                }

                content.addView(textView)
            }

            addText(item.title, 16f)

            addText(
                "${item.academicYearName ?: "-"} • Kelas ${item.gradeLevel}"
            )

            addText(
                "${item.openDate} s.d. ${item.closeDate}"
            )

            val statusLabel = when (item.status) {
                "DRAFT" -> "Draft"
                "PUBLISHED" -> "Diterbitkan"
                "CLOSED" -> "Ditutup"
                else -> item.status
            }

            addText(
                "Status: $statusLabel • ${item.responseCount} pilihan siswa"
            )

            val actions = LinearLayout(
                requireContext()
            ).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            fun addAction(
                label: String,
                listener: () -> Unit
            ) {
                val button = MaterialButton(
                    requireContext()
                ).apply {
                    text = label
                    textSize = 11f
                    isAllCaps = false

                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1f
                    )

                    setOnClickListener {
                        listener()
                    }
                }

                actions.addView(button)
            }

            if (item.status == "DRAFT") {
                addAction("Terbitkan") {
                    confirm("Terbitkan survei ini?") {
                        viewModel.publish(item.id)
                    }
                }
            }

            if (item.status == "PUBLISHED") {
                addAction("Tutup") {
                    confirm("Tutup survei ini?") {
                        viewModel.close(item.id)
                    }
                }
            }

            addAction("Lihat Rekap") {
                viewModel.recap(item.id)
            }

            content.addView(actions)
            card.addView(content)

            binding.listContainer.addView(card)
        }
    }

    private fun confirm(
        message: String,
        onYes: () -> Unit
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Konfirmasi")
            .setMessage(message)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Ya") { _, _ ->
                onYes()
            }
            .show()
    }

    private fun showCreateDialog() {
        val lookups = viewModel.state.value.lookups ?: return

        if (lookups.academicYears.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage("Tahun ajaran belum tersedia.")
                .setPositiveButton("OK", null)
                .show()

            return
        }

        val container = LinearLayout(
            requireContext()
        ).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(6), dp(20), 0)
        }

        fun addEditText(hintText: String): EditText {
            return EditText(requireContext()).also {
                it.hint = hintText
                container.addView(it)
            }
        }

        val etTitle = addEditText("Judul survei")

        val spinnerYears = Spinner(requireContext())

        spinnerYears.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            lookups.academicYears.map { it.name }
        )

        container.addView(spinnerYears)

        val activeIndex = lookups.academicYears.indexOfFirst {
            it.isActive
        }

        if (activeIndex >= 0) {
            spinnerYears.setSelection(activeIndex)
        }

        val etGrade = addEditText(
            "Tingkat kelas (contoh: 10)"
        )

        val etOpen = addEditText(
            "Tanggal buka: YYYY-MM-DD"
        )

        val etClose = addEditText(
            "Tanggal tutup: YYYY-MM-DD"
        )

        listOf(etOpen, etClose).forEach { input ->

            input.isFocusable = false

            input.setOnClickListener {
                val calendar = Calendar.getInstance()

                DatePickerDialog(
                    requireContext(),
                    { _, year, month, day ->

                        val date = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            year,
                            month + 1,
                            day
                        )

                        input.setText(date)
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        val dialog = MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle("Buat Survei Minat")
            .setView(container)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan Draft", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                android.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val title = etTitle.text.toString().trim()
                val grade = etGrade.text.toString().trim()
                val openDate = etOpen.text.toString()
                val closeDate = etClose.text.toString()

                when {
                    title.length !in 5..100 -> {
                        etTitle.error = "Judul 5–100 karakter"
                    }

                    grade.isEmpty() -> {
                        etGrade.error = "Isi tingkat kelas"
                    }

                    openDate.isBlank() -> {
                        etOpen.error = "Pilih tanggal buka"
                    }

                    closeDate.isBlank() -> {
                        etClose.error = "Pilih tanggal tutup"
                    }

                    closeDate < openDate -> {
                        etClose.error =
                            "Tanggal tutup harus setelah tanggal buka"
                    }

                    else -> {
                        viewModel.create(
                            BuatSurveiRequest(
                                academicYearId =
                                    lookups.academicYears[
                                        spinnerYears.selectedItemPosition
                                    ].id,
                                gradeLevel = grade,
                                title = title,
                                openDate = openDate,
                                closeDate = closeDate
                            )
                        )

                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showRecap(
        recap: RekapSurveiDto
    ) {
        val summary = buildString {

            append(
                "Responden: ${recap.respondentCount} siswa\n"
            )

            append(
                "Total pilihan: ${recap.selectionCount}\n\n"
            )

            if (recap.subjects.isEmpty()) {
                append("Belum ada jawaban survei.")
            }

            recap.subjects.forEachIndexed { index, subject ->
                append(
                    "${index + 1}. ${subject.name} " +
                            "(${subject.code}): " +
                            "${subject.studentCount} siswa\n"
                )
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rekap: ${recap.survey.title}")
            .setMessage(summary)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun dp(value: Int): Int {
        return (
                value * resources.displayMetrics.density
                ).toInt()
    }

    override fun onDestroyView() {
        rendered = null
        _binding = null
        super.onDestroyView()
    }
}
