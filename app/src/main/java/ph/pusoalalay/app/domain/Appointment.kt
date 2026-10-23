package ph.pusoalalay.app.domain

import java.util.UUID

data class Appointment(
    val id: String = UUID.randomUUID().toString(),
    val startsAt: Long,
    val remindAt: Long,
    val title: String,
    val note: String = ""
)

enum class AppointmentIssue { TITLE_REQUIRED, PAST_TIME, TOO_LONG }

/** Reminder lead times offered in the app: 1 hour, 2 hours, 1 day before. */
val REMINDER_LEADS = listOf(60 * 60_000L, 2 * 60 * 60_000L, 24 * 60 * 60_000L)

fun validateAppointment(appointment: Appointment, now: Long = System.currentTimeMillis()): AppointmentIssue? = when {
    appointment.title.isBlank() -> AppointmentIssue.TITLE_REQUIRED
    appointment.title.length > 100 || appointment.note.length > 500 -> AppointmentIssue.TOO_LONG
    appointment.startsAt <= now -> AppointmentIssue.PAST_TIME
    else -> null
}
