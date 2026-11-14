package id.sch.santaagnes.sim.ui.admin.fasilitas

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.data.remote.FasilitasDto
import id.sch.santaagnes.sim.data.remote.SimpanFasilitasRequest
import id.sch.santaagnes.sim.databinding.DialogFasilitasBinding
import id.sch.santaagnes.sim.databinding.FragmentFasilitasBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FasilitasFragment : Fragment() {

    private var _binding: FragmentFasilitasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FasilitasViewModel by viewModels()

    private val adapter = FasilitasAdapter(
        onEdit = { tampilForm(it) },
        onDelete = { konfirmasiHapus(it) }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFasilitasBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvFasilitas.layoutManager =
            LinearLayoutManager(requireContext())

        binding.rvFasilitas.adapter = adapter

        binding.btnAdd.setOnClickListener {
            tampilForm(null)
        }

        binding.btnSemua.setOnClickListener {
            viewModel.getFasilitas(null)
        }

        binding.btnRuangan.setOnClickListener {
            viewModel.getFasilitas("ROOM")
        }

        binding.btnBarang.setOnClickListener {
            viewModel.getFasilitas("ITEM")
        }

        binding.btnRetry.setOnClickListener {
            viewModel.getFasilitas()
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

                    binding.tvError.isVisible = state.error != null
                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible =
                        state.error != null

                    adapter.setData(state.daftar)

                    state.pesan?.let {
                        Toast.makeText(
                            requireContext(),
                            it,
                            Toast.LENGTH_SHORT
                        ).show()

                        viewModel.clearMessage()
                    }
                }
            }
        }

        viewModel.getFasilitas()
    }

    private fun tampilForm(item: FasilitasDto?) {
        val form = DialogFasilitasBinding.inflate(layoutInflater)

        form.etName.setText(item?.name.orEmpty())
        form.etQuantity.setText(
            item?.quantity?.toString() ?: "1"
        )

        val jenis = arrayOf("Ruangan", "Barang")
        val adapterJenis = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            jenis
        )

        form.etType.setAdapter(adapterJenis)
        form.etType.setText(
            if (item?.type == "ITEM") "Barang" else "Ruangan",
            false
        )

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (item == null) "Tambah Fasilitas"
                else "Edit Fasilitas"
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
                val quantity = form.etQuantity.text
                    .toString().toIntOrNull()

                val type = if (
                    form.etType.text.toString() == "Barang"
                ) "ITEM" else "ROOM"

                form.etName.error =
                    if (name.isBlank() || name.length > 100)
                        "Nama wajib diisi (maks. 100 karakter)"
                    else null

                form.etQuantity.error =
                    if (quantity == null || quantity <= 0)
                        "Jumlah harus lebih dari 0"
                    else null

                if (
                    form.etName.error != null ||
                    form.etQuantity.error != null
                ) {
                    return@setOnClickListener
                }

                val request = SimpanFasilitasRequest(
                    name = name,
                    type = type,
                    quantity = quantity!!
                )

                viewModel.simpan(item?.id, request) {
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun konfirmasiHapus(item: FasilitasDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Fasilitas")
            .setMessage("Hapus fasilitas ${item.name}?")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.hapus(item.id)
            }
            .show()
    }

    override fun onDestroyView() {
        binding.rvFasilitas.adapter = null
        _binding = null
        super.onDestroyView()
    }
}