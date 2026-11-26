package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    ClinicalNoteEntity::class,
    PronunciationRuleEntity::class,
    ReadingBookmarkEntity::class,
    ReadingHistoryEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class ClinNoteDatabase : RoomDatabase() {
  abstract fun clinicalNoteDao(): ClinicalNoteDao

  abstract fun pronunciationRuleDao(): PronunciationRuleDao

  abstract fun readingBookmarkDao(): ReadingBookmarkDao

  abstract fun readingHistoryDao(): ReadingHistoryDao

  companion object {
    @Volatile private var INSTANCE: ClinNoteDatabase? = null

    fun getInstance(context: Context): ClinNoteDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                ClinNoteDatabase::class.java,
                "clinnote_database.db",
              )
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
