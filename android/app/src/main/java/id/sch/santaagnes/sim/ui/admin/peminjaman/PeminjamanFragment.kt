package id.sch.santaagnes.sim.ui.admin.peminjaman

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
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

@AndroidEntryPoint
class PeminjamanFragment : Fragment() {

    private var _binding: FragmentPeminjamanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PeminjamanViewModel by viewModels()

    private val adapter = PeminjamanAdapter(
        onApprove = { konfirmasi(it, true) },
        onReject = { konfirmasi(it, false) }
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

        binding.rvPeminjaman.layoutManager =
            LinearLayoutManager(requireContext())

        binding.rvPeminjaman.adapter = adapter

        binding.btnMenunggu.setOnClickListener {
            viewModel.getPeminjaman("PENDING")
        }

        binding.btnDisetujui.setOnClickListener {
            viewModel.getPeminjaman("APPROVED")
        }

        binding.btnDitolak.setOnClickListener {
            viewModel.getPeminjaman("REJECTED")
        }

        binding.btnSemua.setOnClickListener {
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
        val judul = if (setujui) {
            "Setujui Peminjaman"
        } else {
            "Tolak Peminjaman"
        }

        val pesan = if (setujui) {
            "Setujui peminjaman ${item.facilityName} " +
                    "oleh ${item.requesterName}?"
        } else {
            "Tolak peminjaman ${item.facilityName} " +
                    "oleh ${item.requesterName}?"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(judul)
            .setMessage(pesan)
            .setNegativeButton("Batal", null)
            .setPositiveButton(
                if (setujui) "Setujui" else "Tolak"
            ) { _, _ ->
                viewModel.putuskan(item.id, setujui)
            }
            .show()
    }

    override fun onDestroyView() {
        binding.rvPeminjaman.adapter = null
        _binding = null
        super.onDestroyView()
    }
}