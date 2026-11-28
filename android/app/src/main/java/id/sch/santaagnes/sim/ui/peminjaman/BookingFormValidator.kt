package id.sch.santaagnes.sim.ui.peminjaman

import androidx.annotation.StringRes
import id.sch.santaagnes.sim.R

data class BookingFormErrors(
    @StringRes val facility: Int? = null,
    @StringRes val date: Int? = null,
    @StringRes val startTime: Int? = null,
    @StringRes val endTime: Int? = null,
    @StringRes val purpose: Int? = null
) {
    val isValid: Boolean
        get() = listOf(facility, date, startTime, endTime, purpose).all { it == null }
}

/**
 * Semua kolom diperiksa sekaligus supaya setiap pesan wajib langsung terlihat. Tanggal berformat
 * `yyyy-MM-dd` dan jam `HH:mm`, jadi perbandingan teks sama dengan perbandingan waktu.
 */
fun validateBookingForm(
    hasFacility: Boolean,
    date: String,
    startTime: String,
    endTime: String,
    purpose: String,
    today: String,
    now: String
): BookingFormErrors = BookingFormErrors(
    facility = if (hasFacility) null else R.string.booking_error_facility,
    date = when {
        date.isBlank() -> R.string.booking_error_date_empty
        date < today -> R.string.booking_error_date_past
        else -> null
    },
    startTime = when {
        startTime.isBlank() -> R.string.booking_error_start_empty
        date == today && startTime <= now -> R.string.booking_error_start_past
        else -> null
    },
    endTime = when {
        endTime.isBlank() -> R.string.booking_error_end_empty
        startTime.isNotBlank() && endTime <= startTime -> R.string.booking_error_end_order
        else -> null
    },
    purpose = if (purpose.trim().length in 5..255) null else R.string.booking_error_purpose
)
