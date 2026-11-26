package com.example.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.example.ClinNoteApplication
import com.example.MainActivity
import com.example.model.CaptureSource

/**
 * Receives selected text from Android's system text-selection toolbar ("Read with ClinNote") via
 * Intent.ACTION_PROCESS_TEXT and begins reading immediately.
 */
class ProcessTextActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val selectedText =
      intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()

    if (selectedText.isNotEmpty()) {
      val app = ClinNoteApplication.instance
      val previewTitle =
        selectedText.lineSequence().firstOrNull { it.isNotBlank() }?.take(42) ?: "Selected Text"

      app.speechCoordinator.loadText(
        title = previewTitle,
        rawText = selectedText,
        captureSource = CaptureSource.PROCESS_TEXT,
        autoPlay = true,
      )

      if (SystemIntegrationHelper.canDrawOverlays(this)) {
        SystemIntegrationHelper.startFloatingShortcut(this)
        Toast.makeText(this, "Reading selection with ClinNote", Toast.LENGTH_SHORT).show()
      } else {
        val openReader =
          Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_READER)
          }
        startActivity(openReader)
      }
    } else {
      Toast.makeText(this, "No text selected to read", Toast.LENGTH_SHORT).show()
    }

    finish()
  }
}
