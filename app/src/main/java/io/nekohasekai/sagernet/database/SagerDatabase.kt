package io.nekohasekai.sagernet.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.matrix.roomigrant.GenerateRoomMigrations
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.gson.GsonConverters
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Database(
    entities = [ProxyGroup::class, ProxyEntity::class, RuleEntity::class],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6)
    ]
)
@TypeConverters(value = [KryoConverters::class, GsonConverters::class])
@GenerateRoomMigrations
abstract class SagerDatabase : RoomDatabase() {

    companion object {
        private val ALL_MIGRATIONS = arrayOf(
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val cursor = db.query("PRAGMA table_info(rules)")
                    var hasConfig = false
                    var hasRuleset = false
                    while (cursor.moveToNext()) {
                        val columnName = cursor.getString(cursor.getColumnIndex("name"))
                        when (columnName) {
                            "config" -> hasConfig = true
                            "ruleset" -> hasRuleset = true
                        }
                    }
                    cursor.close()

                    if (!hasConfig) {
                        db.execSQL("ALTER TABLE `rules` ADD COLUMN `config` TEXT NOT NULL DEFAULT ''")
                    }
                    if (!hasRuleset) {
                        db.execSQL("ALTER TABLE `rules` ADD COLUMN `ruleset` TEXT NOT NULL DEFAULT ''")
                    }
                }
            }
        )

        @OptIn(DelicateCoroutinesApi::class)
        @Suppress("EXPERIMENTAL_API_USAGE")
        val instance by lazy {
            SagerNet.application.getDatabasePath(Key.DB_PROFILE).parentFile?.mkdirs()
            Room.databaseBuilder(SagerNet.application, SagerDatabase::class.java, Key.DB_PROFILE)
                .addMigrations(*ALL_MIGRATIONS)
                .setJournalMode(JournalMode.TRUNCATE)
                .allowMainThreadQueries()
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration()
                .setQueryExecutor { GlobalScope.launch { it.run() } }
                .build()
        }

        val groupDao get() = instance.groupDao()
        val proxyDao get() = instance.proxyDao()
        val rulesDao get() = instance.rulesDao()

    }

    abstract fun groupDao(): ProxyGroup.Dao
    abstract fun proxyDao(): ProxyEntity.Dao
    abstract fun rulesDao(): RuleEntity.Dao

}
