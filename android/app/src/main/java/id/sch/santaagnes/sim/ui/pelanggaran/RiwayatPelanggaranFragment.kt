
package id.sch.santaagnes.sim.ui.pelanggaran

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.data.remote.SiswaRiwayatDto
import id.sch.santaagnes.sim.data.remote.KejadianPelanggaranDto
import id.sch.santaagnes.sim.databinding.FragmentRiwayatPelanggaranBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RiwayatPelanggaranFragment : Fragment() {

    private var _binding: FragmentRiwayatPelanggaranBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RiwayatPelanggaranViewModel by viewModels()

    private val adapter = RiwayatPelanggaranAdapter()

    private var daftarSiswa = listOf<SiswaRiwayatDto>()
    private var daftarKejadian = listOf<KejadianPelanggaranDto>()

    private var selectedId: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentRiwayatPelanggaranBinding.inflate(
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

        // Tombol kembali
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Setup RecyclerView
        binding.rvPelanggaran.layoutManager =
            LinearLayoutManager(requireContext())

        binding.rvPelanggaran.adapter = adapter

        // Tombol retry
        binding.btnRetry.setOnClickListener {
            viewModel.retry()
        }

        // Search kejadian
        binding.etSearch.doAfterTextChanged {
            filterKejadian()
        }

        // Mengamati data ViewModel
        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                viewModel.uiState.collect { state ->

                    // Loading
                    binding.progress.isVisible = state.loading

                    // Error
                    binding.tvError.isVisible = state.error != null
                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible = state.error != null

                    // Daftar siswa
                    if (daftarSiswa != state.siswa) {

                        daftarSiswa = state.siswa

                        setupSiswa()
                    }

                    // Siswa yang dipilih
                    if (selectedId != state.selectedStudentId) {

                        selectedId = state.selectedStudentId

                        val siswa = daftarSiswa.firstOrNull {
                            it.id == selectedId
                        }

                        if (siswa != null) {

                            binding.etSiswa.setText(
                                "${siswa.name} (${siswa.nis})",
                                false
                            )
                        } else {
                            binding.etSiswa.setText("", false)
                        }
                    }

                    // Data riwayat
                    val data = state.riwayat

                    // Summary card
                    binding.cardSummary.isVisible =
                        data != null && !state.loading

                    binding.tvTotal.text =
                        (data?.totalPoints ?: 0).toString()

                    binding.tvCount.text =
                        "Dari ${data?.totalViolations ?: 0} kejadian pelanggaran"

                    // Daftar kejadian
                    daftarKejadian = data?.violations.orEmpty()

                    // Memperbarui hasil pencarian dan empty state
                    filterKejadian()
                }
            }
        }

        // Load data awal
        viewModel.loadData()
    }

    private fun setupSiswa() {

        val labels = daftarSiswa.map {
            "${it.name} (${it.nis})"
        }

        val siswaAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            labels
        )

        binding.etSiswa.setAdapter(siswaAdapter)

        binding.etSiswa.setOnItemClickListener { _, _, position, _ ->

            val siswa = daftarSiswa[position]

            viewModel.pilihSiswa(siswa.id)
        }

        binding.layoutSiswa.isVisible = daftarSiswa.isNotEmpty()
    }

    private fun filterKejadian() {

        val keyword = binding.etSearch.text
            ?.toString()
            ?.trim()
            .orEmpty()

        val hasil = daftarKejadian.filter { item ->

            item.name.contains(
                keyword,
                ignoreCase = true
            ) ||
                    item.description.orEmpty().contains(
                        keyword,
                        ignoreCase = true
                    )
        }

        adapter.setData(hasil)

        val state = viewModel.uiState.value

        // Jangan menampilkan empty state ketika loading/error
        if (state.loading || state.error != null) {
            binding.tvEmpty.isVisible = false
            return
        }

        // Tidak ada siswa yang dapat diakses
        if (daftarSiswa.isEmpty()) {

            binding.tvEmpty.isVisible = true
            binding.tvEmpty.text =
                "Tidak ada siswa yang dapat ditampilkan"

            return
        }

        // Data kejadian kosong
        if (daftarKejadian.isEmpty()) {

            binding.tvEmpty.isVisible = true
            binding.tvEmpty.text =
                "Belum ada riwayat pelanggaran"

            return
        }

        // Pencarian tidak ditemukan
        if (hasil.isEmpty()) {

            binding.tvEmpty.isVisible = true
            binding.tvEmpty.text =
                "Tidak ada kejadian yang cocok dengan pencarian"

            return
        }

        // Ada data yang ditampilkan
        binding.tvEmpty.isVisible = false
    }

    override fun onDestroyView() {

        binding.rvPelanggaran.adapter = null

        _binding = null

        super.onDestroyView()
    }
}
