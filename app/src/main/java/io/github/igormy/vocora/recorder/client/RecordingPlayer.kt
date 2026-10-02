package io.github.igormy.vocora.recorder.client

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

private const val TAG = "VocoraRecorder"

/** Plays one recording at a time, and says which one is playing so the list can show it. */
class RecordingPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var playingUri: Uri? = null

    fun isPlaying(uri: Uri): Boolean = playingUri == uri

    /**
     * How far along the current recording is, between 0 and 1.
     *
     * MediaPlayer reports no progress of its own, so this is read whenever the screen asks. It can
     * throw if the player was released in between, which is treated as nothing playing.
     */
    val progress: Float
        get() = runCatching {
            val current = player ?: return 0f
            val duration = current.duration
            if (duration <= 0) 0f else current.currentPosition.toFloat() / duration
        }.getOrDefault(0f).coerceIn(0f, 1f)

    /** Plays [uri], or stops it if it is the one already playing. [onFinished] runs on completion. */
    fun toggle(uri: Uri, onFinished: () -> Unit) {
        if (playingUri == uri) {
            stop()
            return
        }
        stop()
        try {
            player = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnCompletionListener {
                    stop()
                    onFinished()
                }
                prepare()
                start()
            }
            playingUri = uri
        } catch (e: Exception) {
            Log.w(TAG, "could not play $uri: ${e.javaClass.simpleName}: ${e.message}")
            stop()
        }
    }

    fun stop() {
        player?.runCatching { if (isPlaying) stop() }
        player?.release()
        player = null
        playingUri = null
    }
}
