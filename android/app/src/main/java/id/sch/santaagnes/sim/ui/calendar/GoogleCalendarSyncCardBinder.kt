package id.sch.santaagnes.sim.ui.calendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import id.sch.santaagnes.sim.R
import id.sch.santaagnes.sim.core.DateTimeWib
import id.sch.santaagnes.sim.databinding.ViewGoogleCalendarSyncCardBinding
import id.sch.santaagnes.sim.domain.model.DeviceCalendarAccount
import id.sch.santaagnes.sim.ui.admin.master.AgnesianOption
import id.sch.santaagnes.sim.ui.admin.master.showAgnesianOptionSheet
import id.sch.santaagnes.sim.ui.profile.CalendarSyncUiState
import id.sch.santaagnes.sim.ui.profile.CalendarSyncViewModel
import kotlinx.coroutines.launch

private const val GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"

/**
 * Mengikat kartu sinkronisasi Google Calendar (F-JDW-06 / ADR-009) secara seragam di
 * halaman Kalender Akademik maupun Profil.
 */
fun Fragment.bindGoogleCalendarSyncCard(
    binding: ViewGoogleCalendarSyncCardBinding,
    viewModel: CalendarSyncViewModel,
    permissionLauncher: ActivityResultLauncher<Array<String>>,
    onPendingPermissionAction: (() -> Unit) -> Unit
) {
    fun hasPermission(): Boolean = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    ).all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }

    fun runWithPermission(action: () -> Unit) {
        if (hasPermission()) {
            action()
        } else {
            onPendingPermissionAction(action)
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    binding.switchCalendarSync.setOnCheckedChangeListener { _, checked ->
        if (checked == viewModel.uiState.value.enabled) return@setOnCheckedChangeListener
        if (!checked) {
            viewModel.disable()
        } else {
            if (!hasPermission()) {
                // Kembalikan posisi switch sementara sampai izin kalender benar-benar diberikan.
                binding.switchCalendarSync.isChecked = false
            }
            runWithPermission { viewModel.enable() }
        }
    }

    binding.btnSelectGoogleAccount.setOnClickListener {
        runWithPermission {
            viewLifecycleOwner.lifecycleScope.launch {
                val calendars = viewModel.loadAvailableCalendars()
                if (calendars.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.calendar_no_google_account),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                showCalendarAccountPicker(
                    calendars = calendars,
                    selectedId = viewModel.uiState.value.selectedCalendarId,
                    onSelect = viewModel::selectCalendar
                )
            }
        }
    }

    binding.btnSyncNow.setOnClickListener {
        runWithPermission { viewModel.syncNow() }
    }

    binding.btnOpenGoogleCalendar.setOnClickListener {
        openGoogleCalendarApp(requireContext())
    }

    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.uiState.collect { state -> renderGoogleCalendarCard(binding, state) }
        }
    }
}

private fun Fragment.showCalendarAccountPicker(
    calendars: List<DeviceCalendarAccount>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    val options = calendars.map { cal ->
        val provider = when {
            cal.isGoogle && cal.isPrimary -> "Google Calendar · Utama"
            cal.isGoogle -> "Google Calendar"
            cal.isPrimary -> "Kalender Perangkat · Utama"
            else -> "Kalender Perangkat"
        }
        val subtitle = buildList {
            add(provider)
            if (cal.displayName.isNotBlank() && cal.displayName != cal.shortLabel) {
                add(cal.displayName)
            }
        }.joinToString(" · ")
        AgnesianOption(id = cal.id.toString(), title = cal.shortLabel, subtitle = subtitle)
    }
    val selectedIdx = calendars.indexOfFirst { it.id == selectedId }
    showAgnesianOptionSheet(
        context = requireContext(),
        title = getString(R.string.calendar_pick_account_title),
        subtitle = getString(R.string.calendar_pick_account_subtitle),
        options = options,
        selectedIndex = selectedIdx
    ) { index, _ ->
        calendars.getOrNull(index)?.let { onSelect(it.id) }
    }
}

private fun Fragment.renderGoogleCalendarCard(
    binding: ViewGoogleCalendarSyncCardBinding,
    state: CalendarSyncUiState
) {
    binding.switchCalendarSync.isChecked = state.enabled
    binding.switchCalendarSync.isEnabled = !state.busy
    binding.tvCalendarSyncDesc.setText(
        if (state.includesBookings) R.string.calendar_sync_desc_with_bookings
        else R.string.calendar_sync_desc_academic
    )

    val showAccountPill = state.enabled || !state.calendarLabel.isNullOrBlank() || state.calendars.isNotEmpty()
    binding.btnSelectGoogleAccount.isVisible = showAccountPill
    binding.btnSelectGoogleAccount.isEnabled = !state.busy
    binding.btnSelectGoogleAccount.text = state.calendarLabel?.let {
        getString(R.string.calendar_account_active, it)
    } ?: getString(R.string.calendar_account_pick)

    binding.layoutSyncActions.isVisible = state.enabled
    binding.btnSyncNow.isEnabled = !state.busy
    binding.btnOpenGoogleCalendar.isEnabled = !state.busy

    val status = when {
        state.busy -> getString(R.string.calendar_sync_busy)
        state.errorMessage != null -> state.errorMessage
        state.enabled && state.result != null && state.lastSyncMillis != null -> getString(
            R.string.calendar_sync_status,
            DateTimeWib.formatTimestamp(state.lastSyncMillis),
            state.result.total,
            state.result.added,
            state.result.updated,
            state.result.removed
        )
        state.enabled && state.lastSyncMillis != null -> getString(
            R.string.calendar_sync_status_time_only,
            DateTimeWib.formatTimestamp(state.lastSyncMillis)
        )
        else -> null
    }
    binding.tvCalendarSyncStatus.isVisible = status != null
    binding.tvCalendarSyncStatus.text = status
    binding.tvCalendarSyncStatus.setTextColor(
        ContextCompat.getColor(
            requireContext(),
            if (state.errorMessage != null) R.color.change_cancel else R.color.agnesian_blue
        )
    )
}

/** Membuka aplikasi Google Calendar bila terpasang, atau aplikasi kalender bawaan perangkat. */
fun openGoogleCalendarApp(context: Context) {
    val timeUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
    val googleIntent = Intent(Intent.ACTION_VIEW, timeUri).apply {
        setPackage(GOOGLE_CALENDAR_PACKAGE)
    }
    val fallbackIntent = Intent(Intent.ACTION_VIEW, timeUri)
    val launched = runCatching { context.startActivity(googleIntent) }.isSuccess ||
        runCatching { context.startActivity(fallbackIntent) }.isSuccess
    if (!launched) {
        Toast.makeText(
            context,
            "Tidak ditemukan aplikasi Google Calendar pada perangkat ini.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

/** Memasang target Google Calendar pada Intent tambah agenda bila aplikasi Google Calendar terpasang. */
fun Intent.preferGoogleCalendar(context: Context): Intent {
    val withPackage = Intent(this).setPackage(GOOGLE_CALENDAR_PACKAGE)
    return if (withPackage.resolveActivity(context.packageManager) != null) withPackage else this
}
