package io.github.igormy.vocora.recorder.server

import android.content.Context
import io.github.igormy.vocora.recorder.store.RecordingEntity
import io.github.igormy.vocora.recorder.store.VocoraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** What the server returns for each recording in a page. */
@Serializable
data class ServerRecording(
    val id: String,
    val date: String,
    val contact: String,
    val direction: String,
    val status: String,
    val updated_at: String,
)

@Serializable
data class ServerPage(
    val items: List<ServerRecording>,
    val next_offset: Int? = null,
)

/**
 * Brings back what the server has made of the recordings it was sent.
 *
 * Uploading answers with nothing at all, so the only way to learn a recording's id and how its
 * transcription is going is to ask. Asking is cheap because the server can be asked for just what
 * changed since last time.
 *
 * Matching is by what identifies a call on both sides: the instant it happened, who it was with,
 * and which way it went. Nothing is matched by name, which a rename would break.
 */
object ServerSync {

    private val json = Json { ignoreUnknownKeys = true }

    /** One page is plenty for a phone, and the server takes 200 at most anyway. */
    private const val PAGE = 200

    /**
     * Throws away what a different server said, and marks everything as needing to be sent again.
     *
     * Ids, statuses and transcriptions all belong to the server that gave them: pointed somewhere
     * else, every one of them is about a recording that machine has never seen.
     */
    suspend fun forgetOtherServer(context: Context) = withContext(Dispatchers.IO) {
        // The version is part of it: the same host on another API is another server's answers.
        val url = ServerSettings.url(context)?.let { "$it/${ServerSettings.apiVersion(context)}" }
            ?: return@withContext
        if (url == ServerSettings.syncedUrl(context)) return@withContext

        VocoraDatabase.of(context).recordings().forgetServer()
        ServerSettings.setSyncedAt(context, null)
        ServerSettings.setSyncedUrl(context, url)
    }

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        if (!ServerSettings.isConfigured(context)) return@withContext

        val recordings = VocoraDatabase.of(context).recordings()
        val known = recordings.all().associateBy { keyOf(it) }
        if (known.isEmpty()) return@withContext

        val since = ServerSettings.syncedAt(context)
        var offset = 0
        var newestAt = 0L
        var newest: String? = null
        while (true) {
            val page = fetch(context, since, offset) ?: return@withContext
            for (item in page.items) {
                known[keyOf(item)]?.let { recording ->
                    recordings.setServerState(recording.folder, item.id, item.status)
                }
                // The server orders by last update, but the newest is picked rather than assumed.
                val updatedAt = epochSecondOf(item.updated_at)
                if (updatedAt > newestAt) {
                    newestAt = updatedAt
                    newest = item.updated_at
                }
            }
            offset = page.next_offset ?: break
        }
        // Only moved forward on an answer, so a failed sync asks for the same window again.
        newest?.let { ServerSettings.setSyncedAt(context, it) }
    }

    private fun fetch(context: Context, since: String?, offset: Int): ServerPage? {
        val params = buildMap {
            put("limit", PAGE.toString())
            put("offset", offset.toString())
            since?.let { put("updated_since", it) }
        }
        val body = VocoraServer.get(context, "/recording", params) ?: return null
        return runCatching { json.decodeFromString<ServerPage>(body) }.getOrNull()
    }

    private fun keyOf(recording: RecordingEntity) = Key(
        second = recording.recordedAt / 1000,
        contact = recording.number ?: UNKNOWN_CONTACT,
        direction = recording.direction ?: UNKNOWN_DIRECTION,
    )

    private fun keyOf(item: ServerRecording) = Key(
        second = epochSecondOf(item.date),
        contact = item.contact,
        direction = item.direction,
    )

    /** The instant, whatever offset it is written in: the same call, told the same way twice. */
    private fun epochSecondOf(date: String): Long = runCatching {
        OffsetDateTime.parse(date).toEpochSecond()
    }.recoverCatching {
        // A server that answers without an offset means the time it keeps, which is this phone's.
        LocalDateTime.parse(date).atZone(ZoneId.systemDefault()).toEpochSecond()
    }.getOrDefault(0L)

    private data class Key(val second: Long, val contact: String, val direction: String)
}
