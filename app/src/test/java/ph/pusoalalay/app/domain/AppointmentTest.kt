package ph.pusoalalay.app.domain

import org.junit.Assert.*
import org.junit.Test

class AppointmentTest {
    private val now = 1_000_000_000L
    private fun appt(title: String = "Cardiology check-up", at: Long = now + 3_600_000L, note: String = "") =
        Appointment(startsAt = at, remindAt = at - REMINDER_LEADS[0], title = title, note = note)

    @Test fun validAppointment() = assertNull(validateAppointment(appt(), now))
    @Test fun titleRequired() = assertEquals(AppointmentIssue.TITLE_REQUIRED, validateAppointment(appt(title = "  "), now))
    @Test fun mustBeInFuture() = assertEquals(AppointmentIssue.PAST_TIME, validateAppointment(appt(at = now - 1), now))
    @Test fun lengthLimits() = assertEquals(AppointmentIssue.TOO_LONG, validateAppointment(appt(note = "x".repeat(501)), now))
}
