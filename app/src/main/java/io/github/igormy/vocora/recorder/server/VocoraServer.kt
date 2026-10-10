package io.github.igormy.vocora.recorder.server

import android.content.Context
import android.util.Log
import io.github.igormy.vocora.recorder.store.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "VocoraUpload"

/** `{"detail": "..."}` is how the server words a refusal, whatever the refusal is about. */
private val DETAIL = Regex("\"detail\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

/** What came of trying to send a recording. */
enum class UploadOutcome {
    /** The server has it, whether it had it already or not. */
    DONE,

    /** The server said no in a way that saying it again would not change. */
    REFUSED,

    /** Too fast. The server is fine, it just wants the next one in a moment. */
    PACED,

    /** Nobody answered, or the server is in no state to take it. Worth trying later. */
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

    /**
     * Whether the server answers.
     *
     * Liveness sits outside the versioned API on purpose, so that its path does not move when the
     * API does. It is public too, so the token is not needed to ask.
     */
    suspend fun isAlive(context: Context): Boolean = withContext(Dispatchers.IO) {
        val url = ServerSettings.url(context) ?: return@withContext false
        runCatching {
            client.newCall(Request.Builder().url("$url/health/server/live").build())
                .execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** The recordings live under the major version the server runs, and that is a setting. */
    private fun apiUrl(context: Context, path: String): String? {
        val url = ServerSettings.url(context) ?: return null
        return "$url/${ServerSettings.apiVersion(context)}$path"
    }

    /**
     * An authenticated GET, answered with the body or with null if anything at all went wrong.
     *
     * Query values go through OkHttp rather than through string building: a date carries a `+` for
     * its offset, and a `+` written into a query means a space by the time the server reads it.
     */
    fun get(context: Context, path: String, params: Map<String, String> = emptyMap()): String? {
        val url = apiUrl(context, path) ?: return null
        val token = ServerSettings.token(context) ?: return null
        val built = url.toHttpUrlOrNull()?.newBuilder()?.apply {
            params.forEach { (name, value) -> addQueryParameter(name, value) }
        }?.build() ?: return null

        return runCatching {
            client.newCall(
                Request.Builder().url(built).header("Authorization", "Bearer $token").build(),
            ).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    /** Sends a recording, both sides and the mix, and says whether it is worth trying again. */
    suspend fun upload(
        context: Context,
        recording: RecordingEntity,
    ): UploadOutcome = withContext(Dispatchers.IO) {
        val url = apiUrl(context, "/recording") ?: return@withContext UploadOutcome.LATER
        val token = ServerSettings.token(context) ?: return@withContext UploadOutcome.LATER
        // Files that are no longer there will never upload, and the row is about to go anyway.
        val body = RecordingUpload.bodyOf(context, recording) ?: return@withContext UploadOutcome.REFUSED

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        runCatching {
            uploads.newCall(request).execute().use { response ->
                when {
                    // 201 created, 200 it was already there with the same content. Both are done.
                    response.isSuccessful -> UploadOutcome.DONE
                    // Ten a minute is all the server takes; being told so is not a failure.
                    response.code == 429 -> UploadOutcome.PACED
                    response.code >= 500 -> UploadOutcome.LATER
                    else -> UploadOutcome.REFUSED
                }.also { outcome ->
                    // Said out loud: an upload that quietly goes back to waiting is an upload
                    // nobody can explain, and the body is where the server says what it disliked.
                    if (outcome == UploadOutcome.DONE) return@also
                    val said = complaintIn(response.body?.string())
                    Log.w(TAG, "${recording.folder}: $outcome, HTTP ${response.code} $said")
                    // Pacing is the server working as intended, and not worth interrupting anybody.
                    if (outcome != UploadOutcome.PACED) {
                        UploadReports.report("HTTP ${response.code} · $said")
                    }
                }
            }
        }.getOrElse {
            Log.w(TAG, "${recording.folder}: ${it.javaClass.simpleName}: ${it.message}")
            UploadReports.report("${it.javaClass.simpleName}: ${it.message}")
            UploadOutcome.LATER
        }
    }

    /**
     * What the server actually complained about.
     *
     * It answers errors as `{"detail": "..."}`, and the detail is the whole of the news; the rest is
     * JSON punctuation that would only crowd out the part worth reading.
     */
    private fun complaintIn(body: String?): String {
        val said = body?.takeIf { it.isNotBlank() } ?: return "no answer"
        val detail = DETAIL.find(said)?.groupValues?.get(1)?.replace("\\\"", "\"")
        return (detail ?: said).take(200)
    }
}
