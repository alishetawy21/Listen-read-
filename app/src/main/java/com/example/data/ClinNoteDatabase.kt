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
    TranslationHistoryEntity::class,
    SavedPhraseEntity::class,
    PronunciationAttemptEntity::class,
  ],
  version = 2,
  exportSchema = false,
)
abstract class ClinNoteDatabase : RoomDatabase() {
  abstract fun clinicalNoteDao(): ClinicalNoteDao

  abstract fun pronunciationRuleDao(): PronunciationRuleDao

  abstract fun readingBookmarkDao(): ReadingBookmarkDao

  abstract fun readingHistoryDao(): ReadingHistoryDao

  abstract fun translationHistoryDao(): TranslationHistoryDao

  abstract fun savedPhraseDao(): SavedPhraseDao

  abstract fun pronunciationAttemptDao(): PronunciationAttemptDao

  companion object {
    @Volatile private var INSTANCE: ClinNoteDatabase? = null

    fun getInstance(context: Context): ClinNoteDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                ClinNoteDatabase::class.java,
                "linguafloat_database.db",
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
