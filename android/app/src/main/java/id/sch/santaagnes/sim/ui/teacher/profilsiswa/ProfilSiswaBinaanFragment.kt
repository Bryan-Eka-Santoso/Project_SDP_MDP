package id.sch.santaagnes.sim.ui.teacher.profilsiswa

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.remote.*
import id.sch.santaagnes.sim.databinding.FragmentProfilSiswaBinaanBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class ProfilSiswaBinaanFragment : Fragment() {

    private var _binding: FragmentProfilSiswaBinaanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfilSiswaBinaanViewModel by viewModels()

    private var daftarSiswa = listOf<SiswaBinaanDto>()
    private var selectedId: Int? = null
    private var profilDitampilkan: ProfilSiswaBinaanDto? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfilSiswaBinaanBinding.inflate(
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

                    if (daftarSiswa != state.siswa) {
                        daftarSiswa = state.siswa
                        setupSiswa()
                    }

                    if (selectedId != state.selectedStudentId) {
                        selectedId = state.selectedStudentId

                        val siswa = daftarSiswa.firstOrNull {
                            it.id == selectedId
                        }

                        binding.etSiswa.setText(
                            siswa?.let {
                                "${it.name} (${it.nis}) - ${it.className}"
                            }.orEmpty(),
                            false
                        )
                    }

                    binding.cardProfil.isVisible =
                        state.profil != null && !state.loading

                    if (profilDitampilkan != state.profil) {
                        profilDitampilkan = state.profil

                        state.profil?.let {
                            tampilkanProfil(it)
                        } ?: binding.containerWali.removeAllViews()
                    }

                    binding.tvEmpty.isVisible =
                        !state.loading &&
                                state.error == null &&
                                daftarSiswa.isEmpty()
                }
            }
        }

        viewModel.loadSiswa()
    }

    private fun setupSiswa() {

        val labels = daftarSiswa.map {
            "${it.name} (${it.nis}) - ${it.className}"
        }

        val siswaAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            labels
        )

        binding.etSiswa.setAdapter(siswaAdapter)

        binding.etSiswa.setOnItemClickListener { _, _, position, _ ->
            viewModel.pilihSiswa(daftarSiswa[position].id)
        }
    }

    private fun tampilkanProfil(profil: ProfilSiswaBinaanDto) {

        binding.tvNama.text = profil.name
        binding.tvNis.text = "NIS: ${profil.nis}"

        binding.tvKelas.text = profil.classes
            .joinToString(", ") { it.name }
            .ifBlank { "-" }

        binding.tvTahunAjaran.text = profil.classes
            .map { it.academicYear }
            .distinct()
            .joinToString(", ")
            .ifBlank { "-" }

        binding.tvEmail.text = profil.email

        binding.tvTanggalLahir.text =
            formatTanggal(profil.birthDate)

        binding.tvTelepon.text =
            profil.phone?.takeIf { it.isNotBlank() }
                ?: "Belum tersedia"

        binding.tvStatus.text = when (profil.status) {
            "ACTIVE" -> "Aktif"
            "INACTIVE" -> "Tidak Aktif"
            else -> profil.status
        }

        // Tampilkan seluruh wali murid terhubung
        binding.containerWali.removeAllViews()

        if (profil.guardians.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "Belum ada data wali murid."
                textSize = 13f
                setTextColor(
                    resources.getColor(
                        R.color.agnesian_muted,
                        requireContext().theme
                    )
                )
            }

            binding.containerWali.addView(empty)
            return
        }

        profil.guardians.forEach { wali ->

            val item = LayoutInflater.from(requireContext())
                .inflate(
                    R.layout.item_wali_murid_profil,
                    binding.containerWali,
                    false
                )

            item.findViewById<TextView>(
                R.id.tvNamaWali
            ).text = wali.name

            item.findViewById<TextView>(
                R.id.tvHubunganWali
            ).text = "Hubungan: ${wali.relationship}"

            item.findViewById<TextView>(
                R.id.tvTeleponWali
            ).text = "Telepon: ${wali.phone}"

            item.findViewById<TextView>(
                R.id.tvEmailWali
            ).text = "Email: ${wali.email}"

            binding.containerWali.addView(item)
        }
    }

    private fun formatTanggal(value: String): String {
        return try {
            val input = SimpleDateFormat(
                "yyyy-MM-dd", Locale.US
            ).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val output = SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.forLanguageTag("id-ID")
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            output.format(input.parse(value)!!)
        } catch (e: Exception) {
            value
        }
    }

    override fun onDestroyView() {
        daftarSiswa = emptyList()
        selectedId = null
        profilDitampilkan = null
        _binding = null
        super.onDestroyView()
    }
}
