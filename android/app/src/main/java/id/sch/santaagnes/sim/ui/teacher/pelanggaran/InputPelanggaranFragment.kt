package id.sch.santaagnes.sim.ui.teacher.pelanggaran

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
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
import id.sch.santaagnes.sim.databinding.FragmentInputPelanggaranBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class InputPelanggaranFragment : Fragment() {

    private var _binding: FragmentInputPelanggaranBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InputPelanggaranViewModel by viewModels()

    private var daftarSiswa = listOf<SiswaPelanggaranDto>()
    private var daftarJenis = listOf<JenisPelanggaranGuruDto>()

    private var siswaDipilih: SiswaPelanggaranDto? = null
    private var jenisDipilih: JenisPelanggaranGuruDto? = null

    private var tanggalDipilih = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInputPelanggaranBinding.inflate(
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

        // Tanggal default adalah hari ini.
        val calendar = Calendar.getInstance()
        tanggalDipilih = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(calendar.time)

        binding.etTanggal.setText(tanggalDipilih)

        binding.etTanggal.setOnClickListener {
            pilihTanggal()
        }

        binding.btnSimpan.setOnClickListener {
            simpanPelanggaran()
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadData()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->

                    binding.progress.isVisible =
                        state.loading || state.saving

                    binding.btnSimpan.isEnabled =
                        !state.loading && !state.saving

                    binding.tvError.isVisible =
                        state.error != null

                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null && !state.saving

                    if (daftarSiswa != state.siswa) {
                        daftarSiswa = state.siswa
                        setupDropdownSiswa()
                    }

                    if (daftarJenis != state.jenis) {
                        daftarJenis = state.jenis
                        setupDropdownJenis()
                    }
                }
            }
        }

        viewModel.loadData()
    }

    private fun setupDropdownSiswa() {

        val labels = daftarSiswa.map {
            "${it.name} (${it.nis}) - ${it.className}"
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            labels
        )

        binding.etSiswa.setAdapter(adapter)

        binding.etSiswa.setOnItemClickListener { _, _, position, _ ->
            siswaDipilih = daftarSiswa[position]
        }
    }

    private fun setupDropdownJenis() {

        val labels = daftarJenis.map {
            it.name
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            labels
        )

        binding.etJenis.setAdapter(adapter)

        binding.etJenis.setOnItemClickListener { _, _, position, _ ->

            jenisDipilih = daftarJenis[position]

            binding.tvPoin.text =
                "${jenisDipilih?.points ?: 0} Poin"
        }
    }

    private fun pilihTanggal() {
        val calendar = Calendar.getInstance()

        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                tanggalDipilih = String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    year,
                    month + 1,
                    day
                )

                binding.etTanggal.setText(tanggalDipilih)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun simpanPelanggaran() {

        val siswa = siswaDipilih
        val jenis = jenisDipilih

        if (siswa == null) {
            binding.layoutSiswa.error = "Pilih siswa terlebih dahulu"
            return
        }
        binding.layoutSiswa.error = null

        if (jenis == null) {
            binding.layoutJenis.error = "Pilih jenis pelanggaran"
            return
        }
        binding.layoutJenis.error = null

        if (tanggalDipilih.isBlank()) {
            Toast.makeText(
                requireContext(),
                "Pilih tanggal pelanggaran",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val keterangan = binding.etKeterangan.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (keterangan.length > 255) {
            binding.layoutKeterangan.error =
                "Keterangan maksimal 255 karakter"
            return
        }
        binding.layoutKeterangan.error = null

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Konfirmasi Pelanggaran")
            .setMessage(
                "Catat pelanggaran ${jenis.name} untuk " +
                        "${siswa.name} sebesar ${jenis.points} poin?"
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->

                val request = InputPelanggaranRequest(
                    studentId = siswa.id,
                    violationTypeId = jenis.id,
                    date = tanggalDipilih,
                    description = keterangan.ifBlank { null }
                )

                viewModel.simpan(request) { hasil ->
                    tampilHasil(hasil)
                }
            }
            .show()
    }

    private fun tampilHasil(hasil: HasilPelanggaranDto) {

        val pesan = if (hasil.guardianCount > 0) {
            "Notifikasi diproses untuk " +
                    "${hasil.guardianCount} akun wali murid."
        } else {
            "Belum ada akun wali murid yang terhubung."
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pelanggaran Berhasil Dicatat")
            .setMessage(
                "${hasil.studentName}\n" +
                        "${hasil.violationName}\n" +
                        "${hasil.points} Poin\n\n" +
                        pesan
            )
            .setPositiveButton("OK") { _, _ ->
                // Reset pilihan agar tidak tersimpan dua kali.
                binding.etSiswa.setText("", false)
                binding.etJenis.setText("", false)
                binding.etKeterangan.setText("")
                binding.tvPoin.text = "—"

                siswaDipilih = null
                jenisDipilih = null
            }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}