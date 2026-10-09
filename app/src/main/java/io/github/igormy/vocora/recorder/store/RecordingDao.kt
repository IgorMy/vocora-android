package io.github.igormy.vocora.recorder.store

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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

    @Upsert
    suspend fun put(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE folder IN (:folders)")
    suspend fun forget(folders: List<String>)
}
