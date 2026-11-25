package id.sch.santaagnes.sim.ui.admin.journal

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.data.remote.AdminJournalDto
import id.sch.santaagnes.sim.databinding.FragmentAdminJournalBinding
import id.sch.santaagnes.sim.ui.admin.report.ReportDropdown
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ABS-04: pemantauan jurnal mengajar Admin; mendukung filter kelas, guru, dan rentang tanggal. */
@AndroidEntryPoint
class AdminJournalFragment : Fragment() {
    private var _binding: FragmentAdminJournalBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminJournalViewModel by viewModels()
    private val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))
    private var renderedItems: List<AdminJournalDto> = emptyList()
    private lateinit var classDropdown: ReportDropdown
    private lateinit var teacherDropdown: ReportDropdown

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminJournalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnJournalBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnJournalRetry.setOnClickListener { viewModel.refresh() }
        binding.btnJournalMore.setOnClickListener { viewModel.nextPage() }
        binding.btnJournalStart.setOnClickListener { pickDate(viewModel.state.value.startDate) { viewModel.setStart(it) } }
        binding.btnJournalEnd.setOnClickListener { pickDate(viewModel.state.value.endDate) { viewModel.setEnd(it) } }
        binding.btnJournalClear.setOnClickListener { viewModel.clearFilters() }
        classDropdown = ReportDropdown(binding.inputJournalClass, viewModel::setClassGroup)
        teacherDropdown = ReportDropdown(binding.inputJournalTeacher, viewModel::setTeacher)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun pickDate(current: LocalDate?, onSelected: (LocalDate) -> Unit) {
        val date = current ?: DateTimeWib.today()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            onSelected(LocalDate.of(year, month + 1, day))
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }

    private fun render(state: AdminJournalState) {
        binding.progressJournal.isVisible = state.loading
        binding.tvJournalError.isVisible = state.error != null
        binding.btnJournalRetry.isVisible = state.error != null
        binding.tvJournalError.text = state.error.orEmpty()
        classDropdown.render(
            listOf(null to "Semua Kelas") + state.classes.map { it.id to it.name },
            state.classGroupId
        )
        teacherDropdown.render(
            listOf(null to "Semua Guru") + state.teachers.map { it.id to it.name },
            state.teacherId
        )
        binding.btnJournalStart.text = state.startDate?.format(formatter) ?: "Dari tanggal"
        binding.btnJournalEnd.text = state.endDate?.format(formatter) ?: "Sampai tanggal"
        val blue = requireContext().getColor(R.color.agnesian_blue)
        val white = requireContext().getColor(R.color.white)
        listOf(binding.btnJournalStart to (state.startDate != null), binding.btnJournalEnd to (state.endDate != null))
            .forEach { (btn, active) ->
                btn.backgroundTintList = ColorStateList.valueOf(if (active) blue else white)
                btn.setTextColor(if (active) white else blue)
            }
        binding.btnJournalClear.isVisible = state.hasActiveFilter
        binding.tvJournalTotal.text = "${state.total} sesi jurnal mengajar"
        binding.tvJournalEmpty.isVisible = !state.loading && state.error == null && state.items.isEmpty()
        binding.btnJournalMore.isVisible = state.canLoadMore
        if (state.items != renderedItems) {
            renderedItems = state.items
            binding.journalList.removeAllViews()
            state.items.forEach(::addJournalCard)
        }
    }

    private fun addJournalCard(item: AdminJournalDto) {
        val context = requireContext()
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (density * n).toInt()
        val card = MaterialCardView(context).apply {
            radius = dp(14).toFloat()
            cardElevation = dp(1).toFloat()
            setCardBackgroundColor(context.getColor(R.color.white))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        val readableDate = runCatching { LocalDate.parse(item.date).format(formatter) }.getOrDefault(item.date)
        fun line(text: String, size: Float, bold: Boolean, colorRes: Int) {
            column.addView(TextView(context).apply {
                this.text = text
                textSize = size
                setTextColor(context.getColor(colorRes))
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(6))
            })
        }
        line(readableDate, 13f, true, R.color.agnesian_blue)
        line(item.subjectName, 16f, true, R.color.agnesian_text)
        line("${item.className}  •  ${item.teacherName}", 13f, true, R.color.agnesian_text)
        val hasJournal = !item.journal.isNullOrBlank()
        line(
            if (hasJournal) "Catatan Materi" else "Status Jurnal",
            12f,
            true,
            if (hasJournal) R.color.agnesian_muted else R.color.change_substitute
        )
        line(
            item.journal?.takeIf { it.isNotBlank() } ?: "Belum ada isi jurnal yang dicatat.",
            14f,
            !hasJournal,
            if (hasJournal) R.color.agnesian_text else R.color.change_substitute
        )
        card.addView(column)
        binding.journalList.addView(card)
    }

    override fun onDestroyView() {
        renderedItems = emptyList()
        _binding = null
        super.onDestroyView()
    }
}
