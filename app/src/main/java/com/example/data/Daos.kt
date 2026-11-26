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
