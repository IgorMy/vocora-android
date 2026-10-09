package io.github.igormy.vocora.recorder.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.igormy.vocora.recorder.logic.UploadState

/**
 * A recorded call as the index knows it.
 *
 * The folder is the key because it is what identifies a call on disk and on the server alike. The
 * row is a copy of what the disk already says, kept so the list can be drawn without opening every
 * file to measure it: the disk remains the truth, and [RecordingIndex] makes the two agree.
 */
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val folder: String,
    val recordedAt: Long,
    val number: String?,
    val direction: String?,
    val durationMillis: Int,
    val folderUri: String,
    val mixedUri: String,
    val uploadState: UploadState = UploadState.PENDING,
    /** What the server calls it, learnt by asking rather than by uploading: the upload says nothing. */
    val serverId: String? = null,
    /** What the server last said it was doing with it: pending, transcribing, done, failed. */
    val serverStatus: String? = null,
    /** Kept once downloaded, so opening a recording again does not ask for it twice. */
    val transcription: String? = null,
    /** The transcription split by who was speaking, as the server sent it. */
    val segments: String? = null,
)
