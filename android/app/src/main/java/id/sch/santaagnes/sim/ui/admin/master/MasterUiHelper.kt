package id.sch.santaagnes.sim.ui.admin.master

import android.app.DatePickerDialog
import android.content.Context
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.databinding.LayoutAdminMasterHeaderBinding
import id.sch.santaagnes.sim.databinding.LayoutAdminMasterTabsBinding
import java.util.Calendar
import java.util.Locale

enum class MasterTab {
    USER,
    STUDENT,
    TEACHER,
    CLASS
}

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
            if (active) R.color.white else R.color.agnesian_button
        )
    )
}

fun showIsoDatePicker(
    context: Context,
    currentValue: String?,
    onDatePicked: (String) -> Unit
) {
    val cal = Calendar.getInstance()
    val parts = currentValue?.split("-")
    if (parts != null && parts.size == 3) {
        val y = parts[0].toIntOrNull()
        val m = parts[1].toIntOrNull()
        val d = parts[2].toIntOrNull()
        if (y != null && m != null && d != null) {
            cal.set(y, m - 1, d)
        }
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onDatePicked(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}
