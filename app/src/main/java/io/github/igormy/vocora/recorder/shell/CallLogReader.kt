package io.github.igormy.vocora.recorder.shell

import android.util.Log
import io.github.igormy.vocora.recorder.logic.CallLogEntry
import io.github.igormy.vocora.recorder.logic.CallLogRow
import java.util.concurrent.TimeUnit

private const val TAG = "VocoraRecorder"
private const val COMMAND_TIMEOUT_SECONDS = 5L

/**
 * Reads the newest call log entry by running the `content` command.
 *
 * A ContentResolver does not work from the recorder process: it has the shell uid but its context
 * carries another package, and the provider rejects the mismatch with "Given calling package android
 * does not match caller's uid 2000". The `content` command runs as shell all the way through.
 */
object CallLogReader {

    fun latest(): CallLogEntry? = try {
        val process = ProcessBuilder(
            "content", "query",
            "--uri", "content://call_log/calls",
            "--projection", "number:type:date",
            "--sort", "date DESC",
        ).redirectErrorStream(true).start()

        val firstRow = process.inputStream.bufferedReader().use { it.readLine() }
        process.destroy()
        process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        firstRow?.let(CallLogRow::parse)
    } catch (e: Exception) {
        Log.i(TAG, "could not read the call log: ${e.javaClass.simpleName}: ${e.message}")
        null
    }
}
