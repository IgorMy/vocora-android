package io.github.igormy.vocora.recorder.logic

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Groups recordings the way a call log does: by the day they happened, newest day first.
 *
 * Recent days are named rather than dated, because "Today" says more at a glance than a date the
 * reader has to compare against today's.
 */
object RecordingDay {

    /** Midnight of the day [millis] falls on, which is what makes two recordings the same group. */
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** `Today`, `Yesterday`, or the date. [today] is passed in so this stays testable. */
    fun header(dayStart: Long, today: Long, locale: Locale = Locale.getDefault()): String {
        val todayStart = startOfDay(today)
        val daysApart = ((todayStart - startOfDay(dayStart)) / MILLIS_PER_DAY).toInt()
        return when (daysApart) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> SimpleDateFormat("d MMMM yyyy", locale).format(Date(dayStart))
        }
    }

    private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
}
