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
     * What the queue has to send.
     *
     * Anything left mid flight counts as asked for: a run that was cut short says nothing about
     * whether the server took it, and sending it again is answered with the same recording.
     *
     * [includeWaiting] brings in the ones nobody asked for, which is what the automatic sync is.
     * Without it, pressing send on one recording would send every other one that is not up there.
     */
    @Query(
        "SELECT * FROM recordings WHERE uploadState IN ('QUEUED', 'UPLOADING') " +
            "OR (:includeWaiting AND uploadState = 'PENDING') ORDER BY recordedAt",
    )
    suspend fun toUpload(includeWaiting: Boolean): List<RecordingEntity>

    @Query("UPDATE recordings SET uploadState = :state WHERE folder = :folder")
    suspend fun setUploadState(folder: String, state: UploadState)

    /** The server has it, so whatever the phone believed about sending it is settled. */
    @Query(
        "UPDATE recordings SET serverId = :serverId, serverStatus = :status, " +
            "uploadState = 'UPLOADED' WHERE folder = :folder",
    )
    suspend fun setServerState(folder: String, serverId: String, status: String)

    @Query("UPDATE recordings SET transcription = :transcription, segments = :segments WHERE folder = :folder")
    suspend fun setTranscription(folder: String, transcription: String?, segments: String?)

    /**
     * Puts a recording back in the queue because the server turns out not to have it.
     *
     * What the server once said about it goes with it: an id that points at nothing is worse than
     * no id, and a status describing a recording that is gone is not a status.
     */
    @Query(
        "UPDATE recordings SET serverId = NULL, serverStatus = NULL, transcription = NULL, " +
            "segments = NULL, uploadState = 'PENDING' WHERE folder = :folder",
    )
    suspend fun setMissingOnServer(folder: String)

    /** Asks for one recording to be sent, keeping whatever is already known about it. */
    @Query("UPDATE recordings SET uploadState = 'QUEUED' WHERE folder = :folder")
    suspend fun sendAgain(folder: String)

    /** Drops everything a server said, which is what a different server makes of all of it. */
    @Query(
        "UPDATE recordings SET serverId = NULL, serverStatus = NULL, transcription = NULL, " +
            "segments = NULL, uploadState = 'PENDING'",
    )
    suspend fun forgetServer()

    @Query("SELECT * FROM recordings WHERE folder = :folder")
    suspend fun byFolder(folder: String): RecordingEntity?

    @Upsert
    suspend fun put(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE folder IN (:folders)")
    suspend fun forget(folders: List<String>)
}
