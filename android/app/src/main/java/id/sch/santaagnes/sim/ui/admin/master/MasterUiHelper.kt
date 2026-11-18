package id.sch.santaagnes.sim.ui.admin.master

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.ItemSheetOptionBinding
import id.sch.santaagnes.sim.databinding.LayoutAdminMasterHeaderBinding
import id.sch.santaagnes.sim.databinding.LayoutAdminMasterTabsBinding
import id.sch.santaagnes.sim.databinding.LayoutSheetFormShellBinding
import id.sch.santaagnes.sim.databinding.LayoutSheetOptionPickerBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class MasterTab {
    USER,
    STUDENT,
    TEACHER,
    CLASS
}

data class AgnesianOption(
    val id: String,
    val title: String,
    val subtitle: String? = null
)

fun setupMasterHeader(
    header: LayoutAdminMasterHeaderBinding,
    navController: NavController,
    titleRes: Int,
    showImport: Boolean = true,
    onImport: (() -> Unit)? = null,
    onAdd: () -> Unit
) {
    header.tvTitle.setText(titleRes)
    header.btnBack.setOnClickListener {
        if (!navController.popBackStack(R.id.adminDashboardFragment, false)) {
            navController.navigateUp()
        }
    }
    header.tvSemesterBadge.setOnClickListener {
        if (navController.currentDestination?.id != R.id.adminAcademicYearFragment) {
            navController.navigate(R.id.adminAcademicYearFragment)
        }
    }
    header.btnImport.isVisible = showImport
    header.btnImport.setOnClickListener {
        if (onImport != null) {
            onImport()
        } else {
            Toast.makeText(
                header.root.context,
                R.string.master_import_unavailable,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    header.btnAdd.setOnClickListener { onAdd() }
}

fun setupMasterTabs(
    tabs: LayoutAdminMasterTabsBinding,
    navController: NavController,
    activeTab: MasterTab
) {
    val tabMap = listOf(
        Triple(tabs.tabUser, MasterTab.USER, R.id.adminUserFragment),
        Triple(tabs.tabStudent, MasterTab.STUDENT, R.id.adminStudentFragment),
        Triple(tabs.tabTeacher, MasterTab.TEACHER, R.id.adminTeacherFragment),
        Triple(tabs.tabClass, MasterTab.CLASS, R.id.adminClassFragment)
    )
    tabMap.forEach { (view, tab, destId) ->
        applyTabStyle(view, tab == activeTab)
        view.setOnClickListener {
            if (tab == activeTab) return@setOnClickListener
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.adminUserFragment, inclusive = (destId == R.id.adminUserFragment))
                .build()
            navController.navigate(destId, null, options)
        }
    }
}

private fun applyTabStyle(view: TextView, active: Boolean) {
    view.setBackgroundResource(if (active) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
    view.setTextColor(
        ContextCompat.getColor(
            view.context,
            if (active) R.color.white else R.color.agnesian_blue
        )
    )
}

/** Menampilkan MaterialDatePicker bertema Agnesian dengan konversi aman tanggal YYYY-MM-DD (UTC). */
fun showIsoDatePicker(
    fragmentManager: FragmentManager,
    currentValue: String?,
    title: String = "Pilih Tanggal",
    onDatePicked: (String) -> Unit
) {
    if (fragmentManager.findFragmentByTag("agnesian_date_picker") != null) return

    val initialSelection = runCatching {
        currentValue?.trim()?.takeIf { it.isNotEmpty() }?.let { iso ->
            LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }.getOrNull() ?: MaterialDatePicker.todayInUtcMilliseconds()

    val picker = MaterialDatePicker.Builder.datePicker()
        .setTheme(R.style.ThemeOverlay_Agnesian_DatePicker)
        .setTitleText(title)
        .setSelection(initialSelection)
        .build()

    picker.addOnPositiveButtonClickListener { millis ->
        val picked = Instant.ofEpochMilli(millis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        onDatePicked(picked)
    }
    picker.show(fragmentManager, "agnesian_date_picker")
}

fun bindDateField(
    layout: TextInputLayout,
    input: TextInputEditText,
    fragmentManager: FragmentManager,
    title: String
) {
    val openPicker = View.OnClickListener {
        showIsoDatePicker(fragmentManager, input.text?.toString(), title) {
            input.setText(it)
            layout.error = null
        }
    }
    input.setOnClickListener(openPicker)
    layout.setEndIconOnClickListener(openPicker)
}

fun showAgnesianOptionSheet(
    context: Context,
    title: String,
    subtitle: String? = null,
    options: List<AgnesianOption>,
    selectedIndex: Int = -1,
    onSelected: (Int, AgnesianOption) -> Unit
) {
    val dialog = BottomSheetDialog(context, R.style.ThemeOverlay_Agnesian_BottomSheetDialog)
    val binding = LayoutSheetOptionPickerBinding.inflate(LayoutInflater.from(context))
    binding.tvPickerTitle.text = title
    binding.tvPickerSubtitle.isVisible = !subtitle.isNullOrBlank()
    binding.tvPickerSubtitle.text = subtitle

    val indexed = options.mapIndexed { idx, opt -> idx to opt }
    var visibleItems = indexed

    val adapter = object : RecyclerView.Adapter<OptionHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionHolder {
            val row = ItemSheetOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return OptionHolder(row)
        }

        override fun getItemCount(): Int = visibleItems.size

        override fun onBindViewHolder(holder: OptionHolder, position: Int) {
            val (origIndex, option) = visibleItems[position]
            val selected = origIndex == selectedIndex
            holder.binding.tvOptionTitle.text = option.title
            holder.binding.tvOptionTitle.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (selected) R.color.agnesian_blue else R.color.agnesian_text
                )
            )
            holder.binding.tvOptionSubtitle.isVisible = !option.subtitle.isNullOrBlank()
            holder.binding.tvOptionSubtitle.text = option.subtitle
            holder.binding.ivSelected.isVisible = selected
            holder.binding.rowOption.setBackgroundResource(
                if (selected) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
            )
            holder.binding.rowOption.setOnClickListener {
                dialog.dismiss()
                onSelected(origIndex, option)
            }
        }
    }

    binding.rvOptions.layoutManager = LinearLayoutManager(context)
    binding.rvOptions.adapter = adapter

    binding.etOptionSearch.isVisible = options.size > 6
    if (options.size > 6) {
        binding.etOptionSearch.addTextChangedListener { editable ->
            val q = editable?.toString().orEmpty().trim().lowercase()
            visibleItems = if (q.isEmpty()) {
                indexed
            } else {
                indexed.filter { (_, opt) ->
                    opt.title.lowercase().contains(q) ||
                        (opt.subtitle?.lowercase()?.contains(q) == true)
                }
            }
            adapter.notifyDataSetChanged()
        }
    }

    dialog.setContentView(binding.root)
    expandBottomSheet(dialog)
    dialog.show()
}

fun showAgnesianFormSheet(
    context: Context,
    title: String,
    subtitle: String? = null,
    contentView: View,
    primaryLabel: String = "Simpan",
    secondaryLabel: String = "Batal",
    showPrimary: Boolean = true,
    onPrimary: (BottomSheetDialog) -> Boolean
): BottomSheetDialog {
    val dialog = BottomSheetDialog(context, R.style.ThemeOverlay_Agnesian_BottomSheetDialog)
    val shell = LayoutSheetFormShellBinding.inflate(LayoutInflater.from(context))
    shell.tvSheetTitle.text = title
    shell.tvSheetSubtitle.isVisible = !subtitle.isNullOrBlank()
    shell.tvSheetSubtitle.text = subtitle
    shell.sheetContent.addView(contentView)

    shell.btnSecondary.text = secondaryLabel
    shell.btnSecondary.setOnClickListener { dialog.dismiss() }

    shell.btnPrimary.isVisible = showPrimary
    shell.btnPrimary.text = primaryLabel
    shell.btnPrimary.setOnClickListener {
        if (onPrimary(dialog)) {
            dialog.dismiss()
        }
    }

    dialog.setContentView(shell.root)
    expandBottomSheet(dialog)
    dialog.show()
    return dialog
}

fun bindFilterPill(
    pill: TextView,
    sheetTitle: String,
    options: () -> List<AgnesianOption>,
    selectedIndex: () -> Int,
    onSelected: (Int, AgnesianOption) -> Unit
) {
    pill.setOnClickListener {
        val opts = options()
        if (opts.isEmpty()) return@setOnClickListener
        showAgnesianOptionSheet(
            context = pill.context,
            title = sheetTitle,
            options = opts,
            selectedIndex = selectedIndex(),
            onSelected = { idx, opt ->
                pill.text = opt.title
                onSelected(idx, opt)
            }
        )
    }
}

fun bindSelectorField(
    layout: TextInputLayout,
    input: TextInputEditText,
    sheetTitle: String,
    subtitle: String? = null,
    options: () -> List<AgnesianOption>,
    selectedIndex: () -> Int,
    onSelected: (Int, AgnesianOption) -> Unit
) {
    val openSheet = View.OnClickListener {
        val opts = options()
        if (opts.isEmpty()) return@OnClickListener
        showAgnesianOptionSheet(
            context = input.context,
            title = sheetTitle,
            subtitle = subtitle,
            options = opts,
            selectedIndex = selectedIndex(),
            onSelected = { idx, opt ->
                input.setText(opt.title)
                layout.error = null
                onSelected(idx, opt)
            }
        )
    }
    input.setOnClickListener(openSheet)
    layout.setEndIconOnClickListener(openSheet)
}

private fun expandBottomSheet(dialog: BottomSheetDialog) {
    dialog.setOnShowListener {
        val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return@setOnShowListener
        BottomSheetBehavior.from(sheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }
}

private class OptionHolder(val binding: ItemSheetOptionBinding) : RecyclerView.ViewHolder(binding.root)
