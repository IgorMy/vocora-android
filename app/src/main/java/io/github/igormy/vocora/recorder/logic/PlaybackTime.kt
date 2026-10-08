package io.github.igormy.vocora.recorder.logic

private const val MILLIS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** Shows a position in a recording the way a player does. */
object PlaybackTime {

    /** `m:ss`, or `h:mm:ss` once a call runs past the hour. Negatives read as the start. */
    fun format(millis: Int): String {
        val totalSeconds = (millis.coerceAtLeast(0)) / MILLIS_PER_SECOND
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        val minutes = (totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
        val hours = totalSeconds / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)

        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /** `1x`, `1.5x`: trailing zeros help nobody. */
    fun formatSpeed(speed: Float): String =
        if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
}
