package com.acaba.pdfreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PdfDocumentEntity::class, HighlightStrokeEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun strokeDao(): StrokeDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pdf-reader.db",
        ).build()
    }
}
