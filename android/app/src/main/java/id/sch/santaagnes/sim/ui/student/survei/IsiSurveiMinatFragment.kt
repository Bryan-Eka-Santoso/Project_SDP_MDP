package id.sch.santaagnes.sim.ui.student.survei

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.*
import id.sch.santaagnes.sim.databinding.FragmentIsiSurveiMinatBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class IsiSurveiMinatFragment : Fragment() {

    private var _binding: FragmentIsiSurveiMinatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: IsiSurveiMinatViewModel by viewModels()

    private var renderedSurveys: List<SurveiSiswaDto>? = null
    private var renderedDetailId: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIsiSurveiMinatBinding.inflate(
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
            if (viewModel.state.value.detail != null) {
                viewModel.kembaliKeDaftar()
            } else {
                findNavController().navigateUp()
            }
        }

        binding.btnRetry.setOnClickListener {
            viewModel.retry()
        }

        binding.btnSubmit.setOnClickListener {
            konfirmasiKirim()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.state.collect { state ->

                    binding.progress.isVisible =
                        state.loading || state.saving

                    binding.tvError.isVisible =
                        state.error != null

                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null && !state.saving

                    binding.layoutDaftar.isVisible =
                        state.detail == null

                    binding.layoutDetail.isVisible =
                        state.detail != null

                    binding.btnSubmit.isEnabled =
                        !state.loading &&
                                !state.saving &&
                                state.selectedIds.isNotEmpty() &&
                                state.detail?.survey?.canFill == true

                    if (renderedSurveys != state.surveys) {
                        renderedSurveys = state.surveys
                        tampilkanDaftar(state.surveys)
                    }

                    val detailId = state.detail?.survey?.id

                    if (renderedDetailId != detailId) {
                        renderedDetailId = detailId

                        state.detail?.let {
                            tampilkanDetail(it)
                        }
                    }

                    binding.tvJumlahPilihan.text =
                        "${state.selectedIds.size} mata pelajaran dipilih"

                    binding.tvEmpty.isVisible =
                        !state.loading &&
                                state.error == null &&
                                state.detail == null &&
                                state.surveys.isEmpty()

                    state.message?.let { message ->
                        viewModel.clearMessage()

                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Berhasil")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }

        viewModel.loadSurveys()
    }

    private fun tampilkanDaftar(
        surveys: List<SurveiSiswaDto>
    ) {
        binding.containerSurvei.removeAllViews()

        binding.tvTotal.text = surveys.size.toString()

        surveys.forEach { item ->

            val card = buatCard()

            val content = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }

            content.addView(buatText(item.title, 16f, true))

            content.addView(
                buatText(
                    "${item.academicYearName} • Kelas ${item.gradeLevel}",
                    12f
                )
            )

            content.addView(
                buatText(
                    "${item.openDate} s.d. ${item.closeDate}",
                    12f
                )
            )

            val status = when {
                item.hasSubmitted -> "Sudah Diisi"
                item.canFill -> "Belum Diisi"
                else -> "Tidak Aktif"
            }

            content.addView(
                buatText("Status: $status", 12f)
            )

            val button = MaterialButton(requireContext()).apply {
                text = when {
                    !item.canFill -> "Lihat Jawaban"
                    item.hasSubmitted -> "Ubah Jawaban"
                    else -> "Isi Survei"
                }

                isAllCaps = false

                setOnClickListener {
                    viewModel.pilihSurvei(item.id)
                }
            }

            content.addView(button)
            card.addView(content)

            binding.containerSurvei.addView(card)
        }
    }

    private fun tampilkanDetail(detail: DetailSurveiSiswaDto) {
        val survey = detail.survey

        binding.tvJudulSurvei.text = survey.title

        binding.tvPeriode.text =
            "Periode: ${survey.openDate} - ${survey.closeDate}"

        binding.tvStatus.text = when {
            survey.hasSubmitted -> "Sudah Diisi"
            survey.canFill -> "Belum Diisi"
            else -> "Survei Ditutup"
        }

        binding.containerMapel.removeAllViews()

        detail.subjects.forEach { subject ->

            val checkbox = MaterialCheckBox(
                requireContext()
            ).apply {

                text = "${subject.name} (${subject.code})"

                isChecked = viewModel.state.value
                    .selectedIds.contains(subject.id)

                isEnabled = survey.canFill

                setOnCheckedChangeListener { _, checked ->
                    viewModel.pilihMapel(
                        subject.id,
                        checked
                    )
                }
            }

            binding.containerMapel.addView(checkbox)
        }

        binding.btnSubmit.isVisible = survey.canFill

        binding.tvInfo.text =
            if (survey.canFill) {
                "Pilih mata pelajaran yang kamu minati. " +
                        "Jawaban dapat diperbarui selama survei masih terbuka."
            } else {
                "Periode pengisian survei sudah berakhir. " +
                        "Jawaban hanya dapat dilihat."
            }
    }

    private fun konfirmasiKirim() {
        val state = viewModel.state.value
        val detail = state.detail ?: return

        if (state.selectedIds.isEmpty()) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Konfirmasi Jawaban")
            .setMessage(
                "Survei: ${detail.survey.title}\n" +
                        "Jumlah pilihan: ${state.selectedIds.size}\n\n" +
                        "Simpan jawaban survei minat ini?"
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                viewModel.simpan()
            }
            .show()
    }

    private fun buatCard(): MaterialCardView {
        return MaterialCardView(requireContext()).apply {
            radius = dp(12).toFloat()

            cardElevation = dp(1).toFloat()

            setCardBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.white
                )
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
    }

    private fun buatText(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView {
        return TextView(requireContext()).apply {
            text = value
            textSize = size

            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.agnesian_text
                )
            )

            if (bold) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            setPadding(0, 0, 0, dp(8))
        }
    }

    private fun dp(value: Int): Int {
        return (
                value * resources.displayMetrics.density
                ).toInt()
    }

    override fun onDestroyView() {
        renderedSurveys = null
        renderedDetailId = null
        _binding = null
        super.onDestroyView()
    }
}
