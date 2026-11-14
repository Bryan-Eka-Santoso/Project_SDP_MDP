package id.sch.santaagnes.sim.ui.admin.matapelajaran

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
import id.sch.santaagnes.sim.data.remote.MataPelajaranDto
import id.sch.santaagnes.sim.data.remote.SimpanMataPelajaranRequest
import id.sch.santaagnes.sim.databinding.DialogMataPelajaranBinding
import id.sch.santaagnes.sim.databinding.FragmentMataPelajaranBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MataPelajaranFragment : Fragment() {

    private var _binding: FragmentMataPelajaranBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MataPelajaranViewModel by viewModels()

    private val adapter = MataPelajaranAdapter(
        onEdit = { item -> tampilForm(item) },
        onDelete = { item -> konfirmasiHapus(item) }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMataPelajaranBinding.inflate(
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

        binding.rvSubjects.layoutManager =
            LinearLayoutManager(requireContext())

        binding.rvSubjects.adapter = adapter

        binding.btnAdd.setOnClickListener {
            tampilForm(null)
        }

        binding.btnRetry.setOnClickListener {
            viewModel.getMataPelajaran()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->

                    binding.progress.isVisible =
                        state.loading || state.saving

                    binding.btnAdd.isEnabled = !state.saving

                    binding.tvEmpty.isVisible =
                        state.daftar.isEmpty() &&
                                !state.loading &&
                                state.error == null

                    binding.tvError.isVisible = state.error != null
                    binding.tvError.text = state.error

                    binding.btnRetry.isVisible = state.error != null

                    adapter.setData(state.daftar)

                    state.pesan?.let { pesan ->
                        Toast.makeText(
                            requireContext(),
                            pesan,
                            Toast.LENGTH_SHORT
                        ).show()

                        viewModel.clearMessage()
                    }
                }
            }
        }

        viewModel.getMataPelajaran()
    }

    private fun tampilForm(item: MataPelajaranDto?) {
        val form = DialogMataPelajaranBinding.inflate(layoutInflater)

        form.etCode.setText(item?.code.orEmpty())
        form.etName.setText(item?.name.orEmpty())
        form.etType.setText(item?.type.orEmpty())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (item == null) {
                    "Tambah Mata Pelajaran"
                } else {
                    "Edit Mata Pelajaran"
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

                val code = form.etCode.text.toString().trim()
                val name = form.etName.text.toString().trim()
                val type = form.etType.text.toString().trim()

                form.etCode.error =
                    if (code.isBlank() || code.length > 20)
                        "Kode wajib diisi (maks. 20 karakter)"
                    else null

                form.etName.error =
                    if (name.isBlank() || name.length > 100)
                        "Nama wajib diisi (maks. 100 karakter)"
                    else null

                form.etType.error =
                    if (type.isBlank() || type.length > 30)
                        "Jenis wajib diisi (maks. 30 karakter)"
                    else null

                if (
                    form.etCode.error != null ||
                    form.etName.error != null ||
                    form.etType.error != null
                ) {
                    return@setOnClickListener
                }

                val request = SimpanMataPelajaranRequest(
                    code = code,
                    name = name,
                    type = type
                )

                viewModel.simpan(item?.id, request) {
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun konfirmasiHapus(item: MataPelajaranDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Hapus Mata Pelajaran")
            .setMessage("Apakah kamu ingin menghapus ${item.name}?")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                viewModel.hapus(item.id)
            }
            .show()
    }

    override fun onDestroyView() {
        binding.rvSubjects.adapter = null
        _binding = null
        super.onDestroyView()
    }
}