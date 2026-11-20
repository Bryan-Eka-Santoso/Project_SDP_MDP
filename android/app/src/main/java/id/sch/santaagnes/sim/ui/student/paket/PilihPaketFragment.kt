package id.sch.santaagnes.sim.ui.student.paket

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.DaftarPaketSiswaDto
import id.sch.santaagnes.sim.data.remote.PaketPilihanSiswaDto
import id.sch.santaagnes.sim.databinding.FragmentPilihPaketBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PilihPaketFragment : Fragment() {

    private var _binding: FragmentPilihPaketBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PilihPaketViewModel by viewModels()

    private var renderedData: DaftarPaketSiswaDto? = null
    private var renderedBusy: Boolean? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPilihPaketBinding.inflate(
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

        binding.btnRetry.setOnClickListener {
            viewModel.loadPaket()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.state.collect { state ->
                    val busy = state.loading || state.saving

                    binding.progress.isVisible = busy

                    binding.tvError.isVisible =
                        state.error != null
                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null && !busy

                    binding.tvEmpty.isVisible =
                        !busy &&
                                state.error == null &&
                                state.data?.packages?.isEmpty() == true

                    if (
                        renderedData != state.data ||
                        renderedBusy != busy
                    ) {
                        renderedData = state.data
                        renderedBusy = busy

                        binding.tvTahun.text =
                            "Tahun Ajaran: ${
                                state.data?.academicYearName ?: "-"
                            }"

                        val selected = state.data
                            ?.packages
                            ?.firstOrNull { it.isSelected }

                        binding.tvPilihan.text =
                            selected?.let {
                                "Pilihan Anda: ${it.name}"
                            } ?: "Belum memilih paket"

                        tampilkanPaket(
                            state.data?.packages.orEmpty(),
                            busy
                        )
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

        viewModel.loadPaket()
    }

    private fun tampilkanPaket(
        packages: List<PaketPilihanSiswaDto>,
        busy: Boolean
    ) {
        binding.containerPaket.removeAllViews()

        packages.forEach { paket ->
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
                    dp(16), dp(16), dp(16), dp(16)
                )
            }

            content.addView(
                makeText(paket.name, 17f, true)
            )

            content.addView(
                makeText(
                    "Terisi: ${paket.selectionCount}/${paket.capacity}"
                )
            )

            content.addView(
                makeText(
                    "Sisa kapasitas: ${paket.remainingCapacity}"
                )
            )

            content.addView(
                makeText("Daftar Mata Pelajaran:", 14f, true)
            )

            paket.subjects.forEachIndexed { index, mapel ->
                content.addView(
                    makeText(
                        "${index + 1}. ${mapel.name} (${mapel.code})"
                    )
                )
            }

            val button = MaterialButton(requireContext()).apply {
                text = when {
                    paket.isSelected -> "Paket Dipilih"
                    paket.remainingCapacity <= 0 -> "Paket Penuh"
                    else -> "Pilih Paket"
                }

                isAllCaps = false

                isEnabled =
                    !busy &&
                            !paket.isSelected &&
                            paket.remainingCapacity > 0

                setOnClickListener {
                    konfirmasiPaket(paket)
                }
            }

            content.addView(button)
            card.addView(content)
            binding.containerPaket.addView(card)
        }
    }

    private fun konfirmasiPaket(
        paket: PaketPilihanSiswaDto
    ) {
        val lama = viewModel.state.value.data
            ?.packages?.firstOrNull { it.isSelected }

        val pesan = if (lama != null) {
            "Ganti paket dari ${lama.name} " +
                    "menjadi ${paket.name}?"
        } else {
            "Pilih ${paket.name} sebagai paket mata pelajaran?"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Konfirmasi Pilihan")
            .setMessage(pesan)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                viewModel.pilihPaket(paket.id)
            }
            .show()
    }

    private fun makeText(
        value: String,
        size: Float = 13f,
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
                setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
                )
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
        renderedData = null
        renderedBusy = null
        _binding = null
        super.onDestroyView()
    }
}
