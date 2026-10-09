package io.github.igormy.vocora.recorder.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

private const val DATABASE_NAME = "vocora.db"

/**
 * The app's own database, holding what would otherwise have to be read off the disk on every start.
 *
 * It is deliberately throwaway: everything in it can be rebuilt from the recordings folder, so a
 * schema change drops the tables instead of migrating them and reconciliation fills them in again.
 * The recorder cannot reach this file, since it runs as the shell user in another process, so the
 * app is the only writer.
 */
// No schema is exported: there are no migrations to check it against, by design.
@Database(entities = [RecordingEntity::class], version = 1, exportSchema = false)
abstract class VocoraDatabase : RoomDatabase() {

    abstract fun recordings(): RecordingDao

    companion object {
        @Volatile
        private var instance: VocoraDatabase? = null

        fun of(context: Context): VocoraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VocoraDatabase::class.java,
                    DATABASE_NAME,
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
