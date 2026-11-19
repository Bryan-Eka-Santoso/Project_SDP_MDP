package id.sch.santaagnes.sim.ui.peminjaman

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentRiwayatPeminjamanBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RiwayatPeminjamanFragment : Fragment() {

    private var _binding: FragmentRiwayatPeminjamanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RiwayatPeminjamanViewModel by viewModels()

    private val adapter = RiwayatPeminjamanAdapter()

    // null berarti tampilkan semua status
    private var filterStatus: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentRiwayatPeminjamanBinding.inflate(
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

        binding.rvPeminjaman.layoutManager =
            LinearLayoutManager(requireContext())

        binding.rvPeminjaman.adapter = adapter

        // Filter status
        binding.btnSemua.setOnClickListener {
            filterStatus = null
            tampilkanData()
        }

        binding.btnMenunggu.setOnClickListener {
            filterStatus = "PENDING"
            tampilkanData()
        }

        binding.btnDisetujui.setOnClickListener {
            filterStatus = "APPROVED"
            tampilkanData()
        }

        binding.btnDitolak.setOnClickListener {
            filterStatus = "REJECTED"
            tampilkanData()
        }

        binding.btnMuatLagi.setOnClickListener {
            viewModel.muatLagi()
        }

        binding.btnRetry.setOnClickListener {
            viewModel.retry()
        }

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                viewModel.uiState.collect { state ->

                    binding.progress.isVisible = state.loading

                    binding.tvError.isVisible =
                        state.error != null

                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null

                    binding.tvTotal.text =
                        state.total.toString()

                    binding.tvJumlahData.text =
                        "Menampilkan ${state.daftar.size} dari ${state.total} pengajuan"

                    binding.btnMuatLagi.isVisible =
                        !state.loading &&
                                state.error == null &&
                                state.page < state.totalPages

                    tampilkanData()
                }
            }
        }

        updateButtonActive()

        // Memuat data awal
        viewModel.refresh()
    }

    private fun tampilkanData() {

        val state = viewModel.uiState.value

        val hasil = state.daftar.filter { item ->
            filterStatus == null ||
                    item.status == filterStatus
        }

        adapter.setData(hasil)

        updateButtonActive()

        binding.tvEmpty.isVisible =
            hasil.isEmpty() &&
                    !state.loading &&
                    state.error == null &&
                    state.page > 0

        binding.tvEmpty.text = when {
            state.daftar.isEmpty() ->
                "Belum ada riwayat peminjaman"

            filterStatus != null ->
                "Belum ada pengajuan dengan status ini pada data yang dimuat"

            else ->
                "Belum ada riwayat peminjaman"
        }
    }

    private fun updateButtonActive() {

        val biru = ContextCompat.getColor(
            requireContext(),
            R.color.agnesian_blue
        )

        val putih = ContextCompat.getColor(
            requireContext(),
            R.color.white
        )

        val buttons = listOf(
            binding.btnSemua to null,
            binding.btnMenunggu to "PENDING",
            binding.btnDisetujui to "APPROVED",
            binding.btnDitolak to "REJECTED"
        )

        buttons.forEach { (button, status) ->

            val active = filterStatus == status

            button.backgroundTintList =
                ColorStateList.valueOf(
                    if (active) biru else putih
                )

            button.setTextColor(
                if (active) putih else biru
            )

            button.strokeColor =
                ColorStateList.valueOf(biru)

            button.strokeWidth =
                if (active) 0 else
                    (resources.displayMetrics.density)
                        .toInt()
                        .coerceAtLeast(1)
        }
    }

    override fun onResume() {
        super.onResume()

        // Refresh setelah pengguna kembali ke halaman.
        if (_binding != null &&
            viewModel.uiState.value.daftar.isNotEmpty()) {
            viewModel.refresh()
        }
    }

    override fun onDestroyView() {
        binding.rvPeminjaman.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
