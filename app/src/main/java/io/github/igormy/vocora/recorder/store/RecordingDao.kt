package io.github.igormy.vocora.recorder.store

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.igormy.vocora.recorder.logic.UploadState
import kotlinx.coroutines.flow.Flow

/** Reads and writes the recordings table. Newest first, which is the only order the list shows. */
@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY recordedAt DESC")
    fun stream(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY recordedAt DESC")
    suspend fun all(): List<RecordingEntity>

    /** Just the keys, which is all reconciliation needs to tell new folders from known ones. */
    @Query("SELECT folder FROM recordings")
    suspend fun folders(): List<String>

    /**
     * What the queue has left to send.
     *
     * Anything left mid flight counts as waiting: a run that was cut short says nothing about
     * whether the server took it, and sending it again is answered with the same recording.
     */
    @Query(
        "SELECT * FROM recordings WHERE uploadState IN ('PENDING', 'UPLOADING') ORDER BY recordedAt",
    )
    suspend fun toUpload(): List<RecordingEntity>

    @Query("UPDATE recordings SET uploadState = :state WHERE folder = :folder")
    suspend fun setUploadState(folder: String, state: UploadState)

    @Query(
        "UPDATE recordings SET serverId = :serverId, serverStatus = :status WHERE folder = :folder",
    )
    suspend fun setServerState(folder: String, serverId: String, status: String)

    @Query("UPDATE recordings SET transcription = :transcription, segments = :segments WHERE folder = :folder")
    suspend fun setTranscription(folder: String, transcription: String?, segments: String?)

    @Query("SELECT * FROM recordings WHERE folder = :folder")
    suspend fun byFolder(folder: String): RecordingEntity?

    @Upsert
    suspend fun put(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE folder IN (:folders)")
    suspend fun forget(folders: List<String>)
}
