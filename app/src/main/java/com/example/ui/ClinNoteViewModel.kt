package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ClinNoteApplication
import com.example.ai.AiCallResult
import com.example.capture.SystemIntegrationHelper
import com.example.coach.CoachCaptureMode
import com.example.coach.CoachCaptureState
import com.example.coach.SpeechCoachManager
import com.example.data.ClinicalNoteEntity
import com.example.data.PronunciationAttemptEntity
import com.example.data.PronunciationRuleEntity
import com.example.data.ReadingBookmarkEntity
import com.example.data.ReadingHistoryEntity
import com.example.data.SavedPhraseEntity
import com.example.data.TranslationHistoryEntity
import com.example.model.CaptureSource
import com.example.model.FreeSpeakResult
import com.example.model.ListenAudioMode
import com.example.model.PronunciationEvaluation
import com.example.model.ReaderSessionState
import com.example.model.ReaderSettings
import com.example.model.TranslationResult
import com.example.model.TranslationTone
import com.example.model.VoiceGender
import com.example.model.VoiceInfoModel
import com.example.nlp.LanguageDetector
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TranslationUiState {
  data object Idle : TranslationUiState

  data object Loading : TranslationUiState

  data class Success(val result: TranslationResult) : TranslationUiState

  data class Error(val message: String) : TranslationUiState
}

sealed interface CoachUiState {
  data object Idle : CoachUiState

  data object Analyzing : CoachUiState

  data class Evaluated(val evaluation: PronunciationEvaluation) : CoachUiState

  data class Error(val message: String) : CoachUiState
}

sealed interface FreeSpeakUiState {
  data object Idle : FreeSpeakUiState

  data object Analyzing : FreeSpeakUiState

  data class Replied(val result: FreeSpeakResult) : FreeSpeakUiState

  data class Error(val message: String) : FreeSpeakUiState
}

data class InlineSentenceSheetState(
  val sentenceText: String = "",
  val sentenceLang: String = "en-US",
  val isLoading: Boolean = false,
  val translation: TranslationResult? = null,
  val explanation: String? = null,
  val errorMessage: String? = null,
)

class ClinNoteViewModel(application: Application) : AndroidViewModel(application) {

  private val app: ClinNoteApplication = application as ClinNoteApplication
  private val repository = app.repository
  private val settingsStore = app.settingsStore
  private val pdfProcessor = app.pdfProcessor
  private val aiService = app.linguaAiService
  val speechCoordinator = app.speechCoordinator
  val speechCoachManager = app.speechCoachManager

  val sessionState: StateFlow<ReaderSessionState> = speechCoordinator.sessionState
  val captureState: StateFlow<CoachCaptureState> = speechCoachManager.state

  val settings: StateFlow<ReaderSettings> =
    settingsStore.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = ReaderSettings(),
    )

  val notes: StateFlow<List<ClinicalNoteEntity>> =
    repository.notesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val pronunciationRules: StateFlow<List<PronunciationRuleEntity>> =
    repository.pronunciationRulesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val bookmarks: StateFlow<List<ReadingBookmarkEntity>> =
    repository.bookmarksFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val history: StateFlow<List<ReadingHistoryEntity>> =
    repository.historyFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val translationHistory: StateFlow<List<TranslationHistoryEntity>> =
    repository.translationsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val savedPhrases: StateFlow<List<SavedPhraseEntity>> =
    repository.savedPhrasesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val pronunciationAttempts: StateFlow<List<PronunciationAttemptEntity>> =
    repository.pronunciationAttemptsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  private val _isImporting = MutableStateFlow(false)
  val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

  private val _importBanner = MutableStateFlow<String?>(null)
  val importBanner: StateFlow<String?> = _importBanner.asStateFlow()

  // Translation Workspace state
  private val _translationInput = MutableStateFlow("")
  val translationInput: StateFlow<String> = _translationInput.asStateFlow()

  private val _translationSourceLang = MutableStateFlow("auto")
  val translationSourceLang: StateFlow<String> = _translationSourceLang.asStateFlow()

  private val _translationTargetLang = MutableStateFlow("ar")
  val translationTargetLang: StateFlow<String> = _translationTargetLang.asStateFlow()

  private val _translationTone = MutableStateFlow(TranslationTone.NATURAL)
  val translationTone: StateFlow<TranslationTone> = _translationTone.asStateFlow()

  private val _translationUiState = MutableStateFlow<TranslationUiState>(TranslationUiState.Idle)
  val translationUiState: StateFlow<TranslationUiState> = _translationUiState.asStateFlow()

  // Inline Reader Sentence Assist Sheet
  private val _inlineSheetState = MutableStateFlow<InlineSentenceSheetState?>(null)
  val inlineSheetState: StateFlow<InlineSentenceSheetState?> = _inlineSheetState.asStateFlow()

  // Pronunciation Coach state
  private val _coachTargetSentence =
    MutableStateFlow("Please bring both heavy paper bags before five o'clock.")
  val coachTargetSentence: StateFlow<String> = _coachTargetSentence.asStateFlow()

  private val _coachTargetLang = MutableStateFlow("en-US")
  val coachTargetLang: StateFlow<String> = _coachTargetLang.asStateFlow()

  private val _coachUiState = MutableStateFlow<CoachUiState>(CoachUiState.Idle)
  val coachUiState: StateFlow<CoachUiState> = _coachUiState.asStateFlow()

  // Free Speaking AI Partner state
  private val _freeSpeakTranscript = MutableStateFlow("")
  val freeSpeakTranscript: StateFlow<String> = _freeSpeakTranscript.asStateFlow()

  private val _freeSpeakUiState = MutableStateFlow<FreeSpeakUiState>(FreeSpeakUiState.Idle)
  val freeSpeakUiState: StateFlow<FreeSpeakUiState> = _freeSpeakUiState.asStateFlow()

  private var pendingAudioFile: File? = null
  private var pendingRecordingDurationMs: Long = 0L

  fun clearImportBanner() {
    _importBanner.value = null
  }

  // Document & Reader Actions
  fun openNoteInReader(note: ClinicalNoteEntity, autoPlay: Boolean, resumeLastPosition: Boolean = true) {
    val source =
      runCatching { CaptureSource.valueOf(note.sourceType) }
        .getOrDefault(CaptureSource.INTERNAL_NOTE)
    speechCoordinator.loadText(
      title = note.title,
      rawText = note.content,
      captureSource = source,
      documentId = note.id,
      languageHint = note.languageTag,
      startSentenceIndex = if (resumeLastPosition) note.lastSentenceIndex else 0,
      autoPlay = autoPlay,
    )
  }

  fun openBookmarkInReader(bookmark: ReadingBookmarkEntity, autoPlay: Boolean = true) {
    speechCoordinator.loadText(
      title = bookmark.documentTitle,
      rawText = bookmark.fullTextSnapshot,
      captureSource = CaptureSource.INTERNAL_NOTE,
      documentId = bookmark.documentId,
      languageHint = bookmark.languageTag,
      startSentenceIndex = bookmark.sentenceIndex,
      autoPlay = autoPlay,
    )
  }

  fun openHistoryInReader(entry: ReadingHistoryEntity, autoPlay: Boolean = true) {
    speechCoordinator.loadText(
      title = entry.title,
      rawText = entry.fullText,
      captureSource = CaptureSource.INTERNAL_NOTE,
      documentId = entry.documentId,
      languageHint = entry.languageTag,
      startSentenceIndex = entry.lastSentenceIndex,
      autoPlay = autoPlay,
    )
  }

  fun readAdHocText(title: String, rawText: String, autoPlay: Boolean = true) {
    speechCoordinator.loadText(
      title = title.ifBlank { "Quick Smart Reader" },
      rawText = rawText,
      captureSource = CaptureSource.INTERNAL_NOTE,
      autoPlay = autoPlay,
    )
  }

  fun saveNote(id: Long?, title: String, content: String, category: String, languageTag: String) {
    if (content.isBlank()) return
    viewModelScope.launch(Dispatchers.IO) {
      repository.saveNote(
        id = id,
        title = title,
        content = content,
        category = category,
        languageTag = languageTag,
      )
    }
  }

  fun deleteNote(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deleteNote(id) }
  }

  fun importDocumentUri(uri: Uri, onLoaded: () -> Unit) {
    viewModelScope.launch {
      _isImporting.value = true
      try {
        val mime = app.contentResolver.getType(uri).orEmpty()
        val uriText = uri.toString()
        val isPdf = mime.contains("pdf", ignoreCase = true) || uriText.endsWith(".pdf", true)
        val isEpub = mime.contains("epub", ignoreCase = true) || uriText.endsWith(".epub", true)
        val allowCloudOcr = settings.value.onlineAiEnabled

        val extracted =
          when {
            isEpub -> pdfProcessor.extractFromEpubUri(uri)
            isPdf -> pdfProcessor.extractFromPdfUri(uri, allowCloudArabicOcr = allowCloudOcr)
            else -> pdfProcessor.extractFromTextUri(uri)
          }

        if (extracted.text.isBlank()) {
          _importBanner.value =
            "No text could be extracted from this file. Password-protected PDFs or DRM-locked EPUBs cannot be opened."
        } else {
          val source =
            when {
              isEpub -> CaptureSource.EPUB_BOOK
              extracted.usedOcr -> CaptureSource.PDF_OCR
              isPdf -> CaptureSource.PDF_TEXT
              else -> CaptureSource.INTERNAL_NOTE
            }
          val category =
            when {
              isEpub -> "EPUB Book"
              extracted.usedOcr -> "Scanned PDF (OCR)"
              isPdf -> "PDF Document"
              else -> "Text Document"
            }
          val id =
            repository.saveNote(
              title = extracted.title,
              content = extracted.text,
              category = category,
              sourceType = source,
              pageCount = extracted.pageCount,
              isOcrExtracted = extracted.usedOcr,
            )
          speechCoordinator.loadText(
            title = extracted.title,
            rawText = extracted.text,
            captureSource = source,
            documentId = id,
            autoPlay = false,
          )
          _importBanner.value =
            when {
              isEpub -> "Imported EPUB book (${extracted.pageCount} chapter sections)."
              extracted.usedCloudVisionOcr ->
                "Extracted ${extracted.ocrPagesCount} scanned page(s) using Arabic/English Vision OCR."
              extracted.usedOcr -> "Extracted ${extracted.ocrPagesCount} scanned page(s) via on-device OCR."
              isPdf -> "Imported ${extracted.pageCount} page(s) from PDF."
              else -> "Imported text document."
            }
          onLoaded()
        }
      } catch (t: Throwable) {
        _importBanner.value = "Import failed: ${t.message ?: "Unknown error"}"
      } finally {
        _isImporting.value = false
      }
    }
  }

  fun importImageForOcr(uri: Uri, onLoaded: () -> Unit) {
    viewModelScope.launch {
      _isImporting.value = true
      try {
        val extracted =
          pdfProcessor.extractFromImageUri(uri, allowCloudArabicOcr = settings.value.onlineAiEnabled)
        if (extracted.text.isBlank()) {
          _importBanner.value = "No readable text found in selected image."
        } else {
          val id =
            repository.saveNote(
              title = extracted.title,
              content = extracted.text,
              category = "Scanned Image (OCR)",
              sourceType = CaptureSource.IMAGE_OCR,
              isOcrExtracted = true,
            )
          speechCoordinator.loadText(
            title = extracted.title,
            rawText = extracted.text,
            captureSource = CaptureSource.IMAGE_OCR,
            documentId = id,
            autoPlay = false,
          )
          _importBanner.value =
            if (extracted.usedCloudVisionOcr) "Extracted Arabic/English text via Vision OCR."
            else "Extracted text from image using on-device OCR."
          onLoaded()
        }
      } catch (t: Throwable) {
        _importBanner.value = "OCR failed: ${t.message ?: "Unknown error"}"
      } finally {
        _isImporting.value = false
      }
    }
  }

  // Inline Reader Sentence Translation & Explanation
  fun openSentenceAssistSheet(sentenceText: String, sentenceLang: String) {
    val targetLang = if (sentenceLang.startsWith("ar", true)) "en-US" else "ar"
    _inlineSheetState.value =
      InlineSentenceSheetState(
        sentenceText = sentenceText,
        sentenceLang = sentenceLang,
        isLoading = true,
      )

    if (!settings.value.onlineAiEnabled) {
      _inlineSheetState.update {
        it?.copy(
          isLoading = false,
          errorMessage = "Online AI features are turned OFF in Settings. Enable Online AI for contextual translation.",
        )
      }
      return
    }

    viewModelScope.launch {
      when (
        val res =
          aiService.translateWithContext(
            text = sentenceText,
            sourceLangHint = sentenceLang,
            targetLang = targetLang,
            tone = TranslationTone.NATURAL,
          )
      ) {
        is AiCallResult.Success -> {
          _inlineSheetState.update {
            it?.copy(isLoading = false, translation = res.data, errorMessage = null)
          }
          if (speechCoordinator.sessionState.value.listenAudioMode == ListenAudioMode.TRANSLATED) {
            speechCoordinator.speakOneShot(res.data.translatedText, targetLang)
          }
        }
        is AiCallResult.Error -> {
          _inlineSheetState.update { it?.copy(isLoading = false, errorMessage = res.message) }
        }
      }
    }
  }

  fun explainCurrentSheetPassage() {
    val sheet = _inlineSheetState.value ?: return
    if (!settings.value.onlineAiEnabled) {
      _inlineSheetState.update {
        it?.copy(errorMessage = "Enable Online AI in Settings to request passage explanations.")
      }
      return
    }
    _inlineSheetState.update { it?.copy(isLoading = true, errorMessage = null) }
    val learnerLang = if (sheet.sentenceLang.startsWith("ar", true)) "English and Arabic" else "Arabic and English"
    viewModelScope.launch {
      when (val res = aiService.explainPassage(sheet.sentenceText, learnerLang)) {
        is AiCallResult.Success -> {
          _inlineSheetState.update { it?.copy(isLoading = false, explanation = res.data) }
        }
        is AiCallResult.Error -> {
          _inlineSheetState.update { it?.copy(isLoading = false, errorMessage = res.message) }
        }
      }
    }
  }

  fun dismissSentenceAssistSheet() {
    _inlineSheetState.value = null
  }

  // Translation Workspace Actions
  fun updateTranslationInput(text: String) {
    _translationInput.value = text
    if (_translationSourceLang.value == "auto" && text.length >= 3) {
      val detected = LanguageDetector.detectLanguage(text)
      if (detected.startsWith("ar", true) && _translationTargetLang.value.startsWith("ar", true)) {
        _translationTargetLang.value = settings.value.englishAccentTag
      } else if (!detected.startsWith("ar", true) && !_translationTargetLang.value.startsWith("ar", true)) {
        _translationTargetLang.value = "ar"
      }
    }
  }

  fun setTranslationLanguages(source: String, target: String) {
    _translationSourceLang.value = source
    _translationTargetLang.value = target
  }

  fun swapTranslationLanguages() {
    val currentSrc = _translationSourceLang.value
    val currentTgt = _translationTargetLang.value
    val resolvedSrc =
      if (currentSrc == "auto") {
        val detected = LanguageDetector.detectLanguage(_translationInput.value)
        if (detected.startsWith("ar", true)) "ar" else settings.value.englishAccentTag
      } else currentSrc

    _translationSourceLang.value = currentTgt
    _translationTargetLang.value = resolvedSrc

    val currentResult = (_translationUiState.value as? TranslationUiState.Success)?.result
    if (currentResult != null) {
      _translationInput.value = currentResult.translatedText
    }
  }

  fun setTranslationTone(tone: TranslationTone) {
    _translationTone.value = tone
  }

  fun runTranslation(overrideText: String? = null) {
    val textToTranslate = (overrideText ?: _translationInput.value).trim()
    if (overrideText != null) _translationInput.value = textToTranslate
    if (textToTranslate.isBlank()) {
      _translationUiState.value = TranslationUiState.Error("Enter or paste text to translate.")
      return
    }
    if (!settings.value.onlineAiEnabled) {
      _translationUiState.value =
        TranslationUiState.Error("Online AI translation is disabled in Settings.")
      return
    }

    val srcHint =
      if (_translationSourceLang.value == "auto") LanguageDetector.detectLanguage(textToTranslate)
      else _translationSourceLang.value
    val target = _translationTargetLang.value

    _translationUiState.value = TranslationUiState.Loading
    viewModelScope.launch {
      when (
        val res =
          aiService.translateWithContext(
            text = textToTranslate,
            sourceLangHint = srcHint,
            targetLang = target,
            tone = _translationTone.value,
          )
      ) {
        is AiCallResult.Success -> {
          _translationUiState.value = TranslationUiState.Success(res.data)
          withContext(Dispatchers.IO) {
            repository.recordTranslation(
              sourceText = res.data.sourceText,
              translatedText = res.data.translatedText,
              sourceLanguage = res.data.detectedSourceLanguage,
              targetLanguage = res.data.targetLanguage,
              tone = _translationTone.value.label,
              contextualNote = res.data.literalVsContextualNote,
            )
          }
        }
        is AiCallResult.Error -> {
          _translationUiState.value = TranslationUiState.Error(res.message)
        }
      }
    }
  }

  fun deleteTranslationHistoryItem(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deleteTranslation(id) }
  }

  fun clearAllTranslations() {
    viewModelScope.launch(Dispatchers.IO) { repository.clearTranslationHistory() }
  }

  // Vocabulary / Saved Phrases
  fun savePhraseToVocabulary(
    phrase: String,
    translation: String,
    languageTag: String,
    pronunciationGuide: String = "",
    notes: String = "",
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.savePhrase(
        phrase = phrase,
        translation = translation,
        languageTag = languageTag,
        pronunciationGuide = pronunciationGuide,
        notes = notes,
      )
    }
  }

  fun updatePhraseMastery(id: Long, level: Int) {
    viewModelScope.launch(Dispatchers.IO) { repository.updatePhraseMastery(id, level) }
  }

  fun deletePhrase(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deletePhrase(id) }
  }

  // Pronunciation & Accent Coach Actions
  fun setCoachTarget(sentence: String, languageTag: String? = null) {
    _coachTargetSentence.value = sentence
    _coachTargetLang.value = languageTag ?: LanguageDetector.detectLanguage(sentence)
    _coachUiState.value = CoachUiState.Idle
  }

  fun setCoachTargetLanguage(languageTag: String) {
    _coachTargetLang.value = languageTag
  }

  fun startCoachAudioRecording() {
    pendingAudioFile = null
    _coachUiState.value = CoachUiState.Idle
    speechCoachManager.startAudioRecording()
  }

  fun stopCoachAudioRecordingAndAnalyze() {
    val (file, durationMs) = speechCoachManager.stopAudioRecording()
    pendingAudioFile = file
    pendingRecordingDurationMs = durationMs
    if (file == null) {
      _coachUiState.value =
        CoachUiState.Error("No audio captured. Hold/speak clearly into the microphone and try again.")
      return
    }
    analyzeCoachAttempt(audioFile = file, recognizedTranscript = "", durationMs = durationMs)
  }

  fun startCoachLiveAsr() {
    pendingAudioFile = null
    _coachUiState.value = CoachUiState.Idle
    speechCoachManager.startLiveSpeechRecognition(_coachTargetLang.value) { transcript, durationMs ->
      pendingRecordingDurationMs = durationMs
      analyzeCoachAttempt(audioFile = null, recognizedTranscript = transcript, durationMs = durationMs)
    }
  }

  fun stopCoachLiveAsr() {
    speechCoachManager.stopLiveSpeechRecognition()
  }

  fun analyzeCoachAttempt(
    audioFile: File?,
    recognizedTranscript: String,
    durationMs: Long,
  ) {
    val target = _coachTargetSentence.value.trim()
    val targetLang = _coachTargetLang.value
    if (target.isBlank()) {
      _coachUiState.value = CoachUiState.Error("Enter or select a practice sentence first.")
      return
    }
    if (!settings.value.onlineAiEnabled) {
      if (recognizedTranscript.isNotBlank()) {
        val verified = SpeechCoachManager.computeVerifiedMetrics(target, recognizedTranscript, durationMs)
        _coachUiState.value =
          CoachUiState.Evaluated(
            com.example.model.PronunciationEvaluation(
              targetSentence = target,
              recognizedTranscript = recognizedTranscript,
              estimatedOverallScore = verified.wordAccuracyPct,
              verifiedWordAccuracyPct = verified.wordAccuracyPct,
              measuredWpm = verified.measuredWpm,
              audioAnalyzed = false,
              verifiedMetricsSummary =
                "Offline ASR match: ${verified.wordAccuracyPct}% (${verified.matchedWordCount}/${verified.totalTargetWords} words) · ${verified.measuredWpm} WPM",
              clarityAndArticulationNote = "Online AI coaching is turned off; showing verified speech recognition metrics only.",
              rhythmAndStressNote = "Measured pace: ${verified.measuredWpm} words per minute.",
              l1InterferenceTips = "Enable Online AI in Settings for detailed Arabic/English phoneme feedback.",
              practiceDrillTip = "Listen to the 0.75x reference audio and repeat any mismatched words.",
              wordAssessments = emptyList(),
            )
          )
      } else {
        _coachUiState.value =
          CoachUiState.Error("Online AI is disabled in Settings. Enable Online AI or use Live Speech Check.")
      }
      return
    }

    _coachUiState.value = CoachUiState.Analyzing
    viewModelScope.launch {
      val audioBytes =
        withContext(Dispatchers.IO) {
          audioFile?.takeIf { it.exists() }?.readBytes()
        }
      val metrics =
        if (recognizedTranscript.isNotBlank()) {
          SpeechCoachManager.computeVerifiedMetrics(target, recognizedTranscript, durationMs)
        } else {
          // Estimate duration-based target pace when using raw multimodal audio mode
          val targetWordCount = target.split(Regex("\\s+")).filter { it.isNotBlank() }.size
          val wpm = ((targetWordCount / (durationMs / 60_000.0).coerceAtLeast(0.02)).toInt()).coerceIn(20, 240)
          com.example.coach.VerifiedSpeechMetrics(
            transcript = "(Audio clip submitted to Gemini)",
            wordAccuracyPct = 0,
            measuredWpm = wpm,
            matchedWordCount = 0,
            totalTargetWords = targetWordCount,
          )
        }

      val res =
        aiService.evaluatePronunciation(
          targetSentence = target,
          targetLanguage = targetLang,
          audioBytes = audioBytes,
          audioMimeType = "audio/mp4",
          recognizedTranscript = recognizedTranscript,
          measuredWordAccuracyPct = metrics.wordAccuracyPct,
          measuredWpm = metrics.measuredWpm,
        )

      val keepRecording = settings.value.saveVoiceRecordingsConsent
      when (res) {
        is AiCallResult.Success -> {
          _coachUiState.value = CoachUiState.Evaluated(res.data)
          val storedPath =
            if (keepRecording && audioFile?.exists() == true) audioFile.absolutePath else null
          withContext(Dispatchers.IO) {
            repository.savePronunciationAttempt(
              targetSentence = target,
              recognizedTranscript = res.data.recognizedTranscript,
              targetLanguage = targetLang,
              overallScore = res.data.estimatedOverallScore,
              verifiedAccuracyPct = res.data.verifiedWordAccuracyPct,
              measuredWpm = res.data.measuredWpm,
              audioAnalyzed = res.data.audioAnalyzed,
              coachingSummary = res.data.clarityAndArticulationNote,
              recordingFilePath = storedPath,
            )
          }
          speechCoachManager.deleteCachedAudioIfUnconsented(audioFile, keepRecording)
        }
        is AiCallResult.Error -> {
          speechCoachManager.deleteCachedAudioIfUnconsented(audioFile, keepRecording)
          _coachUiState.value = CoachUiState.Error(res.message)
        }
      }
    }
  }

  // Free Speaking AI Partner Actions
  fun updateFreeSpeakTranscript(text: String) {
    _freeSpeakTranscript.value = text
  }

  fun startFreeSpeakAsr(targetLanguage: String) {
    _freeSpeakUiState.value = FreeSpeakUiState.Idle
    speechCoachManager.startLiveSpeechRecognition(targetLanguage) { transcript, _ ->
      _freeSpeakTranscript.value = transcript
      submitFreeSpeakForFeedback(transcript = transcript, audioFile = null, targetLanguage = targetLanguage)
    }
  }

  fun startFreeSpeakAudioRecording() {
    _freeSpeakUiState.value = FreeSpeakUiState.Idle
    speechCoachManager.startAudioRecording()
  }

  fun stopFreeSpeakAudioAndAnalyze(targetLanguage: String) {
    val (file, _) = speechCoachManager.stopAudioRecording()
    if (file == null) {
      _freeSpeakUiState.value = FreeSpeakUiState.Error("No audio captured. Try speaking again.")
      return
    }
    submitFreeSpeakForFeedback(
      transcript = _freeSpeakTranscript.value.ifBlank { "(Spoken audio message)" },
      audioFile = file,
      targetLanguage = targetLanguage,
    )
  }

  fun submitFreeSpeakForFeedback(
    transcript: String = _freeSpeakTranscript.value,
    audioFile: File? = null,
    targetLanguage: String,
  ) {
    if (transcript.isBlank() && audioFile == null) {
      _freeSpeakUiState.value = FreeSpeakUiState.Error("Speak or type a message first.")
      return
    }
    if (!settings.value.onlineAiEnabled) {
      _freeSpeakUiState.value = FreeSpeakUiState.Error("Online AI is turned OFF in Settings.")
      return
    }
    _freeSpeakUiState.value = FreeSpeakUiState.Analyzing
    viewModelScope.launch {
      val bytes = withContext(Dispatchers.IO) { audioFile?.takeIf { it.exists() }?.readBytes() }
      val res =
        aiService.evaluateFreeSpeaking(
          userTranscript = transcript,
          audioBytes = bytes,
          audioMimeType = "audio/mp4",
          targetLanguage = targetLanguage,
        )
      speechCoachManager.deleteCachedAudioIfUnconsented(audioFile, settings.value.saveVoiceRecordingsConsent)
      _freeSpeakUiState.value =
        when (res) {
          is AiCallResult.Success -> FreeSpeakUiState.Replied(res.data)
          is AiCallResult.Error -> FreeSpeakUiState.Error(res.message)
        }
    }
  }

  // Dictionary, Bookmarks & History
  fun savePronunciationRule(term: String, replacement: String, languageTag: String) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.savePronunciationRule(
        term = term,
        spokenReplacement = replacement,
        languageTag = languageTag,
      )
    }
  }

  fun togglePronunciationRule(rule: PronunciationRuleEntity) {
    viewModelScope.launch(Dispatchers.IO) { repository.togglePronunciationRule(rule) }
  }

  fun deletePronunciationRule(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deletePronunciationRule(id) }
  }

  fun deleteBookmark(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deleteBookmark(id) }
  }

  fun clearAllHistory() {
    viewModelScope.launch(Dispatchers.IO) { repository.clearAllHistory() }
  }

  fun deleteHistoryEntry(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deleteHistoryEntry(id) }
  }

  fun clearAllVoiceData() {
    viewModelScope.launch(Dispatchers.IO) { repository.clearAllPronunciationAttemptsAndRecordings() }
  }

  // Settings updates
  fun setFloatingShortcutEnabled(enabled: Boolean) {
    if (enabled && !SystemIntegrationHelper.canDrawOverlays(app)) {
      SystemIntegrationHelper.openOverlayPermissionSettings(app)
      return
    }
    viewModelScope.launch {
      settingsStore.updateSettings { it.copy(floatingShortcutEnabled = enabled) }
      if (enabled) {
        SystemIntegrationHelper.startFloatingShortcut(app)
      } else {
        SystemIntegrationHelper.stopFloatingShortcut(app)
      }
    }
  }

  fun updateFloatingButtonSize(dp: Float) {
    viewModelScope.launch { settingsStore.updateSettings { it.copy(floatingButtonSizeDp = dp) } }
  }

  fun updateFloatingButtonAlpha(alpha: Float) {
    viewModelScope.launch { settingsStore.updateSettings { it.copy(floatingButtonAlpha = alpha) } }
  }

  fun resetFloatingButtonPosition() {
    viewModelScope.launch {
      settingsStore.updateSettings { it.copy(floatingButtonX = 24, floatingButtonY = 360) }
    }
  }

  fun setDoubleTapToTogglePlay(enabled: Boolean) {
    viewModelScope.launch { settingsStore.updateSettings { it.copy(doubleTapToTogglePlay = enabled) } }
  }

  fun setDefaultLanguage(tag: String) {
    viewModelScope.launch { settingsStore.updateSettings { it.copy(defaultLanguageTag = tag) } }
  }

  fun setEnglishAccent(tag: String) {
    viewModelScope.launch { settingsStore.updateSettings { it.copy(englishAccentTag = tag) } }
  }

  fun setAutoDetectLanguage(enabled: Boolean) {
    viewModelScope.launch { settingsStore.updateSettings { it.copy(autoDetectLanguage = enabled) } }
  }

  fun setMixArabicEnglishSwitching(enabled: Boolean) {
    viewModelScope.launch {
      settingsStore.updateSettings { it.copy(mixArabicEnglishSwitching = enabled) }
    }
  }

  fun setReadingHistoryEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsStore.updateSettings { it.copy(readingHistoryEnabled = enabled) } }
  }

  fun setSaveVoiceRecordingsConsent(enabled: Boolean) {
    viewModelScope.launch { settingsStore.updateSettings { it.copy(saveVoiceRecordingsConsent = enabled) } }
  }

  fun setOnlineAiEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsStore.updateSettings { it.copy(onlineAiEnabled = enabled) } }
  }

  fun setCloudTtsConfig(enabled: Boolean, endpointUrl: String) {
    viewModelScope.launch {
      settingsStore.updateSettings {
        it.copy(cloudTtsEnabled = enabled, cloudTtsEndpointUrl = endpointUrl.trim())
      }
    }
  }

  fun selectVoice(voice: VoiceInfoModel) = speechCoordinator.selectVoiceForLanguage(voice)

  fun toggleFavoriteVoice(voiceId: String) = speechCoordinator.toggleFavoriteVoice(voiceId)

  fun testVoice(voice: VoiceInfoModel) = speechCoordinator.testVoiceSample(voice)

  override fun onCleared() {
    speechCoachManager.release()
    super.onCleared()
  }
}
