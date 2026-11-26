package com.example.capture

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.ClinNoteApplication
import com.example.MainActivity
import com.example.model.CaptureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives shared text, PDFs, or document images via Android Share Sheet (ACTION_SEND) so users
 * can send content from any app — including apps that block direct text selection.
 */
class ShareReceiverActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val app = ClinNoteApplication.instance
    val incoming = intent

    if (incoming?.action == Intent.ACTION_SEND) {
      val mimeType = incoming.type.orEmpty()
      val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)
      val sharedSubject = incoming.getStringExtra(Intent.EXTRA_SUBJECT)
      val streamUri = getSharedStreamUri(incoming)

      when {
        mimeType.startsWith("text/") && !sharedText.isNullOrBlank() -> {
          app.speechCoordinator.loadText(
            title = sharedSubject ?: "Shared Text",
            rawText = sharedText,
            captureSource = CaptureSource.SHARE_INTENT,
            autoPlay = true,
          )
          launchMainReader()
        }
        mimeType == "application/pdf" && streamUri != null -> {
          CoroutineScope(Dispatchers.IO).launch {
            val extracted = runCatching { app.pdfProcessor.extractFromPdfUri(streamUri) }.getOrNull()
            if (extracted != null && extracted.text.isNotBlank()) {
              val source =
                if (extracted.usedOcr) CaptureSource.PDF_OCR else CaptureSource.PDF_TEXT
              val savedId =
                app.repository.saveNote(
                  title = extracted.title,
                  content = extracted.text,
                  category = if (extracted.usedOcr) "Scanned PDF (OCR)" else "PDF Document",
                  sourceType = source,
                  pageCount = extracted.pageCount,
                  isOcrExtracted = extracted.usedOcr,
                )
              app.speechCoordinator.loadText(
                title = extracted.title,
                rawText = extracted.text,
                captureSource = source,
                documentId = savedId,
                autoPlay = true,
              )
            } else {
              app.speechCoordinator.notifyCaptureFeedback(
                "Unable to extract text from shared PDF."
              )
            }
            launchMainReader()
          }
        }
        mimeType.startsWith("image/") && streamUri != null -> {
          CoroutineScope(Dispatchers.IO).launch {
            val extracted = runCatching { app.pdfProcessor.extractFromImageUri(streamUri) }.getOrNull()
            if (extracted != null && extracted.text.isNotBlank()) {
              val savedId =
                app.repository.saveNote(
                  title = extracted.title,
                  content = extracted.text,
                  category = "Scanned Image (OCR)",
                  sourceType = CaptureSource.IMAGE_OCR,
                  isOcrExtracted = true,
                )
              app.speechCoordinator.loadText(
                title = extracted.title,
                rawText = extracted.text,
                captureSource = CaptureSource.IMAGE_OCR,
                documentId = savedId,
                autoPlay = true,
              )
            } else {
              app.speechCoordinator.notifyCaptureFeedback("No readable text detected in image.")
            }
            launchMainReader()
          }
        }
        else -> launchMainReader()
      }
    } else {
      launchMainReader()
    }
  }

  private fun getSharedStreamUri(intent: Intent): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
  }

  private fun launchMainReader() {
    val mainIntent =
      Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_READER)
      }
    startActivity(mainIntent)
    finish()
  }
}
