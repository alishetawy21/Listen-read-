package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class ExtractedDocumentResult(
  val title: String,
  val text: String,
  val pageCount: Int,
  val usedOcr: Boolean,
  val ocrPagesCount: Int = 0,
)

class PdfDocumentProcessor(private val context: Context) {

  private val textRecognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  fun queryDisplayName(uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) {
        val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) {
          val name = it.getString(idx)
          if (!name.isNullOrBlank()) return name
        }
      }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Document"
  }

  suspend fun extractFromPdfUri(uri: Uri): ExtractedDocumentResult =
    withContext(Dispatchers.IO) {
      val fileName = queryDisplayName(uri).removeSuffix(".pdf").removeSuffix(".PDF")
      val rawBytes =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: throw IllegalStateException("Unable to read PDF file from URI")

      val embeddedText = extractEmbeddedPdfText(rawBytes)
      var pageCount = 1
      val ocrPages = mutableListOf<String>()
      var ocrPagesCount = 0

      // Open PdfRenderer to get accurate page count and OCR scanned pages if embedded text is sparse
      context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          pageCount = renderer.pageCount.coerceAtLeast(1)
          val needsOcr = embeddedText.isBlank() || embeddedText.length < pageCount * 40
          if (needsOcr) {
            val maxPagesToOcr = pageCount.coerceAtMost(20)
            for (pageIndex in 0 until maxPagesToOcr) {
              renderer.openPage(pageIndex).use { page ->
                val scale = 2f
                val width = (page.width * scale).toInt().coerceIn(400, 2200)
                val height = (page.height * scale).toInt().coerceIn(400, 3000)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                try {
                  val pageText = recognizeBitmapText(bitmap).trim()
                  if (pageText.isNotEmpty()) {
                    ocrPages.add(pageText)
                    ocrPagesCount++
                  }
                } finally {
                  bitmap.recycle()
                }
              }
            }
          }
        }
      }

      val ocrJoined = ocrPages.joinToString("\n\n")
      val useOcrResult = ocrJoined.length > embeddedText.length
      val finalText =
        when {
          useOcrResult && ocrJoined.isNotBlank() -> ocrJoined
          embeddedText.isNotBlank() -> embeddedText
          else -> ""
        }

      ExtractedDocumentResult(
        title = fileName,
        text = finalText,
        pageCount = pageCount,
        usedOcr = useOcrResult && ocrPagesCount > 0,
        ocrPagesCount = ocrPagesCount,
      )
    }

  suspend fun extractFromImageUri(uri: Uri): ExtractedDocumentResult =
    withContext(Dispatchers.IO) {
      val name = queryDisplayName(uri)
      val image = InputImage.fromFilePath(context, uri)
      val extracted = recognizeInputImage(image).trim()
      ExtractedDocumentResult(
        title = "Scan: $name",
        text = extracted,
        pageCount = 1,
        usedOcr = true,
        ocrPagesCount = 1,
      )
    }

  suspend fun extractFromTextUri(uri: Uri): ExtractedDocumentResult =
    withContext(Dispatchers.IO) {
      val name = queryDisplayName(uri)
      val content =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
      ExtractedDocumentResult(
        title = name.substringBeforeLast('.'),
        text = content,
        pageCount = 1,
        usedOcr = false,
      )
    }

  private suspend fun recognizeBitmapText(bitmap: Bitmap): String {
    val image = InputImage.fromBitmap(bitmap, 0)
    return recognizeInputImage(image)
  }

  private suspend fun recognizeInputImage(image: InputImage): String =
    suspendCancellableCoroutine { cont ->
      textRecognizer
        .process(image)
        .addOnSuccessListener { visionText ->
          val blocksText =
            visionText.textBlocks.joinToString("\n\n") { block ->
              block.lines.joinToString(" ") { it.text }
            }
          cont.resume(blocksText.ifBlank { visionText.text })
        }
        .addOnFailureListener { exc -> cont.resumeWithException(exc) }
    }

  /**
   * Lightweight, dependency-free PDF content stream extractor supporting uncompressed &
   * FlateDecode streams, literal strings `(...) Tj`, array strings `[...] TJ`, and UTF-16BE hex
   * strings `<FEFF...>`. Falls back to ML Kit OCR when a PDF is scanned/image-only.
   */
  internal fun extractEmbeddedPdfText(pdfBytes: ByteArray): String {
    val latin = String(pdfBytes, StandardCharsets.ISO_8859_1)
    val streamRegex = Regex("""stream\r?\n(.*?)\r?\n?endstream""", setOf(RegexOption.DOT_MATCHES_ALL))
    val extractedBlocks = mutableListOf<String>()

    // Also inspect any ToUnicode hex map entries if present
    val cmap = parseSimpleToUnicodeCMap(latin)

    for (match in streamRegex.findAll(latin)) {
      val headerStart = (match.range.first - 300).coerceAtLeast(0)
      val header = latin.substring(headerStart, match.range.first)
      val rawStreamLatin = match.groupValues[1]
      val rawStreamBytes = rawStreamLatin.toByteArray(StandardCharsets.ISO_8859_1)

      val contentString =
        if (header.contains("/FlateDecode")) {
          inflateBytes(rawStreamBytes)?.toString(StandardCharsets.ISO_8859_1) ?: continue
        } else {
          rawStreamLatin
        }

      if (contentString.contains("BT") && contentString.contains("ET")) {
        val textFromStream = extractTextOperators(contentString, cmap)
        if (textFromStream.isNotBlank()) {
          extractedBlocks.add(textFromStream)
        }
      }
    }

    return extractedBlocks
      .joinToString("\n\n")
      .replace(Regex("[ \\t]+"), " ")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
  }

  private fun inflateBytes(compressed: ByteArray): ByteArray? {
    return runCatching {
        val inflater = Inflater()
        inflater.setInput(compressed)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
          val count = inflater.inflate(buffer)
          if (count == 0) {
            if (inflater.needsInput() || inflater.needsDictionary()) break
          } else {
            out.write(buffer, 0, count)
          }
        }
        inflater.end()
        out.toByteArray()
      }
      .getOrNull()
  }

  private fun parseSimpleToUnicodeCMap(pdfText: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val bfCharRegex = Regex("""beginbfchar\s*(.*?)\s*endbfchar""", RegexOption.DOT_MATCHES_ALL)
    val pairRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
    for (block in bfCharRegex.findAll(pdfText)) {
      for (pair in pairRegex.findAll(block.groupValues[1])) {
        val code = pair.groupValues[1].uppercase()
        val uni = decodeHexUtf16(pair.groupValues[2])
        if (uni.isNotEmpty()) map[code] = uni
      }
    }
    return map
  }

  private fun extractTextOperators(stream: String, cmap: Map<String, String>): String {
    val sb = StringBuilder()
    val btEtRegex = Regex("""BT\b(.*?)\bET""", RegexOption.DOT_MATCHES_ALL)
    val opRegex = Regex("""\(((?:\\.|[^\\)])*)\)\s*Tj|\[(.*?)\]\s*TJ|<([0-9A-Fa-f]+)>\s*Tj""", RegexOption.DOT_MATCHES_ALL)
    val arrayTokenRegex = Regex("""\(((?:\\.|[^\\)])*)\)|<([0-9A-Fa-f]+)>|(-?\d+(?:\.\d+)?)""")

    for (block in btEtRegex.findAll(stream)) {
      val body = block.groupValues[1]
      val blockSb = StringBuilder()
      for (op in opRegex.findAll(body)) {
        val literal = op.groupValues[1]
        val array = op.groupValues[2]
        val hex = op.groupValues[3]
        when {
          literal.isNotEmpty() -> blockSb.append(unescapePdfString(literal))
          hex.isNotEmpty() -> blockSb.append(decodePdfHex(hex, cmap))
          array.isNotEmpty() -> {
            for (tok in arrayTokenRegex.findAll(array)) {
              val lit = tok.groupValues[1]
              val hx = tok.groupValues[2]
              val kern = tok.groupValues[3]
              when {
                lit.isNotEmpty() -> blockSb.append(unescapePdfString(lit))
                hx.isNotEmpty() -> blockSb.append(decodePdfHex(hx, cmap))
                kern.isNotEmpty() -> {
                  val num = kern.toDoubleOrNull() ?: 0.0
                  if (num < -140.0 && blockSb.isNotEmpty() && !blockSb.endsWith(" ")) {
                    blockSb.append(' ')
                  }
                }
              }
            }
          }
        }
      }
      val line = blockSb.toString().trim()
      if (line.isNotEmpty()) {
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(line)
      }
    }
    return sb.toString()
  }

  private fun unescapePdfString(raw: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < raw.length) {
      val c = raw[i]
      if (c == '\\' && i + 1 < raw.length) {
        when (val next = raw[i + 1]) {
          'n' -> sb.append('\n')
          'r' -> sb.append('\r')
          't' -> sb.append('\t')
          '(', ')', '\\' -> sb.append(next)
          else -> sb.append(next)
        }
        i += 2
      } else {
        sb.append(c)
        i++
      }
    }
    return sb.toString()
  }

  private fun decodePdfHex(hex: String, cmap: Map<String, String>): String {
    val upper = hex.uppercase()
    if (upper.startsWith("FEFF")) {
      return decodeHexUtf16(upper.removePrefix("FEFF"))
    }
    if (cmap.isNotEmpty() && upper.length % 4 == 0) {
      val mapped = StringBuilder()
      for (idx in upper.indices step 4) {
        val key = upper.substring(idx, idx + 4)
        mapped.append(cmap[key] ?: "")
      }
      if (mapped.isNotEmpty()) return mapped.toString()
    }
    return decodeHexUtf16(upper)
  }

  private fun decodeHexUtf16(hex: String): String {
    if (hex.length < 2) return ""
    return runCatching {
        val padded = if (hex.length % 2 == 0) hex else "0$hex"
        val bytes = ByteArray(padded.length / 2) { idx ->
          padded.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
        }
        if (bytes.size >= 2 && bytes.size % 2 == 0) {
          String(bytes, StandardCharsets.UTF_16BE).filter { !it.isISOControl() || it == '\n' }
        } else {
          String(bytes, StandardCharsets.ISO_8859_1)
        }
      }
      .getOrDefault("")
  }
}
