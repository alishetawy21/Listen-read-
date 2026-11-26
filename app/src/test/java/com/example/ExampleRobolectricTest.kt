package com.example

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.capture.ClinNoteAccessibilityService
import com.example.capture.ProcessTextActivity
import com.example.coach.SpeechCoachManager
import com.example.model.PLAYBACK_SPEED_PRESETS
import com.example.model.ReaderSettings
import com.example.model.TranslationTone
import com.example.ui.TranslationUiState
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.TranslationScreen
import com.example.ui.theme.LinguaFloatTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `app name and process text label match LinguaFloat`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("LinguaFloat", context.getString(R.string.app_name))
    assertEquals("LinguaFloat: Read / Translate", context.getString(R.string.process_text_label))
  }

  @Test
  fun `playback speed presets match all required speeds`() {
    assertEquals(
      listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f),
      PLAYBACK_SPEED_PRESETS,
    )
  }

  @Test
  fun `accessibility service rejects password and sensitive nodes`() {
    val passwordNode =
      AccessibilityNodeInfo.obtain().apply {
        isPassword = true
        text = "super_secret_password"
      }
    assertTrue(ClinNoteAccessibilityService.isSensitiveNode(passwordNode))
    assertEquals(null, ClinNoteAccessibilityService.extractSelectedSubstring(passwordNode))
    passwordNode.recycle()

    val normalNode =
      AccessibilityNodeInfo.obtain().apply {
        isPassword = false
        text = "Bilingual Arabic and English reading passage."
        setTextSelection(0, 9)
      }
    assertFalse(ClinNoteAccessibilityService.isSensitiveNode(normalNode))
    assertEquals("Bilingual", ClinNoteAccessibilityService.extractSelectedSubstring(normalNode))
    normalNode.recycle()
  }

  @Test
  fun `ProcessTextActivity loads selected text into SpeechCoordinator`() {
    val intent =
      Intent(Intent.ACTION_PROCESS_TEXT).apply {
        putExtra(Intent.EXTRA_PROCESS_TEXT, "Please pronounce this clearly. Second sentence follows.")
      }
    val controller = Robolectric.buildActivity(ProcessTextActivity::class.java, intent).setup()
    val state = ClinNoteApplication.instance.speechCoordinator.sessionState.value
    assertTrue(state.hasContent)
    assertEquals(2, state.sentences.size)
    assertTrue(state.rawText.contains("Please pronounce this clearly"))
    controller.pause().stop().destroy()
  }

  @Test
  fun `SpeechCoachManager computes accurate verified word accuracy and WPM for English and Arabic`() {
    val enMetrics =
      SpeechCoachManager.computeVerifiedMetrics(
        targetSentence = "Please bring both heavy paper bags.",
        recognizedTranscript = "Please bring heavy paper bags",
        durationMs = 3_000L,
      )
    assertEquals(5, enMetrics.matchedWordCount)
    assertEquals(6, enMetrics.totalTargetWords)
    assertEquals(83, enMetrics.wordAccuracyPct)
    assertTrue(enMetrics.measuredWpm >= 80)

    val arMetrics =
      SpeechCoachManager.computeVerifiedMetrics(
        targetSentence = "عَلِمَ العَالِمُ حَقِيقَةَ القَرَارِ",
        recognizedTranscript = "علم العالم حقيقة القرار",
        durationMs = 2_400L,
      )
    assertEquals(100, arMetrics.wordAccuracyPct)
  }

  @Test
  fun `Repository persists documents, reading resume index, saved phrases, translations, and coach attempts`() =
    runBlocking {
      val app = ClinNoteApplication.instance
      app.repository.seedInitialDataIfEmpty()

      val docId =
        app.repository.saveNote(
          title = "Bilingual Test Book",
          content = "First sentence. Second sentence. Third sentence.",
          category = "EPUB Book",
        )
      app.repository.updateDocumentReadingProgress(docId, 2)
      val loadedDoc = app.repository.getNoteById(docId)
      assertEquals(2, loadedDoc?.lastSentenceIndex)

      app.repository.savePhrase(
        phrase = "Break the ice",
        translation = "كسر الحاجز",
        languageTag = "en-US",
      )
      val phrases = app.repository.savedPhrasesFlow.first()
      assertTrue(phrases.any { it.phrase == "Break the ice" })

      app.repository.recordTranslation(
        sourceText = "Good morning",
        translatedText = "صباح الخير",
        sourceLanguage = "en-US",
        targetLanguage = "ar",
        tone = "Natural",
        contextualNote = "Standard greeting",
      )
      val translations = app.repository.translationsFlow.first()
      assertTrue(translations.any { it.translatedText == "صباح الخير" })

      app.repository.savePronunciationAttempt(
        targetSentence = "Test sentence",
        recognizedTranscript = "Test sentence",
        targetLanguage = "en-US",
        overallScore = 92,
        verifiedAccuracyPct = 100,
        measuredWpm = 110,
        audioAnalyzed = true,
        coachingSummary = "Clear delivery",
        recordingFilePath = null,
      )
      val attempts = app.repository.pronunciationAttemptsFlow.first()
      assertTrue(attempts.any { it.overallScore == 92 })
    }

  @Test
  fun `HomeScreen renders LinguaFloat Studio and key hubs`() {
    composeTestRule.setContent {
      LinguaFloatTheme {
        HomeScreen(
          settings = ReaderSettings(),
          notes = emptyList(),
          savedPhrases = emptyList(),
          attempts = emptyList(),
          translations = emptyList(),
          onToggleFloatingShortcut = {},
          onResumeDocument = {},
          onOpenLibrary = {},
          onOpenReader = {},
          onOpenTranslation = {},
          onOpenCoach = {},
          onOpenPractice = {},
          onOpenVocabulary = {},
          onOpenProgress = {},
          onOpenFloatingSettings = {},
          onOpenStudio = {},
          onOpenSettings = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("LinguaFloat Studio").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_quick_translate").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_quick_coach").assertIsDisplayed()
  }

  @Test
  fun `LibraryScreen renders LinguaFloat PDF EPUB OCR actions`() {
    composeTestRule.setContent {
      LinguaFloatTheme {
        LibraryScreen(
          notes = emptyList(),
          settings = ReaderSettings(),
          isImporting = false,
          importBanner = null,
          onDismissImportBanner = {},
          onOpenNote = { _, _ -> },
          onSaveNote = { _, _, _, _, _ -> },
          onDeleteNote = {},
          onImportDocumentUri = {},
          onImportImageUri = {},
          onQuickReadText = { _, _ -> },
          onToggleFloatingShortcut = {},
          onOpenSettings = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Smart Book & PDF Library").assertIsDisplayed()
    composeTestRule.onNodeWithTag("new_note_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("import_pdf_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("scan_image_ocr_button").assertIsDisplayed()
  }

  @Test
  fun `TranslationScreen renders input, language swap, and translate button`() {
    composeTestRule.setContent {
      LinguaFloatTheme {
        TranslationScreen(
          inputText = "Knowledge is light",
          sourceLang = "en-US",
          targetLang = "ar",
          tone = TranslationTone.NATURAL,
          uiState = TranslationUiState.Idle,
          history = emptyList(),
          onInputChange = {},
          onSelectLanguages = { _, _ -> },
          onSwapLanguages = {},
          onSelectTone = {},
          onTranslate = {},
          onListenText = { _, _ -> },
          onPracticeInCoach = { _, _ -> },
          onSavePhrase = { _, _, _, _, _ -> },
          onDeleteHistoryItem = {},
          onClearHistory = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("translation_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("translation_swap_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("translate_submit_button").assertIsDisplayed()
  }
}
