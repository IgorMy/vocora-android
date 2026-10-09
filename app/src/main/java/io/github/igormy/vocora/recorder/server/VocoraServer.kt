package io.github.igormy.vocora.recorder.server

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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

    /** Whether the server answers. The liveness endpoint is public, so the token is not needed. */
    suspend fun isAlive(context: Context): Boolean = withContext(Dispatchers.IO) {
        val url = ServerSettings.url(context) ?: return@withContext false
        runCatching {
            client.newCall(Request.Builder().url("$url/server/live").build())
                .execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
