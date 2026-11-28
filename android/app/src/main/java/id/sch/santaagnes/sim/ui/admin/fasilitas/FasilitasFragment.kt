package id.sch.santaagnes.sim.ui.admin.fasilitas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import id.sch.santaagnes.sim.data.remote.FasilitasDto
import id.sch.santaagnes.sim.data.remote.SimpanFasilitasRequest
import id.sch.santaagnes.sim.databinding.DialogFasilitasBinding
import id.sch.santaagnes.sim.databinding.FragmentFasilitasBinding
import kotlinx.coroutines.launch
import android.text.Editable
import android.text.TextWatcher
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

@AndroidEntryPoint
class FasilitasFragment : Fragment() {
    private var semuaFasilitas = listOf<FasilitasDto>()
    private var filterJenis: String? = null
    private var sedangLoading = false
    private var adaError = false
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

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.rvFasilitas.layoutManager =
            LinearLayoutManager(requireContext())

        binding.rvFasilitas.adapter = adapter

        binding.etSearch.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    filterFasilitas()
                }

                override fun afterTextChanged(s: Editable?) {}
            }
        )

        binding.btnAdd.setOnClickListener {
            tampilForm(null)
        }

        binding.btnSemua.setOnClickListener {
            filterJenis = null
            updateButtonActive(binding.btnSemua)
            filterFasilitas()
        }

        binding.btnRuangan.setOnClickListener {
            filterJenis = "ROOM"
            updateButtonActive(binding.btnRuangan)
            filterFasilitas()
        }

        binding.btnBarang.setOnClickListener {
            filterJenis = "ITEM"
            updateButtonActive(binding.btnBarang)
            filterFasilitas()
        }

        // Secara default, Semua aktif
        updateButtonActive(binding.btnSemua)

        binding.btnRetry.setOnClickListener {
            viewModel.getFasilitas(null)
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

                    semuaFasilitas = state.daftar
                    sedangLoading = state.loading
                    adaError = state.error != null

                    filterFasilitas()

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

        viewModel.getFasilitas(null)
    }

    private fun filterFasilitas() {

        val keyword = binding.etSearch.text
            ?.toString()
            ?.trim()
            .orEmpty()

        val hasil = semuaFasilitas.filter { item ->

            val sesuaiJenis =
                filterJenis == null || item.type == filterJenis

            val sesuaiPencarian =
                item.name.contains(keyword, ignoreCase = true) ||
                        item.type.contains(keyword, ignoreCase = true)

            sesuaiJenis && sesuaiPencarian

        }.sortedBy { it.name.lowercase() }

        adapter.setData(hasil)

        // Total fasilitas, tidak terpengaruh filter
        binding.tvTotal.text = semuaFasilitas.size.toString()

        binding.tvEmpty.isVisible =
            hasil.isEmpty() && !sedangLoading && !adaError

        binding.tvEmpty.text = if (keyword.isNotBlank()) {
            "Fasilitas tidak ditemukan"
        } else if (filterJenis != null) {
            "Belum ada fasilitas pada kategori ini"
        } else {
            "Belum ada fasilitas terdaftar"
        }
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

                form.tvDialogError.isVisible = false
                viewModel.simpan(
                    item?.id,
                    request,
                    onSuccess = { dialog.dismiss() },
                    onError = { pesan ->
                        form.tvDialogError.text = pesan
                        form.tvDialogError.isVisible = true
                    }
                )
            }
        }

        dialog.show()
    }

    private fun updateButtonActive(activeButton: MaterialButton) {

        val warnaBiru = ContextCompat.getColor(
            requireContext(),
            R.color.agnesian_blue
        )

        val warnaPutih = ContextCompat.getColor(
            requireContext(),
            R.color.white
        )

        val buttons = listOf(
            binding.btnSemua,
            binding.btnRuangan,
            binding.btnBarang
        )

        buttons.forEach { button ->

            val isActive = button == activeButton

            button.backgroundTintList = ColorStateList.valueOf(
                if (isActive) warnaBiru else warnaPutih
            )

            button.setTextColor(
                if (isActive) warnaPutih else warnaBiru
            )

            button.strokeColor = ColorStateList.valueOf(warnaBiru)

            button.strokeWidth = if (isActive) 0 else
                (resources.displayMetrics.density).toInt()
                    .coerceAtLeast(1)
        }
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