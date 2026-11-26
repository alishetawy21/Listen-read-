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
import com.example.model.PLAYBACK_SPEED_PRESETS
import com.example.ui.screens.LibraryScreen
import com.example.model.ReaderSettings
import com.example.ui.theme.ClinNoteTheme
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
  fun `app name matches ClinNote`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("ClinNote", context.getString(R.string.app_name))
    assertEquals("Read with ClinNote", context.getString(R.string.process_text_label))
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
        text = "Clinical discharge note ready for reading."
        setTextSelection(0, 8)
      }
    assertFalse(ClinNoteAccessibilityService.isSensitiveNode(normalNode))
    assertEquals("Clinical", ClinNoteAccessibilityService.extractSelectedSubstring(normalNode))
    normalNode.recycle()
  }

  @Test
  fun `ProcessTextActivity loads selected text into SpeechCoordinator`() {
    val intent =
      Intent(Intent.ACTION_PROCESS_TEXT).apply {
        putExtra(Intent.EXTRA_PROCESS_TEXT, "SpO2 is 97% on room air. Patient resting comfortably.")
      }
    val controller = Robolectric.buildActivity(ProcessTextActivity::class.java, intent).setup()
    val state = ClinNoteApplication.instance.speechCoordinator.sessionState.value
    assertTrue(state.hasContent)
    assertEquals(2, state.sentences.size)
    assertTrue(state.rawText.contains("SpO2 is 97%"))
    controller.pause().stop().destroy()
  }

  @Test
  fun `Repository seeds multilingual notes and pronunciation rules`() = runBlocking {
    val app = ClinNoteApplication.instance
    app.repository.seedInitialDataIfEmpty()
    val rules = app.repository.getEnabledPronunciationRules()
    assertTrue(rules.any { it.term == "HbA1c" })
    assertTrue(rules.any { it.term == "mmHg" })
  }

  @Test
  fun `LibraryScreen renders core ClinNote actions`() {
    composeTestRule.setContent {
      ClinNoteTheme {
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
    composeTestRule.onNodeWithText("ClinNote Workspace").assertIsDisplayed()
    composeTestRule.onNodeWithTag("new_note_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("import_pdf_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("scan_image_ocr_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("quick_paste_read_button").assertIsDisplayed()
  }
}
