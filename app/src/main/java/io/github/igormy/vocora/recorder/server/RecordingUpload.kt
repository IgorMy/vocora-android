package io.github.igormy.vocora.recorder.server

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.igormy.vocora.recorder.client.RecordingsLibrary
import io.github.igormy.vocora.recorder.store.RecordingEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIXED = "mixed.m4a"
private const val UPLINK = "uplink.m4a"
private const val DOWNLINK = "downlink.m4a"

/** What the server calls a call whose number nobody could put a name or a number to. */
private const val UNKNOWN_CONTACT = "unknown"

/** The only direction the server accepts when the call log did not say which way the call went. */
private const val UNKNOWN_DIRECTION = "call"

private val AUDIO = "audio/mp4".toMediaType()

/**
 * Turns a recording into the request the server expects.
 *
 * A recording is identified over there by date, contact and direction, so the same call sent twice
 * is the same recording rather than a second one. That is what lets the queue be as dumb as it is:
 * a retry costs nothing and a half finished run can simply start again.
 */
object RecordingUpload {

    /** Dates carry their offset, which is the one in force when the call happened, not today's. */
    private const val DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX"

    /** Null when the folder no longer holds the three files, which is nothing to send. */
    fun bodyOf(context: Context, recording: RecordingEntity): MultipartBody? {
        val files = RecordingsLibrary.filesIn(context, Uri.parse(recording.folderUri))
        val mixed = files[MIXED] ?: return null
        val uplink = files[UPLINK] ?: return null
        val downlink = files[DOWNLINK] ?: return null

        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("mixed", MIXED, audioBody(context, mixed))
            .addFormDataPart("uplink", UPLINK, audioBody(context, uplink))
            .addFormDataPart("downlink", DOWNLINK, audioBody(context, downlink))
            .addFormDataPart("contact", recording.number ?: UNKNOWN_CONTACT)
            .addFormDataPart("date", isoDate(recording.recordedAt))
            .addFormDataPart("direction", recording.direction ?: UNKNOWN_DIRECTION)
            .build()
    }

    private fun isoDate(millis: Long): String =
        SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date(millis))

    /**
     * The file as it is read, rather than as it is held in memory: a ten minute call is around 5 MB
     * per side, and there is no reason for any of it to go through the heap twice.
     */
    private fun audioBody(context: Context, uri: Uri): RequestBody = object : RequestBody() {
        override fun contentType() = AUDIO

        override fun contentLength(): Long = sizeOf(context, uri)

        override fun writeTo(sink: BufferedSink) {
            context.contentResolver.openInputStream(uri)?.use { sink.writeAll(it.source()) }
        }
    }

    /** -1 when the size is unknown, which is what OkHttp reads as "send it in chunks". */
    private fun sizeOf(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L }
            ?: -1L
    }.getOrDefault(-1L)
}
