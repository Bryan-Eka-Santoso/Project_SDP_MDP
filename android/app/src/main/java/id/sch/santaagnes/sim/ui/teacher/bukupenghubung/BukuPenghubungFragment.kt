
package id.sch.santaagnes.sim.ui.teacher.bukupenghubung

import android.app.DatePickerDialog
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
import id.sch.santaagnes.sim.databinding.FragmentBukuPenghubungBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class BukuPenghubungFragment : Fragment() {

    private var _binding: FragmentBukuPenghubungBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BukuPenghubungViewModel by viewModels()

    private var daftarKelas = listOf<KelasPerwalianDto>()
    private var daftarSiswa = listOf<SiswaBukuPenghubungDto>()

    private var kelasDipilih: KelasPerwalianDto? = null
    private var siswaDipilih: SiswaBukuPenghubungDto? = null

    private var tanggal = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBukuPenghubungBinding.inflate(
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

        // Tanggal awal adalah hari ini.
        tanggal = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Calendar.getInstance().time)

        binding.etTanggal.setText(tanggal)

        binding.etTanggal.setOnClickListener {
            pilihTanggal()
        }

        binding.btnSimpan.setOnClickListener {
            simpanCatatan()
        }

        binding.btnRetry.setOnClickListener {
            val id = kelasDipilih?.id

            if (id != null) {
                viewModel.getSiswa(id)
            } else {
                viewModel.getKelas()
            }
        }

        binding.etSiswa.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                viewModel.uiState.collect { state ->

                    binding.progress.isVisible =
                        state.loading || state.saving

                    binding.btnSimpan.isEnabled =
                        !state.loading &&
                                !state.saving &&
                                daftarKelas.isNotEmpty()

                    binding.tvError.isVisible =
                        state.error != null

                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null &&
                                !state.saving

                    if (daftarKelas != state.kelas) {
                        daftarKelas = state.kelas
                        setupKelas()
                    }

                    if (daftarSiswa != state.siswa) {
                        daftarSiswa = state.siswa
                        setupSiswa()
                    }

                    binding.tvEmpty.isVisible =
                        !state.loading &&
                                state.error == null &&
                                daftarKelas.isEmpty()

                    state.berhasil?.let { hasil ->
                        viewModel.clearBerhasil()
                        tampilBerhasil(hasil)
                    }
                }
            }
        }

        viewModel.getKelas()
    }

    private fun setupKelas() {

        val labels = daftarKelas.map {
            it.name
        }

        binding.etKelas.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                labels
            )
        )

        binding.etKelas.setOnItemClickListener { _, _, position, _ ->

            kelasDipilih = daftarKelas[position]
            siswaDipilih = null

            binding.etSiswa.setText("", false)
            binding.etSiswa.isEnabled = false

            binding.layoutKelas.error = null

            viewModel.getSiswa(kelasDipilih!!.id)
        }

        if (daftarKelas.isEmpty()) {
            kelasDipilih = null
            siswaDipilih = null

            binding.etKelas.setText("", false)
            binding.etSiswa.setText("", false)
            binding.etSiswa.isEnabled = false
        }
    }

    private fun setupSiswa() {

        val labels = daftarSiswa.map {
            "${it.name} (${it.nis})"
        }

        binding.etSiswa.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                labels
            )
        )

        binding.etSiswa.isEnabled = daftarSiswa.isNotEmpty()

        binding.etSiswa.setOnItemClickListener { _, _, position, _ ->

            siswaDipilih = daftarSiswa[position]

            binding.layoutSiswa.error = null
        }
    }

    private fun pilihTanggal() {

        val cal = Calendar.getInstance()

        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->

                tanggal = String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    year,
                    month + 1,
                    day
                )

                binding.etTanggal.setText(tanggal)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )

        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun simpanCatatan() {

        val kelas = kelasDipilih
        val siswa = siswaDipilih

        if (kelas == null) {
            binding.layoutKelas.error =
                "Pilih kelas terlebih dahulu"
            return
        }
        binding.layoutKelas.error = null

        if (siswa == null) {
            binding.layoutSiswa.error =
                "Pilih siswa terlebih dahulu"
            return
        }
        binding.layoutSiswa.error = null

        val isi = binding.etCatatan.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (isi.isBlank()) {
            binding.layoutCatatan.error =
                "Catatan wajib diisi"
            return
        }

        if (isi.length > 500) {
            binding.layoutCatatan.error =
                "Catatan maksimal 500 karakter"
            return
        }
        binding.layoutCatatan.error = null

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Konfirmasi Catatan")
            .setMessage(
                "Siswa: ${siswa.name}\n" +
                        "Kelas: ${kelas.name}\n" +
                        "Tanggal: $tanggal\n\n" +
                        "Simpan catatan buku penghubung ini?"
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->

                viewModel.simpan(
                    CatatanBukuPenghubungRequest(
                        classId = kelas.id,
                        studentId = siswa.id,
                        date = tanggal,
                        content = isi
                    )
                )
            }
            .show()
    }

    private fun tampilBerhasil(
        hasil: CatatanBukuPenghubungDto
    ) {

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Catatan Berhasil Disimpan")
            .setMessage(
                "Catatan untuk ${hasil.studentName} " +
                        "berhasil disimpan ke Buku Penghubung."
            )
            .setPositiveButton("OK") { _, _ ->
                binding.etCatatan.setText("")
            }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
