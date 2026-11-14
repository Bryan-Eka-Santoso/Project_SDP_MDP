
package id.sch.santaagnes.sim.ui.grades

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.data.repository.AuthRepository
import id.sch.santaagnes.sim.data.repository.GradesRepository
import id.sch.santaagnes.sim.databinding.FragmentTeacherDashboardBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TeacherDashboardFragment : Fragment() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var gradesRepository: GradesRepository

    private var _binding: FragmentTeacherDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        state: Bundle?
    ): View {
        _binding = FragmentTeacherDashboardBinding.inflate(
            inflater,
            parent,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Navigasi menuju seluruh kelas
        binding.btnAllClasses.setOnClickListener {
            findNavController().navigate(
                R.id.teacherClassesFragment
            )
        }

        // Navigasi menuju rekap nilai
        binding.btnGradeReport.setOnClickListener {
            findNavController().navigate(
                R.id.teacherGradeReportFragment
            )
        }

        // Bottom navigation - Home
        binding.bottomNav.navTeacherHome.setOnClickListener {
            refreshDashboard()
        }

        // Bottom navigation - Kelas
        binding.bottomNav.navTeacherClasses.setOnClickListener {
            findNavController().navigate(
                R.id.teacherClassesFragment
            )
        }

        // Memuat data dashboard
        refreshDashboard()
    }

    private fun refreshDashboard() {
        viewLifecycleOwner.lifecycleScope.launch {

            binding.progressBar.visibility = View.VISIBLE
            binding.tvHomeStatus.text = ""

            try {
                // Mengambil profil guru
                val user = authRepository.getProfile().getOrNull()

                if (_binding == null) return@launch

                binding.tvTeacherName.text =
                    user?.name ?: "Guru SMA Santa Agnes"

                // Mengambil daftar kelas yang diajar
                val assignments = gradesRepository.assignments()

                if (_binding == null) return@launch

                binding.classesPreviewContainer.removeAllViews()

                // Menampilkan maksimal 3 kelas pada dashboard
                assignments.take(3).forEach { assignment ->

                    val card = layoutInflater.inflate(
                        R.layout.item_grade_class,
                        binding.classesPreviewContainer,
                        false
                    )

                    // Nama mata pelajaran dan kelas
                    card.findViewById<TextView>(
                        R.id.tvAssignment
                    ).text =
                        "${assignment.subjectName} · ${assignment.className}"

                    // Informasi semester dan jadwal
                    card.findViewById<TextView>(
                        R.id.tvClassSchedule
                    ).text =
                        if (assignment.schedules.isEmpty()) {
                            "Semester ${assignment.semesterName} · Jadwal belum tersedia"
                        } else {
                            "Semester ${assignment.semesterName}\n" +
                                    assignment.schedules.joinToString("\n") { slot ->
                                        "${slot.day}  ${slot.startTime}–${slot.endTime}"
                                    }
                        }

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

                    binding.classesPreviewContainer.addView(card)
                }

                // Pesan jika tidak ada kelas
                binding.tvHomeStatus.text =
                    if (assignments.isEmpty()) {
                        "Belum ada kelas yang diajar."
                    } else {
                        ""
                    }

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {

                if (_binding != null) {
                    binding.tvHomeStatus.text =
                        GradesRepository.friendlyError(e)
                }

            } finally {

                if (_binding != null) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
