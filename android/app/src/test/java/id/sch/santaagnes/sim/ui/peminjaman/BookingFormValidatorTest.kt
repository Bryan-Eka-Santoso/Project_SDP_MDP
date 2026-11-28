package id.sch.santaagnes.sim.ui.peminjaman

import id.sch.santaagnes.sim.R
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingFormValidatorTest {

    private val today = "2026-11-28"
    private val now = "10:00"

    private fun validate(
        hasFacility: Boolean = true,
        date: String = "2026-12-01",
        start: String = "08:00",
        end: String = "09:00",
        purpose: String = "Latihan paduan suara"
    ) = validateBookingForm(hasFacility, date, start, end, purpose, today, now)

    @Test
    fun emptyFormReportsEveryRequiredFieldAtOnce() {
        val errors = validate(hasFacility = false, date = "", start = "", end = "", purpose = "")

        assertEquals(
            BookingFormErrors(
                facility = R.string.booking_error_facility,
                date = R.string.booking_error_date_empty,
                startTime = R.string.booking_error_start_empty,
                endTime = R.string.booking_error_end_empty,
                purpose = R.string.booking_error_purpose
            ),
            errors
        )
    }

    @Test
    fun endMustBeAfterStart() {
        assertEquals(
            BookingFormErrors(endTime = R.string.booking_error_end_order),
            validate(start = "09:00", end = "09:00")
        )
    }

    @Test
    fun pastDateIsRejected() {
        assertEquals(
            BookingFormErrors(date = R.string.booking_error_date_past),
            validate(date = "2026-11-27")
        )
    }

    @Test
    fun startTimeAlreadyPassedTodayIsRejected() {
        assertEquals(
            BookingFormErrors(startTime = R.string.booking_error_start_past),
            validate(date = today, start = "09:00", end = "11:00")
        )
    }

    @Test
    fun validFormHasNoErrors() {
        assertEquals(BookingFormErrors(), validate(date = today, start = "10:30", end = "11:30"))
        assertEquals(true, validate().isValid)
    }

    @Test
    fun shortPurposeIsRejected() {
        assertEquals(
            BookingFormErrors(purpose = R.string.booking_error_purpose),
            validate(purpose = "abcd")
        )
    }
}
