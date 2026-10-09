package io.github.igormy.vocora.recorder.server

import android.content.Context
import io.github.igormy.vocora.recorder.store.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** What came of trying to send a recording. */
enum class UploadOutcome {
    /** The server has it, whether it had it already or not. */
    DONE,

    /** The server said no in a way that saying it again would not change. */
    REFUSED,

    /** Nobody answered, or the answer was "not now". Worth trying later. */
    LATER,
}

/**
 * The self hosted server, as far as the phone is concerned.
 *
 * Reaching it from outside the house is the owner's business, not the app's: a server that does not
 * answer is a server that does not answer, whatever the reason, and uploads retry like any other.
 */
object VocoraServer {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Short on purpose: this runs behind a button, and a server that is not there should
            // say so rather than keep the user waiting.
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** The same client with room to send megabytes, which the liveness check has no use for. */
    private val uploads: OkHttpClient by lazy {
        client.newBuilder()
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .build()
    }

    /** Whether the server answers. The liveness endpoint is public, so the token is not needed. */
    suspend fun isAlive(context: Context): Boolean = withContext(Dispatchers.IO) {
        val url = ServerSettings.url(context) ?: return@withContext false
        runCatching {
            client.newCall(Request.Builder().url("$url/server/live").build())
                .execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Sends a recording, both sides and the mix, and says whether it is worth trying again. */
    suspend fun upload(
        context: Context,
        recording: RecordingEntity,
    ): UploadOutcome = withContext(Dispatchers.IO) {
        val url = ServerSettings.url(context) ?: return@withContext UploadOutcome.LATER
        val token = ServerSettings.token(context) ?: return@withContext UploadOutcome.LATER
        // Files that are no longer there will never upload, and the row is about to go anyway.
        val body = RecordingUpload.bodyOf(context, recording) ?: return@withContext UploadOutcome.REFUSED

        val request = Request.Builder()
            .url("$url/recording")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        runCatching {
            uploads.newCall(request).execute().use { response ->
                when {
                    // 201 created, 200 it was already there with the same content. Both are done.
                    response.isSuccessful -> UploadOutcome.DONE
                    // Ten uploads a minute is all the server takes, and it may simply be down.
                    response.code == 429 || response.code >= 500 -> UploadOutcome.LATER
                    else -> UploadOutcome.REFUSED
                }
            }
        }.getOrDefault(UploadOutcome.LATER)
    }
}
