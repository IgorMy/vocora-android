package io.github.igormy.vocora.recorder.logic

/** An entry of the call log: who the call was with, which way it went, and when. */
data class CallLogEntry(val number: String, val direction: String, val date: Long)

/**
 * Parses the rows the `content` command prints, which look like:
 *
 *     Row: 0 number=617352491, type=2, date=1790965180029
 */
object CallLogRow {

    private val NUMBER = Regex("number=([^,]*)")
    private val TYPE = Regex("type=(\\d+)")
    private val DATE = Regex("date=(\\d+)")

    private const val INCOMING_TYPE = 1
    private const val OUTGOING_TYPE = 2

    const val UNKNOWN_NUMBER = "unknown"

    fun parse(row: String): CallLogEntry? {
        val date = DATE.find(row)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val number = NUMBER.find(row)?.groupValues?.get(1)?.trim().orEmpty()
            .ifBlank { UNKNOWN_NUMBER }
        val direction = when (TYPE.find(row)?.groupValues?.get(1)?.toIntOrNull()) {
            INCOMING_TYPE -> "incoming"
            OUTGOING_TYPE -> "outgoing"
            else -> "call"
        }
        return CallLogEntry(number, direction, date)
    }
}
