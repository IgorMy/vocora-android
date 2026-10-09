package io.github.igormy.vocora.recorder.logic

/** One turn of a call, and which side of it said so. */
data class TranscriptLine(val fromMe: Boolean, val text: String)

/**
 * What was said during a call.
 *
 * Who said what is a fact here rather than a guess, which is the whole reason each side is recorded
 * apart: what goes up the line is this phone, what comes down it is the other end.
 */
data class Transcript(val lines: List<TranscriptLine>, val text: String?) {
    val isEmpty: Boolean get() = lines.isEmpty() && text.isNullOrBlank()
}
