
package id.sch.santaagnes.sim.ui.grades

import id.sch.santaagnes.sim.data.repository.AttendanceRepository
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.Resource
import id.sch.santaagnes.sim.data.repository.AuthRepository
import id.sch.santaagnes.sim.data.repository.GradesRepository
import id.sch.santaagnes.sim.databinding.FragmentTeacherDashboardBinding
import id.sch.santaagnes.sim.ui.calendar.AcademicCalendarViewModel
import id.sch.santaagnes.sim.ui.calendar.bindDashboardCalendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.recyclerview.widget.GridLayoutManager
import id.sch.santaagnes.sim.ui.home.HomeShortcut
import id.sch.santaagnes.sim.ui.home.HomeShortcutAdapter
import id.sch.santaagnes.sim.data.remote.BukuPenghubungApi
import id.sch.santaagnes.sim.data.remote.BacaBukuPenghubungApi

@AndroidEntryPoint
class TeacherDashboardFragment : Fragment() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var gradesRepository: GradesRepository

    private val calendarViewModel: AcademicCalendarViewModel by viewModels()
    @Inject
    lateinit var attendanceRepository: AttendanceRepository

    @Inject
    lateinit var bukuPenghubungApi: BukuPenghubungApi

    @Inject
    lateinit var bacaBukuApi: BacaBukuPenghubungApi

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
            findNavController().navigate(R.id.teacherClassesFragment)
        }

        binding.rvShortcuts.layoutManager = GridLayoutManager(requireContext(), SHORTCUT_COLUMNS)
        renderShortcuts()

        // Menu buku penghubung hanya untuk guru yang punya siswa terkait.
        viewLifecycleOwner.lifecycleScope.launch {
            val canRead = hasData { bacaBukuApi.getSiswa().let { it.success && !it.data.isNullOrEmpty() } }
            val canWrite = hasData { bukuPenghubungApi.getKelas().let { it.success && !it.data.isNullOrEmpty() } }
            val canSubstitute = hasData {
                attendanceRepository.slots(AttendanceRepository.SUBSTITUTE_SLOTS).isNotEmpty()
            }
            if (_binding == null) return@launch
            showReadLiaison = canRead
            showWriteLiaison = canWrite
            showSubstituteAttendance = canSubstitute
            renderShortcuts()
        }

        bindDashboardCalendar(binding.calendarCard, calendarViewModel)

        // Memuat data dashboard
        refreshDashboard()
    }

    override fun onStart() {
        super.onStart()
        calendarViewModel.load()
    }

    // Menu bersyarat: dipasang setelah pengecekan ke server selesai.
    private var showWriteLiaison = false
    private var showReadLiaison = false
    private var showHomeroomMenus = false
    private var showSubstituteAttendance = false

    private suspend fun hasData(check: suspend () -> Boolean): Boolean =
        try {
            check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    private fun renderShortcuts() {
        val nav = findNavController()
        val go = { id: Int -> { nav.navigate(id) } }
        val items = mutableListOf(
            HomeShortcut(R.string.shortcut_teacher_leave, R.drawable.ic_check, go(R.id.homeroomPendingFragment)),
            HomeShortcut(R.string.shortcut_teacher_grade_report, R.drawable.ic_grades, go(R.id.teacherGradeReportFragment)),
            HomeShortcut(R.string.shortcut_violation_input, R.drawable.ic_warning, go(R.id.action_teacherHome_to_inputPelanggaran)),
            HomeShortcut(R.string.shortcut_violation_history, R.drawable.ic_shield_person, go(R.id.action_teacherHome_to_riwayatPelanggaran)),
            HomeShortcut(R.string.shortcut_booking_apply, R.drawable.ic_building, go(R.id.teacherAjukanPeminjamanFragment)),
            HomeShortcut(R.string.home_shortcut_booking_history, R.drawable.ic_facility, go(R.id.teacherRiwayatPeminjamanFragment)),
            HomeShortcut(R.string.shortcut_announcements, R.drawable.ic_bell, go(R.id.action_global_announcements)),
            HomeShortcut(R.string.shortcut_calendar, R.drawable.ic_calendar, go(R.id.action_global_calendar))
        )
        if (showWriteLiaison) {
            items += HomeShortcut(R.string.shortcut_liaison_write, R.drawable.ic_edit, go(R.id.action_teacherHome_to_bukuPenghubung))
        }
        if (showReadLiaison) {
            items += HomeShortcut(R.string.shortcut_liaison_read, R.drawable.ic_book, go(R.id.teacherBacaBukuPenghubungFragment))
        }
        if (showHomeroomMenus) {
            items += HomeShortcut(R.string.shortcut_homeroom_profile, R.drawable.ic_person, go(R.id.action_teacherHome_to_profilSiswaBinaan))
            items += HomeShortcut(R.string.shortcut_homeroom_attendance, R.drawable.ic_location) {
                nav.navigate(R.id.teacherAttendanceRecapFragment, bundleOf("homeroomOnly" to true))
            }
            items += HomeShortcut(R.string.shortcut_homeroom_notes, R.drawable.ic_book) {
                nav.navigate(R.id.teacherProgressNotesFragment, bundleOf("assignmentId" to 0))
            }
        }
        if (showSubstituteAttendance) {
            items += HomeShortcut(R.string.shortcut_substitute_attendance, R.drawable.ic_check) {
                nav.navigate(
                    R.id.attendanceFragment,
                    bundleOf("assignmentId" to AttendanceRepository.SUBSTITUTE_SLOTS, "assignmentLabel" to "")
                )
            }
        }
        binding.rvShortcuts.adapter = HomeShortcutAdapter(items)
    }

    private fun refreshDashboard() {
        viewLifecycleOwner.lifecycleScope.launch {

            binding.progressBar.visibility = View.VISIBLE
            binding.tvHomeStatus.text = ""

            try {
                // Mengambil profil guru
                val user = (authRepository.getProfile() as? Resource.Success)?.data

                if (_binding == null) return@launch

                binding.tvTeacherName.text =
                    user?.name ?: "Guru SMAK Santa Agnes"

                if (user != null && user.isHomeroomTeacher != showHomeroomMenus) {
                    showHomeroomMenus = user.isHomeroomTeacher
                    renderShortcuts()
                }

                // Mengambil daftar kelas yang diajar
                val assignments = gradesRepository.assignments()

                if (_binding == null) return@launch

                binding.classesPreviewContainer.removeAllViews()

                // Menampilkan maksimal 3 kelas secara ringkas pada dashboard (detail jadwal dibuka saat diklik)
                assignments.take(3).forEach { assignment ->
                    val card = inflateTeacherClassCard(
                        inflater = layoutInflater,
                        parent = binding.classesPreviewContainer,
                        assignment = assignment,
                        showNotes = false,
                        onAttendanceClick = {
                            findNavController().navigate(
                                R.id.attendanceFragment,
                                bundleOf(
                                    "assignmentId" to assignment.id,
                                    "assignmentLabel" to assignment.label
                                )
                            )
                        },
                        onGradesClick = {
                            findNavController().navigate(
                                R.id.gradeManageFragment,
                                bundleOf(
                                    "assignmentId" to assignment.id,
                                    "assignmentLabel" to assignment.label
                                )
                            )
                        }
                    )
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

    private companion object {
        const val SHORTCUT_COLUMNS = 4
    }
}
