package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ClinNoteApplication
import com.example.capture.SystemIntegrationHelper
import com.example.data.ClinicalNoteEntity
import com.example.data.PronunciationRuleEntity
import com.example.data.ReadingBookmarkEntity
import com.example.data.ReadingHistoryEntity
import com.example.model.CaptureSource
import com.example.model.ReaderSessionState
import com.example.model.ReaderSettings
import com.example.model.VoiceInfoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClinNoteViewModel(application: Application) : AndroidViewModel(application) {

  private val app: ClinNoteApplication = application as ClinNoteApplication
  private val repository = app.repository
  private val settingsStore = app.settingsStore
  private val pdfProcessor = app.pdfProcessor
  val speechCoordinator = app.speechCoordinator

  val sessionState: StateFlow<ReaderSessionState> = speechCoordinator.sessionState

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

  private val _isImporting = MutableStateFlow(false)
  val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

  private val _importBanner = MutableStateFlow<String?>(null)
  val importBanner: StateFlow<String?> = _importBanner.asStateFlow()

  fun clearImportBanner() {
    _importBanner.value = null
  }

  fun openNoteInReader(note: ClinicalNoteEntity, autoPlay: Boolean) {
    val source =
      runCatching { CaptureSource.valueOf(note.sourceType) }
        .getOrDefault(CaptureSource.INTERNAL_NOTE)
    speechCoordinator.loadText(
      title = note.title,
      rawText = note.content,
      captureSource = source,
      documentId = note.id,
      languageHint = note.languageTag,
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
        val extracted =
          if (mime.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", true)) {
            pdfProcessor.extractFromPdfUri(uri)
          } else {
            pdfProcessor.extractFromTextUri(uri)
          }

        if (extracted.text.isBlank()) {
          _importBanner.value =
            "No text could be extracted from this PDF. If it is password-protected, unlock it first."
        } else {
          val source = if (extracted.usedOcr) CaptureSource.PDF_OCR else CaptureSource.PDF_TEXT
          val id =
            repository.saveNote(
              title = extracted.title,
              content = extracted.text,
              category = if (extracted.usedOcr) "Scanned PDF (OCR)" else "PDF Document",
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
            if (extracted.usedOcr) {
              "Extracted ${extracted.ocrPagesCount} scanned page(s) via on-device OCR."
            } else {
              "Imported ${extracted.pageCount} page(s) from PDF."
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
        val extracted = pdfProcessor.extractFromImageUri(uri)
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
          _importBanner.value = "Extracted text from image using on-device OCR."
          onLoaded()
        }
      } catch (t: Throwable) {
        _importBanner.value = "OCR failed: ${t.message ?: "Unknown error"}"
      } finally {
        _isImporting.value = false
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
}
