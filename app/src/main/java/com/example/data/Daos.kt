package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClinicalNoteDao {
  @Query("SELECT * FROM clinical_notes ORDER BY updatedAt DESC")
  fun observeAllNotes(): Flow<List<ClinicalNoteEntity>>

  @Query("SELECT * FROM clinical_notes WHERE id = :id")
  suspend fun getNoteById(id: Long): ClinicalNoteEntity?

  @Query("SELECT COUNT(*) FROM clinical_notes")
  suspend fun countNotes(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNote(note: ClinicalNoteEntity): Long

  @Update
  suspend fun updateNote(note: ClinicalNoteEntity)

  @Query("UPDATE clinical_notes SET lastSentenceIndex = :sentenceIndex, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateReadingProgress(id: Long, sentenceIndex: Int, updatedAt: Long)

  @Query("DELETE FROM clinical_notes WHERE id = :id")
  suspend fun deleteNoteById(id: Long)
}

@Dao
interface PronunciationRuleDao {
  @Query("SELECT * FROM pronunciation_rules ORDER BY term ASC")
  fun observeAllRules(): Flow<List<PronunciationRuleEntity>>

  @Query("SELECT * FROM pronunciation_rules WHERE isEnabled = 1")
  suspend fun getEnabledRules(): List<PronunciationRuleEntity>

  @Query("SELECT COUNT(*) FROM pronunciation_rules")
  suspend fun countRules(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRule(rule: PronunciationRuleEntity): Long

  @Query("DELETE FROM pronunciation_rules WHERE id = :id")
  suspend fun deleteRuleById(id: Long)
}

@Dao
interface ReadingBookmarkDao {
  @Query("SELECT * FROM reading_bookmarks ORDER BY createdAt DESC")
  fun observeAllBookmarks(): Flow<List<ReadingBookmarkEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBookmark(bookmark: ReadingBookmarkEntity): Long

  @Query("DELETE FROM reading_bookmarks WHERE id = :id")
  suspend fun deleteBookmarkById(id: Long)
}

@Dao
interface ReadingHistoryDao {
  @Query("SELECT * FROM reading_history ORDER BY timestamp DESC LIMIT 50")
  fun observeHistory(): Flow<List<ReadingHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHistory(entry: ReadingHistoryEntity): Long

  @Query("DELETE FROM reading_history")
  suspend fun clearAllHistory()

  @Query("DELETE FROM reading_history WHERE id = :id")
  suspend fun deleteHistoryById(id: Long)
}

@Dao
interface TranslationHistoryDao {
  @Query("SELECT * FROM translation_history ORDER BY timestamp DESC LIMIT 100")
  fun observeTranslations(): Flow<List<TranslationHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTranslation(item: TranslationHistoryEntity): Long

  @Query("DELETE FROM translation_history WHERE id = :id")
  suspend fun deleteTranslationById(id: Long)

  @Query("DELETE FROM translation_history")
  suspend fun clearAllTranslations()
}

@Dao
interface SavedPhraseDao {
  @Query("SELECT * FROM saved_phrases ORDER BY createdAt DESC")
  fun observeSavedPhrases(): Flow<List<SavedPhraseEntity>>

  @Query("SELECT COUNT(*) FROM saved_phrases")
  suspend fun countPhrases(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPhrase(phrase: SavedPhraseEntity): Long

  @Query("UPDATE saved_phrases SET masteryLevel = :level WHERE id = :id")
  suspend fun updateMastery(id: Long, level: Int)

  @Query("DELETE FROM saved_phrases WHERE id = :id")
  suspend fun deletePhraseById(id: Long)
}

@Dao
interface PronunciationAttemptDao {
  @Query("SELECT * FROM pronunciation_attempts ORDER BY timestamp DESC LIMIT 100")
  fun observeAttempts(): Flow<List<PronunciationAttemptEntity>>

  @Query("SELECT * FROM pronunciation_attempts WHERE recordingFilePath IS NOT NULL")
  suspend fun getAttemptsWithRecordings(): List<PronunciationAttemptEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAttempt(attempt: PronunciationAttemptEntity): Long

  @Query("DELETE FROM pronunciation_attempts WHERE id = :id")
  suspend fun deleteAttemptById(id: Long)

  @Query("DELETE FROM pronunciation_attempts")
  suspend fun clearAllAttempts()
}
