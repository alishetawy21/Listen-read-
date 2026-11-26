package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clinical_notes")
data class ClinicalNoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val content: String,
  val category: String = "Clinical Note",
  val sourceType: String = "INTERNAL_NOTE",
  val languageTag: String = "auto",
  val pageCount: Int = 1,
  val isOcrExtracted: Boolean = false,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "pronunciation_rules")
data class PronunciationRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val term: String,
  val spokenReplacement: String,
  val languageTag: String = "all",
  val isEnabled: Boolean = true,
)

@Entity(tableName = "reading_bookmarks")
data class ReadingBookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val documentId: Long?,
  val documentTitle: String,
  val sentenceIndex: Int,
  val paragraphIndex: Int,
  val sentencePreview: String,
  val fullTextSnapshot: String,
  val languageTag: String,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val documentId: Long?,
  val title: String,
  val sourceLabel: String,
  val previewText: String,
  val fullText: String,
  val languageTag: String,
  val sentenceCount: Int,
  val lastSentenceIndex: Int,
  val timestamp: Long = System.currentTimeMillis(),
)
