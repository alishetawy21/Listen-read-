package com.example.data

import com.example.model.CaptureSource
import kotlinx.coroutines.flow.Flow

class ClinNoteRepository(private val database: ClinNoteDatabase) {

  private val noteDao = database.clinicalNoteDao()
  private val ruleDao = database.pronunciationRuleDao()
  private val bookmarkDao = database.readingBookmarkDao()
  private val historyDao = database.readingHistoryDao()

  val notesFlow: Flow<List<ClinicalNoteEntity>> = noteDao.observeAllNotes()
  val pronunciationRulesFlow: Flow<List<PronunciationRuleEntity>> = ruleDao.observeAllRules()
  val bookmarksFlow: Flow<List<ReadingBookmarkEntity>> = bookmarkDao.observeAllBookmarks()
  val historyFlow: Flow<List<ReadingHistoryEntity>> = historyDao.observeHistory()

  suspend fun seedInitialDataIfEmpty() {
    if (noteDao.countNotes() == 0) {
      starterNotes().forEach { noteDao.insertNote(it) }
    }
    if (ruleDao.countRules() == 0) {
      starterPronunciationRules().forEach { ruleDao.insertRule(it) }
    }
  }

  suspend fun getNoteById(id: Long): ClinicalNoteEntity? = noteDao.getNoteById(id)

  suspend fun saveNote(
    id: Long? = null,
    title: String,
    content: String,
    category: String = "Clinical Note",
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
          title = title.trim().ifEmpty { "Untitled Note" },
          content = content,
          category = category,
          sourceType = sourceType.name,
          languageTag = languageTag,
          pageCount = pageCount,
          isOcrExtracted = isOcrExtracted,
          createdAt = existing?.createdAt ?: now,
          updatedAt = now,
        )
      } else {
        ClinicalNoteEntity(
          title = title.trim().ifEmpty { "Untitled Note" },
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

  private fun starterNotes(): List<ClinicalNoteEntity> =
    listOf(
      ClinicalNoteEntity(
        title = "Cardiology Consult — Acute Dyspnea",
        category = "Cardiology",
        languageTag = "en-US",
        content =
          """
          HISTORY OF PRESENT ILLNESS:
          Dr. Alvarez evaluated a 64-year-old patient presenting with progressive exertional dyspnea over 4 days. Vitals on admission showed BP 148/92 mmHg, HR 96 BPM, and SpO2 93% on room air.

          LABORATORY & DIAGNOSTICS:
          Serum creatinine was 1.3 mg/dL, BNP measured 640 pg/mL, and HbA1c was 7.2%. Twelve-lead ECG demonstrated sinus tachycardia without acute ST elevation.

          ASSESSMENT & PLAN:
          Initiate Furosemide 40 mg IV once daily and continue Metformin 500 mg b.i.d. Monitor electrolytes q.d. and recheck renal panel in 24 hours.

          References:
          [1] AHA/ACC Heart Failure Guidelines, 2024 Update.
          """
            .trimIndent(),
      ),
      ClinicalNoteEntity(
        title = "تقرير سريري — متابعة السكري وضغط الدم",
        category = "Endocrinology",
        languageTag = "ar",
        content =
          """
          مُلَخَّصُ الحَالَةِ السَّرِيرِيَّةِ:
          حضر المريضُ البالغ من العمر 58 عاماً للمتابعة الدورية لمرض السكري من النوع الثاني وارتفاع ضغط الدم الشرياني. أظهر الفحص السريري ضغط دم 138/86 mmHg ومعدل نبض 78 نبضة في الدقيقة.

          التَّحَالِيلُ المَخْبَرِيَّةُ:
          بلغت نسبة السكر التراكمي HbA1c 7.4%، ومستوى الكرياتينين 0.95 mg/dL، ومعدل الترشيح الكبيبي eGFR 84 mL/min.

          الخُطَّةُ العِلَاجِيَّةُ:
          الاستمرار على دواء Metformin 500 mg مرتين يومياً بعد الطعام، وإضافة Lisinopril 10 mg صباحاً مع مراقبة ضغط الدم في المنزل أسبوعياً.

          المراجع:
          [1] الدليل الإرشادي للجمعية الأمريكية للسكري 2025.
          """
            .trimIndent(),
      ),
      ClinicalNoteEntity(
        title = "Neurology Ward Round (UK)",
        category = "Neurology",
        languageTag = "en-GB",
        content =
          """
          WARD SUMMARY:
          The patient reports significant improvement in migraine frequency following prophylactic therapy. Full blood count confirmed normal haemoglobin with no evidence of anaemia or peripheral oedema.

          MEDICATION REVIEW:
          Continue Sumatriptan 50 mg p.r.n. at headache onset and Paracetamol 1 g every 6 hours if required. Follow up in neurology outpatients in 6 weeks.
          """
            .trimIndent(),
      ),
      ClinicalNoteEntity(
        title = "Multilingual Clinical Rounds (FR / DE / ES)",
        category = "Multilingual",
        languageTag = "auto",
        content =
          """
          Le patient présente une tension artérielle stable à 122/78 mmHg sans signe de détresse respiratoire. Le traitement par voie orale est bien toléré.

          Der Patient zeigt eine deutliche Besserung der Symptomatik nach der Physiotherapie und hat keine Nebenwirkungen unter der aktuellen Medikation.

          El paciente refiere mejoría clínica progresiva y mantiene una saturación de oxígeno del 98% con respiración espontánea.
          """
            .trimIndent(),
      ),
    )

  private fun starterPronunciationRules(): List<PronunciationRuleEntity> =
    listOf(
      PronunciationRuleEntity(
        term = "HbA1c",
        spokenReplacement = "hemoglobin A 1 C",
        languageTag = "en",
      ),
      PronunciationRuleEntity(
        term = "SpO2",
        spokenReplacement = "oxygen saturation",
        languageTag = "en",
      ),
      PronunciationRuleEntity(
        term = "eGFR",
        spokenReplacement = "estimated G F R",
        languageTag = "en",
      ),
      PronunciationRuleEntity(
        term = "b.i.d.",
        spokenReplacement = "twice daily",
        languageTag = "en",
      ),
      PronunciationRuleEntity(
        term = "t.i.d.",
        spokenReplacement = "three times daily",
        languageTag = "en",
      ),
      PronunciationRuleEntity(
        term = "q.d.",
        spokenReplacement = "once daily",
        languageTag = "en",
      ),
      PronunciationRuleEntity(
        term = "p.r.n.",
        spokenReplacement = "as needed",
        languageTag = "en",
      ),
      PronunciationRuleEntity(
        term = "mmHg",
        spokenReplacement = "millimeters of mercury",
        languageTag = "en",
      ),
      PronunciationRuleEntity(
        term = "mg/dL",
        spokenReplacement = "milligrams per deciliter",
        languageTag = "en",
      ),
    )
}
