package io.github.igormy.vocora.recorder.client

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

private const val TAG = "VocoraRecorder"

/** What the speed button cycles through. */
val PLAYBACK_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Plays one recording at a time.
 *
 * MediaPlayer reports nothing of its own, so position and state are read whenever the screen asks.
 * Every read can throw if the player was released in between, which is treated as nothing playing.
 */
class RecordingPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    var current: Uri? = null
        private set

    var speed: Float = 1f
        private set

    val isPlaying: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    val positionMillis: Int
        get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    val durationMillis: Int
        get() = runCatching { player?.duration?.coerceAtLeast(0) ?: 0 }.getOrDefault(0)

    /** Starts [uri] from the beginning, replacing whatever was playing. */
    fun play(uri: Uri, onFinished: () -> Unit) {
        stop()
        try {
            player = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnCompletionListener { onFinished() }
                prepare()
                playbackParams = playbackParams.setSpeed(speed)
                start()
            }
            current = uri
        } catch (e: Exception) {
            Log.w(TAG, "could not play $uri: ${e.javaClass.simpleName}: ${e.message}")
            stop()
        }
    }

    fun togglePlayPause() {
        val player = player ?: return
        runCatching { if (player.isPlaying) player.pause() else player.start() }
    }

    fun seekTo(millis: Int) {
        runCatching { player?.seekTo(millis.coerceAtLeast(0)) }
    }

    /** Applied to whatever plays next too, so the choice survives changing recording. */
    fun setSpeed(value: Float) {
        speed = value
        val player = player ?: return
        runCatching {
            val wasPlaying = player.isPlaying
            // Setting the params resumes playback on some versions, so put it back if it was paused.
            player.playbackParams = player.playbackParams.setSpeed(value)
            if (!wasPlaying) player.pause()
        }
    }

    fun stop() {
        player?.runCatching { if (isPlaying) stop() }
        player?.release()
        player = null
        current = null
    }
}
