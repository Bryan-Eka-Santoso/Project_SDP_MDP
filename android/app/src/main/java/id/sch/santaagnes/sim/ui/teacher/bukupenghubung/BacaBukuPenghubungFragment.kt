package id.sch.santaagnes.sim.ui.bukupenghubung

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
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.data.remote.SiswaBacaBukuDto
import id.sch.santaagnes.sim.databinding.FragmentBacaBukuPenghubungBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BacaBukuPenghubungFragment : Fragment() {

    private var _binding: FragmentBacaBukuPenghubungBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BacaBukuPenghubungViewModel by viewModels()
    private val adapter = BacaBukuPenghubungAdapter()

    private var daftarSiswa = listOf<SiswaBacaBukuDto>()
    private var selectedId: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBacaBukuPenghubungBinding.inflate(
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

        binding.rvCatatan.layoutManager =
            LinearLayoutManager(requireContext())
        binding.rvCatatan.adapter = adapter

        binding.btnRetry.setOnClickListener {
            viewModel.retry()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->

                    binding.progress.isVisible = state.loading
                    binding.tvError.isVisible = state.error != null
                    binding.tvError.text = state.error
                    binding.btnRetry.isVisible = state.error != null

                    if (daftarSiswa != state.siswa) {
                        daftarSiswa = state.siswa
                        setupSiswa()
                    }

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

                    binding.cardSummary.isVisible =
                        state.riwayat != null

                    binding.tvTotal.text =
                        (state.riwayat?.total ?: 0).toString()

                    adapter.setData(
                        state.riwayat?.notes.orEmpty()
                    )

                    binding.tvEmpty.isVisible =
                        !state.loading &&
                                state.error == null &&
                                (
                                        daftarSiswa.isEmpty() ||
                                                state.riwayat?.notes?.isEmpty() == true
                                        )

                    binding.tvEmpty.text =
                        if (daftarSiswa.isEmpty()) {
                            "Tidak ada siswa yang dapat ditampilkan"
                        } else {
                            "Belum ada catatan Buku Penghubung"
                        }
                }
            }
        }

        viewModel.loadSiswa()
    }

    private fun setupSiswa() {
        val labels = daftarSiswa.map {
            "${it.name} (${it.nis}) - ${it.className}"
        }

        binding.etSiswa.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                labels
            )
        )

        binding.etSiswa.setOnItemClickListener { _, _, position, _ ->
            viewModel.pilihSiswa(daftarSiswa[position].id)
        }

        binding.layoutSiswa.isVisible = daftarSiswa.isNotEmpty()
    }

    override fun onDestroyView() {
        binding.rvCatatan.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
