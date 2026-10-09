package io.github.igormy.vocora.recorder.server

import android.content.Context
import io.github.igormy.vocora.recorder.logic.Transcript
import io.github.igormy.vocora.recorder.logic.TranscriptLine
import io.github.igormy.vocora.recorder.store.VocoraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A stretch of a call, as the server split it. */
@Serializable
data class ServerSegment(val speaker: String, val text: String)

@Serializable
private data class ServerDetail(
    val transcription: String? = null,
    val segments: List<ServerSegment>? = null,
)

/** The side of the call this phone is on: what it sends is what its owner said. */
private const val MINE = "uplink"

/**
 * Fetches a transcription the first time a recording is opened, and keeps it.
 *
 * Downloading it once is the point: the recording does not change after it is transcribed, so the
 * second time a call is opened there is nothing to ask anybody.
 */
object Transcripts {

    private val json = Json { ignoreUnknownKeys = true }

    /** Null while the server has nothing to say about it yet, which is not an error. */
    suspend fun of(context: Context, folder: String): Transcript? = withContext(Dispatchers.IO) {
        val recordings = VocoraDatabase.of(context).recordings()
        val recording = recordings.byFolder(folder) ?: return@withContext null

        val kept = transcriptOf(recording.transcription, recording.segments)
        if (kept != null) return@withContext kept

        val serverId = recording.serverId ?: return@withContext null
        val body = VocoraServer.get(context, "/recording/$serverId") ?: return@withContext null
        val detail = runCatching { json.decodeFromString<ServerDetail>(body) }.getOrNull()
            ?: return@withContext null

        val segments = detail.segments?.let { json.encodeToString(it) }
        val transcript = transcriptOf(detail.transcription, segments) ?: return@withContext null
        recordings.setTranscription(folder, detail.transcription, segments)
        transcript
    }

    private fun transcriptOf(text: String?, segments: String?): Transcript? {
        val lines = segments
            ?.let { runCatching { json.decodeFromString<List<ServerSegment>>(it) }.getOrNull() }
            ?.map { TranscriptLine(fromMe = it.speaker == MINE, text = it.text) }
            .orEmpty()

        val transcript = Transcript(lines = lines, text = text)
        return transcript.takeUnless { it.isEmpty }
    }
}
