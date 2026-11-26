package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clinical_notes")
data class ClinicalNoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val content: String,
  val category: String = "Book / Article",
  val sourceType: String = "INTERNAL_NOTE",
  val languageTag: String = "auto",
  val pageCount: Int = 1,
  val isOcrExtracted: Boolean = false,
  val lastSentenceIndex: Int = 0,
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

@Entity(tableName = "translation_history")
data class TranslationHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sourceText: String,
  val translatedText: String,
  val sourceLanguage: String,
  val targetLanguage: String,
  val tone: String,
  val contextualNote: String,
  val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "saved_phrases")
data class SavedPhraseEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val phrase: String,
  val translation: String,
  val languageTag: String,
  val pronunciationGuide: String = "",
  val notes: String = "",
  val masteryLevel: Int = 1,
  val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "pronunciation_attempts")
data class PronunciationAttemptEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val targetSentence: String,
  val recognizedTranscript: String,
  val targetLanguage: String,
  val overallScore: Int,
  val verifiedAccuracyPct: Int,
  val measuredWpm: Int,
  val audioAnalyzed: Boolean,
  val coachingSummary: String,
  val recordingFilePath: String? = null,
  val timestamp: Long = System.currentTimeMillis(),
)
