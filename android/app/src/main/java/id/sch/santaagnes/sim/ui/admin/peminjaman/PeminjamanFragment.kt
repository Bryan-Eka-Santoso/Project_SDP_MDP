package id.sch.santaagnes.sim.ui.admin.peminjaman

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import id.sch.santaagnes.sim.R
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.data.remote.PeminjamanDto
import id.sch.santaagnes.sim.databinding.FragmentPeminjamanBinding
import kotlinx.coroutines.launch
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat

@AndroidEntryPoint
class PeminjamanFragment : Fragment() {

    private var _binding: FragmentPeminjamanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PeminjamanViewModel by viewModels()

    private val adapter = PeminjamanAdapter(
        onApprove = { konfirmasi(it, true) },
        onReject = { konfirmasi(it, false) },
        onReturn = { konfirmasiKembali(it) }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPeminjamanBinding.inflate(
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

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.rvPeminjaman.layoutManager =
            LinearLayoutManager(requireContext())

        binding.rvPeminjaman.adapter = adapter

        binding.btnMenunggu.setOnClickListener {
            updateButtonActive("PENDING")
            viewModel.getPeminjaman("PENDING")
        }

        binding.btnDisetujui.setOnClickListener {
            updateButtonActive("APPROVED")
            viewModel.getPeminjaman("APPROVED")
        }

        binding.btnDitolak.setOnClickListener {
            updateButtonActive("REJECTED")
            viewModel.getPeminjaman("REJECTED")
        }

        binding.btnSemua.setOnClickListener {
            updateButtonActive(null)
            viewModel.getPeminjaman(null)
        }

        binding.btnRetry.setOnClickListener {
            viewModel.getPeminjaman()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->
                    updateButtonActive(state.filter)

                    binding.progress.isVisible =
                        state.loading || state.processing

                    binding.tvEmpty.isVisible =
                        state.daftar.isEmpty() &&
                                !state.loading &&
                                state.error == null

                    binding.tvError.isVisible =
                        state.error != null

                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null

                    binding.btnSemua.isEnabled = !state.processing
                    binding.btnMenunggu.isEnabled = !state.processing
                    binding.btnDisetujui.isEnabled = !state.processing
                    binding.btnDitolak.isEnabled = !state.processing

                    binding.tvTotal.isVisible = !state.loading && state.error == null
                    binding.tvTotal.text = getString(R.string.master_total_bookings, state.daftar.size)

                    adapter.setData(state.daftar)

                    state.pesan?.let { pesan ->
                        Toast.makeText(
                            requireContext(),
                            pesan,
                            Toast.LENGTH_SHORT
                        ).show()

                        viewModel.clearMessage()
                    }

                    state.error?.let { pesan ->
                        Toast.makeText(
                            requireContext(),
                            pesan,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        viewModel.getPeminjaman()
    }

    private fun konfirmasi(
        item: PeminjamanDto,
        setujui: Boolean
    ) {
        if (setujui) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Setujui Peminjaman")
                .setMessage(
                    "Setujui peminjaman ${item.facilityName} oleh ${item.requesterName}?\n" +
                        "Tanggal: ${item.bookingDate} (${item.startTime} – ${item.endTime})"
                )
                .setNegativeButton("Batal", null)
                .setPositiveButton("Setujui") { _, _ ->
                    viewModel.putuskan(item.id, true)
                }
                .show()
            return
        }

        val density = resources.displayMetrics.density
        val pad = (density * 20).toInt()
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, (density * 8).toInt(), pad, 0)
        }
        val subtitle = android.widget.TextView(requireContext()).apply {
            text = "Tolak peminjaman ${item.facilityName} oleh ${item.requesterName}.\nTuliskan alasan penolakan agar pemohon mengetahuinya."
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.agnesian_text))
        }
        val inputLayout = com.google.android.material.textfield.TextInputLayout(
            requireContext(),
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = "Alasan penolakan (wajib diisi)"
            isCounterEnabled = true
            counterMaxLength = 255
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (density * 12).toInt()
            }
        }
        val editText = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            minLines = 2
            maxLines = 4
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(255))
        }
        inputLayout.addView(editText)
        container.addView(subtitle)
        container.addView(inputLayout)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Tolak Peminjaman")
            .setView(container)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Tolak", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reason = editText.text?.toString()?.trim().orEmpty()
                if (reason.isBlank()) {
                    inputLayout.error = "Alasan penolakan wajib diisi."
                    return@setOnClickListener
                }
                inputLayout.error = null
                dialog.dismiss()
                viewModel.putuskan(item.id, false, reason)
            }
        }
        dialog.show()
    }

    private fun konfirmasiKembali(item: PeminjamanDto) {

        val pesan = """
        Pastikan fasilitas sudah benar-benar dikembalikan.

        Fasilitas: ${item.facilityName}
        Pemohon: ${item.requesterName}
        Tanggal Pinjam: ${item.bookingDate}
        Waktu: ${item.startTime} - ${item.endTime}

        Konfirmasi pengembalian fasilitas ini?
    """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Konfirmasi Pengembalian")
            .setMessage(pesan)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Konfirmasi") { _, _ ->

                viewModel.konfirmasiPengembalian(item.id)
            }
            .show()
    }

    override fun onDestroyView() {
        binding.rvPeminjaman.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun updateButtonActive(status: String?) {

        val biru = ContextCompat.getColor(
            requireContext(),
            R.color.agnesian_blue
        )

        val putih = ContextCompat.getColor(
            requireContext(),
            R.color.white
        )

        val buttons = listOf(
            binding.btnMenunggu to "PENDING",
            binding.btnDisetujui to "APPROVED",
            binding.btnDitolak to "REJECTED",
            binding.btnSemua to null
        )

        buttons.forEach { (button, jenis) ->

            val isActive = jenis == status

            button.backgroundTintList =
                ColorStateList.valueOf(
                    if (isActive) biru else putih
                )

            button.setTextColor(
                if (isActive) putih else biru
            )

            button.strokeColor =
                ColorStateList.valueOf(biru)

            button.strokeWidth =
                if (isActive) 0
                else (resources.displayMetrics.density)
                    .toInt()
                    .coerceAtLeast(1)
        }
    }

}