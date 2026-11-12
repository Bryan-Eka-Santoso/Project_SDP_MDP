
package id.sch.santaagnes.sim.ui.grades

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.FragmentTeacherClassesBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TeacherClassesFragment : Fragment() {

    private var _binding: FragmentTeacherClassesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TeacherClassesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeacherClassesBinding.inflate(
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

        // Tombol muat ulang kelas
        binding.btnReload.setOnClickListener {
            viewModel.load()
        }

        // Mengamati data dari ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->

                    binding.progressBar.visibility =
                        if (state.loading) View.VISIBLE else View.GONE

                    binding.tvStatus.text = state.error
                        ?: if (
                            !state.loading &&
                            state.assignments.isEmpty()
                        ) {
                            "Belum ada kelas yang diajar di semester aktif."
                        } else {
                            ""
                        }

                    // Bersihkan daftar sebelum menampilkan ulang
                    binding.classesContainer.removeAllViews()

                    state.assignments.forEach { assignment ->

                        val card = layoutInflater.inflate(
                            R.layout.item_grade_class,
                            binding.classesContainer,
                            false
                        )

                        // Nama kelas dan mata pelajaran
                        card.findViewById<TextView>(
                            R.id.tvAssignment
                        ).text = assignment.label

                        // Tombol Nilai
                        card.findViewById<Button>(
                            R.id.btnGrades
                        ).setOnClickListener {
                            findNavController().navigate(
                                R.id.gradeManageFragment,
                                bundleOf(
                                    "assignmentId" to assignment.id,
                                    "assignmentLabel" to assignment.label
                                )
                            )
                        }

                        // Tombol Absensi
                        card.findViewById<Button>(
                            R.id.btnAttendance
                        ).setOnClickListener {
                            findNavController().navigate(
                                R.id.attendanceFragment,
                                bundleOf(
                                    "assignmentId" to assignment.id,
                                    "assignmentLabel" to assignment.label
                                )
                            )
                        }

                        // Tambahkan card ke daftar
                        binding.classesContainer.addView(card)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
