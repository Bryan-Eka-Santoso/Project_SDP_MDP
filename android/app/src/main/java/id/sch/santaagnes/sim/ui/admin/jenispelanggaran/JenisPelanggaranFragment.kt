package id.sch.santaagnes.sim.ui.admin.jenispelanggaran

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
import id.sch.santaagnes.sim.data.remote.JenisPelanggaranDto
import id.sch.santaagnes.sim.data.remote.SimpanJenisPelanggaranRequest
import id.sch.santaagnes.sim.databinding.DialogJenisPelanggaranBinding
import id.sch.santaagnes.sim.databinding.FragmentJenisPelanggaranBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class JenisPelanggaranFragment : Fragment() {

    private var _binding: FragmentJenisPelanggaranBinding? = null
    private val binding get() = _binding!!

    private val viewModel: JenisPelanggaranViewModel by viewModels()

    private val adapter = JenisPelanggaranAdapter(
        onEdit = { tampilForm(it) },
        onDelete = { konfirmasiHapus(it) }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJenisPelanggaranBinding.inflate(
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

        binding.rvPelanggaran.layoutManager =
            LinearLayoutManager(requireContext())

        binding.rvPelanggaran.adapter = adapter

        binding.btnAdd.setOnClickListener {
            tampilForm(null)
        }

        binding.btnRetry.setOnClickListener {
            viewModel.getJenisPelanggaran()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->

                    binding.progress.isVisible = state.loading
                    binding.btnAdd.isEnabled = !state.loading

                    binding.tvEmpty.isVisible =
                        state.daftar.isEmpty() &&
                                !state.loading &&
                                state.error == null

                    binding.tvError.isVisible =
                        state.error != null

                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null

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

                        viewModel.clearMessage()
                    }
                }
            }
        }

        viewModel.getJenisPelanggaran()
    }

    private fun tampilForm(item: JenisPelanggaranDto?) {
        val form = DialogJenisPelanggaranBinding.inflate(layoutInflater)

        form.etName.setText(item?.name.orEmpty())
        form.etPoints.setText(item?.points?.toString().orEmpty())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (item == null) {
                    "Tambah Jenis Pelanggaran"
                } else {
                    "Edit Jenis Pelanggaran"
                }
            )
            .setView(form.root)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                android.content.DialogInterface.BUTTON_POSITIVE
            ).setOnClickListener {

                val name = form.etName.text.toString().trim()
                val points = form.etPoints.text.toString()
                    .toIntOrNull()

                form.etName.error =
                    if (name.isBlank() || name.length > 100) {
                        "Nama wajib diisi (maks. 100 karakter)"
                    } else null

                form.etPoints.error =
                    if (points == null || points <= 0) {
                        "Bobot poin harus lebih dari 0"
                    } else null

                if (
                    form.etName.error != null ||
                    form.etPoints.error != null
                ) {
                    return@setOnClickListener
                }

                val request = SimpanJenisPelanggaranRequest(
                    name = name,
                    points = points!!
                )

                viewModel.simpan(item?.id, request) {
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun konfirmasiHapus(item: JenisPelanggaranDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Jenis Pelanggaran")
            .setMessage(
                "Apakah kamu ingin menghapus ${item.name}?"
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.hapus(item.id)
            }
            .show()
    }

    override fun onDestroyView() {
        binding.rvPelanggaran.adapter = null
        _binding = null
        super.onDestroyView()
    }
}