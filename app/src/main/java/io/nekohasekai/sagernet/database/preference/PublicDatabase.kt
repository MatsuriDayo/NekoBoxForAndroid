package io.nekohasekai.sagernet.database.preference

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.matrix.roomigrant.GenerateRoomMigrations
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Database(entities = [KeyValuePair::class], version = 1)
@GenerateRoomMigrations
abstract class PublicDatabase : RoomDatabase() {
    companion object {
        val instance by lazy {
            SagerNet.application.getDatabasePath(Key.DB_PROFILE).parentFile?.mkdirs()
            Room.databaseBuilder(SagerNet.application, PublicDatabase::class.java, Key.DB_PUBLIC)
                .setJournalMode(JournalMode.TRUNCATE)
                .allowMainThreadQueries()
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration()
                .setQueryExecutor { GlobalScope.launch { it.run() } }
                .build()
        }

        val kvPairDao get() = instance.keyValuePairDao()
    }

    abstract fun keyValuePairDao(): KeyValuePair.Dao

}
