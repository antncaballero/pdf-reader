package com.acaba.pdfreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PdfDocumentEntity::class, HighlightStrokeEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun strokeDao(): StrokeDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pdf_documents ADD COLUMN lastPageIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pdf_documents ADD COLUMN pageCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pdf-reader.db",
        ).addMigrations(MIGRATION_1_2).build()
    }
}
