package id.sch.santaagnes.sim.ui.peminjaman

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.data.remote.*
import id.sch.santaagnes.sim.databinding.FragmentAjukanPeminjamanBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class AjukanPeminjamanFragment : Fragment() {

    private var _binding: FragmentAjukanPeminjamanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AjukanPeminjamanViewModel by viewModels()

    private var daftarFasilitas = listOf<FasilitasPeminjamanDto>()
    private var fasilitasTerpilih: FasilitasPeminjamanDto? = null

    private var jenisTerpilih = "ROOM"
    private var tanggal = ""
    private var jamMulai = ""
    private var jamSelesai = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAjukanPeminjamanBinding.inflate(
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

        setupJenis()
        setupTanggalDanWaktu()

        binding.btnRetry.setOnClickListener {
            viewModel.loadFasilitas()
        }

        binding.btnAjukan.setOnClickListener {
            konfirmasiPengajuan()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->

                    binding.progress.isVisible =
                        state.loading || state.saving

                    binding.btnAjukan.isEnabled =
                        !state.loading && !state.saving

                    binding.tvError.isVisible =
                        state.error != null

                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null && !state.saving

                    if (daftarFasilitas != state.fasilitas) {
                        daftarFasilitas = state.fasilitas
                        setupFasilitas()
                    }

                    state.berhasil?.let { hasil ->
                        viewModel.clearBerhasil()
                        tampilBerhasil(hasil)
                    }
                }
            }
        }

        viewModel.loadFasilitas()
    }

    private fun setupJenis() {
        val labels = listOf("Ruangan", "Barang")

        binding.etJenis.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                labels
            )
        )

        binding.etJenis.setText("Ruangan", false)

        binding.etJenis.setOnItemClickListener { _, _, position, _ ->
            jenisTerpilih = if (position == 0) "ROOM" else "ITEM"
            fasilitasTerpilih = null
            binding.etFasilitas.setText("", false)
            setupFasilitas()
        }
    }

    private fun setupFasilitas() {
        val tersedia = daftarFasilitas.filter {
            it.type == jenisTerpilih
        }

        binding.etFasilitas.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                tersedia.map { it.name }
            )
        )

        binding.etFasilitas.setOnItemClickListener { _, _, posisi, _ ->
            fasilitasTerpilih = tersedia[posisi]
        }

        binding.layoutFasilitas.helperText =
            if (tersedia.isEmpty()) {
                "Belum ada fasilitas pada kategori ini"
            } else {
                null
            }
    }

    private fun setupTanggalDanWaktu() {
        binding.etTanggal.setOnClickListener {
            val cal = Calendar.getInstance()

            val dialog = DatePickerDialog(
                requireContext(),
                { _, tahun, bulan, hari ->
                    tanggal = String.format(
                        Locale.US,
                        "%04d-%02d-%02d",
                        tahun, bulan + 1, hari
                    )

                    binding.etTanggal.setText(tanggal)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )

            dialog.datePicker.minDate = System.currentTimeMillis()
            dialog.show()
        }

        binding.etJamMulai.setOnClickListener {
            pilihJam(true)
        }

        binding.etJamSelesai.setOnClickListener {
            pilihJam(false)
        }
    }

    private fun pilihJam(isMulai: Boolean) {
        val cal = Calendar.getInstance()

        TimePickerDialog(
            requireContext(),
            { _, jam, menit ->

                val value = String.format(
                    Locale.US,
                    "%02d:%02d",
                    jam, menit
                )

                if (isMulai) {
                    jamMulai = value
                    binding.etJamMulai.setText(value)
                } else {
                    jamSelesai = value
                    binding.etJamSelesai.setText(value)
                }
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun konfirmasiPengajuan() {
        val fasilitas = fasilitasTerpilih
        val keperluan = binding.etKeperluan.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (fasilitas == null) {
            binding.layoutFasilitas.error =
                "Pilih fasilitas terlebih dahulu"
            return
        }
        binding.layoutFasilitas.error = null

        if (tanggal.isBlank()) {
            binding.etTanggal.error = "Pilih tanggal"
            return
        }

        if (jamMulai.isBlank() || jamSelesai.isBlank()) {
            binding.etJamMulai.error = "Lengkapi waktu peminjaman"
            return
        }

        if (jamMulai >= jamSelesai) {
            binding.etJamSelesai.error =
                "Jam selesai harus setelah jam mulai"
            return
        }

        if (keperluan.length !in 5..255) {
            binding.layoutKeperluan.error =
                "Keperluan harus 5 sampai 255 karakter"
            return
        }

        binding.layoutKeperluan.error = null

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Konfirmasi Peminjaman")
            .setMessage(
                "Fasilitas: ${fasilitas.name}\n" +
                        "Tanggal: $tanggal\n" +
                        "Waktu: $jamMulai - $jamSelesai\n\n" +
                        "Ajukan peminjaman ini?"
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Ajukan") { _, _ ->
                viewModel.ajukan(
                    AjukanPeminjamanRequest(
                        facilityId = fasilitas.id,
                        bookingDate = tanggal,
                        startTime = jamMulai,
                        endTime = jamSelesai,
                        purpose = keperluan
                    )
                )
            }
            .show()
    }

    private fun tampilBerhasil(hasil: HasilPengajuanDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pengajuan Berhasil")
            .setMessage(
                "Peminjaman ${hasil.facilityName} berhasil diajukan.\n\n" +
                        "Status: Menunggu Persetujuan Admin."
            )
            .setPositiveButton("OK") { _, _ ->
                findNavController().navigateUp()
            }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
