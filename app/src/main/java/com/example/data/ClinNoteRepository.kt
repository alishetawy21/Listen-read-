package com.example.data

import com.example.model.CaptureSource
import java.io.File
import kotlinx.coroutines.flow.Flow

class ClinNoteRepository(private val database: ClinNoteDatabase) {

  private val noteDao = database.clinicalNoteDao()
  private val ruleDao = database.pronunciationRuleDao()
  private val bookmarkDao = database.readingBookmarkDao()
  private val historyDao = database.readingHistoryDao()
  private val translationDao = database.translationHistoryDao()
  private val phraseDao = database.savedPhraseDao()
  private val attemptDao = database.pronunciationAttemptDao()

  val notesFlow: Flow<List<ClinicalNoteEntity>> = noteDao.observeAllNotes()
  val pronunciationRulesFlow: Flow<List<PronunciationRuleEntity>> = ruleDao.observeAllRules()
  val bookmarksFlow: Flow<List<ReadingBookmarkEntity>> = bookmarkDao.observeAllBookmarks()
  val historyFlow: Flow<List<ReadingHistoryEntity>> = historyDao.observeHistory()
  val translationsFlow: Flow<List<TranslationHistoryEntity>> = translationDao.observeTranslations()
  val savedPhrasesFlow: Flow<List<SavedPhraseEntity>> = phraseDao.observeSavedPhrases()
  val pronunciationAttemptsFlow: Flow<List<PronunciationAttemptEntity>> = attemptDao.observeAttempts()

  suspend fun seedInitialDataIfEmpty() {
    if (noteDao.countNotes() == 0) {
      starterDocuments().forEach { noteDao.insertNote(it) }
    }
    if (ruleDao.countRules() == 0) {
      starterPronunciationRules().forEach { ruleDao.insertRule(it) }
    }
    if (phraseDao.countPhrases() == 0) {
      starterSavedPhrases().forEach { phraseDao.insertPhrase(it) }
    }
  }

  suspend fun getNoteById(id: Long): ClinicalNoteEntity? = noteDao.getNoteById(id)

  suspend fun saveNote(
    id: Long? = null,
    title: String,
    content: String,
    category: String = "Book / Article",
    sourceType: CaptureSource = CaptureSource.INTERNAL_NOTE,
    languageTag: String = "auto",
    pageCount: Int = 1,
    isOcrExtracted: Boolean = false,
  ): Long {
    val now = System.currentTimeMillis()
    val entity =
      if (id != null && id > 0) {
        val existing = noteDao.getNoteById(id)
        ClinicalNoteEntity(
          id = id,
          title = title.trim().ifEmpty { "Untitled Document" },
          content = content,
          category = category,
          sourceType = sourceType.name,
          languageTag = languageTag,
          pageCount = pageCount,
          isOcrExtracted = isOcrExtracted,
          lastSentenceIndex = existing?.lastSentenceIndex ?: 0,
          createdAt = existing?.createdAt ?: now,
          updatedAt = now,
        )
      } else {
        ClinicalNoteEntity(
          title = title.trim().ifEmpty { "Untitled Document" },
          content = content,
          category = category,
          sourceType = sourceType.name,
          languageTag = languageTag,
          pageCount = pageCount,
          isOcrExtracted = isOcrExtracted,
          createdAt = now,
          updatedAt = now,
        )
      }
    return noteDao.insertNote(entity)
  }

  suspend fun updateDocumentReadingProgress(documentId: Long, sentenceIndex: Int) {
    noteDao.updateReadingProgress(documentId, sentenceIndex, System.currentTimeMillis())
  }

  suspend fun deleteNote(id: Long) = noteDao.deleteNoteById(id)

  suspend fun getEnabledPronunciationRules(): List<PronunciationRuleEntity> =
    ruleDao.getEnabledRules()

  suspend fun savePronunciationRule(
    id: Long = 0,
    term: String,
    spokenReplacement: String,
    languageTag: String = "all",
    isEnabled: Boolean = true,
  ) {
    if (term.isBlank() || spokenReplacement.isBlank()) return
    ruleDao.insertRule(
      PronunciationRuleEntity(
        id = id,
        term = term.trim(),
        spokenReplacement = spokenReplacement.trim(),
        languageTag = languageTag,
        isEnabled = isEnabled,
      )
    )
  }

  suspend fun togglePronunciationRule(rule: PronunciationRuleEntity) {
    ruleDao.insertRule(rule.copy(isEnabled = !rule.isEnabled))
  }

  suspend fun deletePronunciationRule(id: Long) = ruleDao.deleteRuleById(id)

  suspend fun addBookmark(
    documentId: Long?,
    documentTitle: String,
    sentenceIndex: Int,
    paragraphIndex: Int,
    sentencePreview: String,
    fullTextSnapshot: String,
    languageTag: String,
  ) {
    bookmarkDao.insertBookmark(
      ReadingBookmarkEntity(
        documentId = documentId,
        documentTitle = documentTitle,
        sentenceIndex = sentenceIndex,
        paragraphIndex = paragraphIndex,
        sentencePreview = sentencePreview.take(180),
        fullTextSnapshot = fullTextSnapshot,
        languageTag = languageTag,
      )
    )
  }

  suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteBookmarkById(id)

  suspend fun recordHistory(
    documentId: Long?,
    title: String,
    sourceLabel: String,
    fullText: String,
    languageTag: String,
    sentenceCount: Int,
    lastSentenceIndex: Int,
  ) {
    historyDao.insertHistory(
      ReadingHistoryEntity(
        documentId = documentId,
        title = title,
        sourceLabel = sourceLabel,
        previewText = fullText.lineSequence().firstOrNull { it.isNotBlank() }?.take(140) ?: "",
        fullText = fullText,
        languageTag = languageTag,
        sentenceCount = sentenceCount,
        lastSentenceIndex = lastSentenceIndex,
      )
    )
  }

  suspend fun clearAllHistory() = historyDao.clearAllHistory()

  suspend fun deleteHistoryEntry(id: Long) = historyDao.deleteHistoryById(id)

  // Translation history
  suspend fun recordTranslation(
    sourceText: String,
    translatedText: String,
    sourceLanguage: String,
    targetLanguage: String,
    tone: String,
    contextualNote: String,
  ) {
    translationDao.insertTranslation(
      TranslationHistoryEntity(
        sourceText = sourceText,
        translatedText = translatedText,
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        tone = tone,
        contextualNote = contextualNote,
      )
    )
  }

  suspend fun deleteTranslation(id: Long) = translationDao.deleteTranslationById(id)

  suspend fun clearTranslationHistory() = translationDao.clearAllTranslations()

  // Saved phrases / Vocabulary
  suspend fun savePhrase(
    phrase: String,
    translation: String,
    languageTag: String,
    pronunciationGuide: String = "",
    notes: String = "",
  ) {
    if (phrase.isBlank() || translation.isBlank()) return
    phraseDao.insertPhrase(
      SavedPhraseEntity(
        phrase = phrase.trim(),
        translation = translation.trim(),
        languageTag = languageTag,
        pronunciationGuide = pronunciationGuide.trim(),
        notes = notes.trim(),
      )
    )
  }

  suspend fun updatePhraseMastery(id: Long, level: Int) {
    phraseDao.updateMastery(id, level.coerceIn(0, 3))
  }

  suspend fun deletePhrase(id: Long) = phraseDao.deletePhraseById(id)

  // Pronunciation attempts & recordings
  suspend fun savePronunciationAttempt(
    targetSentence: String,
    recognizedTranscript: String,
    targetLanguage: String,
    overallScore: Int,
    verifiedAccuracyPct: Int,
    measuredWpm: Int,
    audioAnalyzed: Boolean,
    coachingSummary: String,
    recordingFilePath: String?,
  ) {
    attemptDao.insertAttempt(
      PronunciationAttemptEntity(
        targetSentence = targetSentence,
        recognizedTranscript = recognizedTranscript,
        targetLanguage = targetLanguage,
        overallScore = overallScore,
        verifiedAccuracyPct = verifiedAccuracyPct,
        measuredWpm = measuredWpm,
        audioAnalyzed = audioAnalyzed,
        coachingSummary = coachingSummary,
        recordingFilePath = recordingFilePath,
      )
    )
  }

  suspend fun deletePronunciationAttempt(id: Long) = attemptDao.deleteAttemptById(id)

  suspend fun clearAllPronunciationAttemptsAndRecordings() {
    attemptDao.getAttemptsWithRecordings().forEach { attempt ->
      attempt.recordingFilePath?.let { path -> runCatching { File(path).delete() } }
    }
    attemptDao.clearAllAttempts()
  }

  private fun starterDocuments(): List<ClinicalNoteEntity> =
    listOf(
      ClinicalNoteEntity(
        title = "The Art of Bilingual Reading & Clinical Precision",
        category = "English Book",
        languageTag = "en-US",
        content =
          """
          CHAPTER 1: FLUENCY THROUGH CONTEXT
          Reading fluently in a second language means understanding idioms, rhythm, and subtle tone shifts rather than translating word by word. When you listen closely to natural stress patterns, complex medical and academic terms become effortless to pronounce.

          CLINICAL COMMUNICATION EXAMPLE:
          Dr. Patel reviewed the patient's chart carefully. Vitals remained stable at BP 124/78 mmHg and SpO2 98% on room air, while HbA1c improved to 6.8%.

          PRACTICE SENTENCE FOR PRONUNCIATION:
          People probably prefer practicing clear pronunciation patiently every morning before work.
          """
            .trimIndent(),
      ),
      ClinicalNoteEntity(
        title = "رحلةُ الكلمةِ والعِلم — القراءةُ الفصيحةُ والتَّرجمةُ",
        category = "Arabic Book",
        languageTag = "ar",
        content =
          """
          الفَصْلُ الأَوَّلُ: بَلَاغَةُ التَّعْبِيرِ وَوُضُوحُ النُّطْقِ
          تُعَدُّ القِرَاءَةُ الجَهْرِيَّةُ المُنَظَّمَةُ مِنْ أَنْجَعِ الوَسَائِلِ لِإِتْقَانِ مَخَارِجِ الحُرُوفِ العَرَبِيَّةِ وَضَبْطِ الإِيقَاعِ الصَّوْتِيِّ. عِنْدَمَا نَرْبِطُ السِّيَاقَ المَعْرِفِيَّ بِالنُّطْقِ الصَّحِيحِ تَتَرَسَّخُ المُفْرَدَاتُ فِي الذَّاكِرَةِ طَوِيلَةِ الأَمَدِ.

          مِثَالٌ طِبِّيٌّ مُزْدَوَجُ اللُّغَةِ:
          أَظْهَرَتْ نَتَائِجُ الفَحْصِ السَّرِيرِيِّ اسْتِقْرَارَ ضَغْطِ الدَّمِ عِنْدَ 128/82 mmHg وَانْخِفَاضَ مُعَدَّلِ HbA1c بَعْدَ الِالْتِزَامِ بِدَوَاءِ Metformin 500 mg يَوْمِيًّا.
          """
            .trimIndent(),
      ),
      ClinicalNoteEntity(
        title = "Arabic ↔ English Accent Mastery Drills",
        category = "Pronunciation",
        languageTag = "auto",
        content =
          """
          ENGLISH CONTRAST DRILLS (/p/ vs /b/, /v/ vs /f/, /θ/ vs /s/):
          Please bring both heavy paper bags before five o'clock.
          The very vivid voice verified thirty-three thoughtful theories.

          ARABIC ARTICULATION DRILLS (ع / غ / ح / خ / ق / ض):
          عَلِمَ العَالِمُ حَقِيقَةَ القَرَارِ الصَّائِبِ فِي ضَوْءِ الخِبْرَةِ العَمِيقَةِ.
          حَافِظْ عَلَى هُدُوءِ الصَّوْتِ وَوُضُوحِ المَخْرَجِ عِنْدَ قِرَاءَةِ النُّصُوصِ الطَّوِيلَةِ.
          """
            .trimIndent(),
      ),
      ClinicalNoteEntity(
        title = "British Academic & Medical Digest (EN-GB)",
        category = "Academic",
        languageTag = "en-GB",
        content =
          """
          EDITORIAL SUMMARY:
          Research into bilingual cognition demonstrates that regular auditory exposure strengthens phonological awareness. Laboratory assessments showed normal haemoglobin levels with no signs of anaemia or peripheral oedema.
          """
            .trimIndent(),
      ),
    )

  private fun starterPronunciationRules(): List<PronunciationRuleEntity> =
    listOf(
      PronunciationRuleEntity(term = "HbA1c", spokenReplacement = "hemoglobin A 1 C", languageTag = "en"),
      PronunciationRuleEntity(term = "SpO2", spokenReplacement = "oxygen saturation", languageTag = "en"),
      PronunciationRuleEntity(term = "b.i.d.", spokenReplacement = "twice daily", languageTag = "en"),
      PronunciationRuleEntity(term = "mmHg", spokenReplacement = "millimeters of mercury", languageTag = "en"),
      PronunciationRuleEntity(term = "mg/dL", spokenReplacement = "milligrams per deciliter", languageTag = "en"),
    )

  private fun starterSavedPhrases(): List<SavedPhraseEntity> =
    listOf(
      SavedPhraseEntity(
        phrase = "Break the ice",
        translation = "كسرُ الحاجز أو بدءُ الحديث بلطف",
        languageTag = "en-US",
        pronunciationGuide = "/breɪk ði aɪs/",
        notes = "Idiom: relieve tension in an initial meeting",
        masteryLevel = 2,
      ),
      SavedPhraseEntity(
        phrase = "على قَدَمٍ وَسَاق",
        translation = "In full swing / actively underway",
        languageTag = "ar",
        pronunciationGuide = "ʿalā qadamin wa-sāq",
        notes = "Classical Arabic idiom for vigorous ongoing progress",
        masteryLevel = 1,
      ),
    )
}
