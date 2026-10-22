package com.lumalink.g100d.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransferEntity::class, MediaOverlayEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(TransferConverters::class)
abstract class LumaLinkDatabase : RoomDatabase() {

    abstract fun transferDao(): TransferDao

    abstract fun mediaOverlayDao(): MediaOverlayDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `luts`")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `luts` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`gridSize` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_overlays` (" +
                        "`uri` TEXT NOT NULL, " +
                        "`favorite` INTEGER NOT NULL, " +
                        "`tags` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`uri`))",
                )
            }
        }

        fun create(context: Context): LumaLinkDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LumaLinkDatabase::class.java,
                "lumalink.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }
}
